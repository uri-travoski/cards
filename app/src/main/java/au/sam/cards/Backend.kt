package au.sam.cards

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Xml
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RemoteFile(val id: String, val name: String)

/** Where the cloud copy lives. Sync only needs these four operations. */
interface Backend {
    fun list(): List<RemoteFile>
    fun download(id: String): ByteArray
    fun upload(name: String, mime: String, data: ByteArray): String
    fun delete(id: String)

    /** True when [list] just created the (empty) place the cards live in, so "missing there" does not mean "deleted". */
    fun createdNewStorage(): Boolean = false
}

/** A folder picked with Android's file picker, on a cloud app's provider (Dropbox, OneDrive, Nextcloud, ...). */
class SafBackend(ctx: Context, private val tree: Uri) : Backend {
    private val cr = ctx.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private fun doc(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    override fun list(): List<RemoteFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val out = mutableListOf<RemoteFile>()
        cr.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += RemoteFile(c.getString(0), c.getString(1))
        } ?: throw IOException("Can't read that folder")
        return out
    }

    override fun download(id: String): ByteArray =
        (cr.openInputStream(doc(id)) ?: throw IOException("Can't read $id")).use { it.readBytes() }

    override fun upload(name: String, mime: String, data: ByteArray): String {
        val u = DocumentsContract.createDocument(cr, doc(rootId), mime, name) ?: throw IOException("Can't create $name")
        (cr.openOutputStream(u, "wt") ?: throw IOException("Can't write $name")).use { it.write(data) }
        return DocumentsContract.getDocumentId(u)
    }

    override fun delete(id: String) {
        try {
            DocumentsContract.deleteDocument(cr, doc(id))
        } catch (_: FileNotFoundException) {
        }
    }
}

data class S3Config(
    val endpoint: String = "",
    val region: String = "auto",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
) {
    val complete get() = endpoint.isNotBlank() && region.isNotBlank() && bucket.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()
}

/** Any S3-compatible bucket (AWS, Backblaze B2, Cloudflare R2, MinIO), path-style addressing, AWS Signature V4. */
class S3Backend(private val c: S3Config) : Backend {
    private val base = c.endpoint.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
    private val hostHeader = URL(base).let { if (it.port == -1) it.host else "${it.host}:${it.port}" }
    private val prefix = "cards/"

    private fun enc(s: String) = buildString {
        for (b in s.toByteArray()) {
            val ch = b.toInt().toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.~") append(ch) else append("%%%02X".format(b.toInt() and 0xff))
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sha(b: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun hmac(key: ByteArray, msg: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg.toByteArray())

    private fun call(method: String, key: String?, query: Map<String, String> = emptyMap(), body: ByteArray? = null, mime: String? = null): HttpURLConnection {
        val path = "/" + enc(c.bucket) + if (key != null) "/" + key.split("/").joinToString("/") { enc(it) } else ""
        val q = query.toSortedMap().entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val now = Date()
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        val day = amzDate.take(8)
        val payload = sha(body ?: ByteArray(0))
        val signed = "host;x-amz-content-sha256;x-amz-date"
        val canonical = "$method\n$path\n$q\nhost:$hostHeader\nx-amz-content-sha256:$payload\nx-amz-date:$amzDate\n\n$signed\n$payload"
        val scope = "$day/${c.region}/s3/aws4_request"
        val toSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha(canonical.toByteArray())}"
        var k = hmac(("AWS4" + c.secretKey).toByteArray(), day)
        for (part in listOf(c.region, "s3", "aws4_request")) k = hmac(k, part)
        val sig = hex(hmac(k, toSign))

        val conn = URL(base + path + if (q.isNotEmpty()) "?$q" else "").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty("x-amz-date", amzDate)
        conn.setRequestProperty("x-amz-content-sha256", payload)
        conn.setRequestProperty("Authorization", "AWS4-HMAC-SHA256 Credential=${c.accessKey}/$scope, SignedHeaders=$signed, Signature=$sig")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", mime ?: "application/octet-stream")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun ok(conn: HttpURLConnection, allow404: Boolean = false): ByteArray {
        val code = conn.responseCode
        if (code in 200..299) return conn.inputStream.use { it.readBytes() }
        val err = conn.errorStream?.readBytes()?.decodeToString().orEmpty()
        if (allow404 && code == 404) return ByteArray(0)
        val why = Regex("<Message>(.*?)</Message>").find(err)?.groupValues?.get(1) ?: err.take(200)
        throw IOException("Storage said $code: $why")
    }

    override fun list(): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var token = ""
        do {
            val q = mutableMapOf("list-type" to "2", "prefix" to prefix)
            if (token.isNotEmpty()) q["continuation-token"] = token
            val xml = ok(call("GET", null, q))
            val p = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
            var tag = ""
            token = ""
            while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> tag = p.name
                    org.xmlpull.v1.XmlPullParser.TEXT -> when (tag) {
                        "Key" -> p.text.removePrefix(prefix).takeIf { it.isNotEmpty() }?.let { out += RemoteFile(it, it) }
                        "NextContinuationToken" -> token = p.text
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> tag = ""
                }
            }
        } while (token.isNotEmpty())
        return out
    }

    override fun download(id: String): ByteArray = ok(call("GET", prefix + id))

    override fun upload(name: String, mime: String, data: ByteArray): String {
        ok(call("PUT", prefix + name, body = data, mime = mime))
        return name
    }

    override fun delete(id: String) {
        ok(call("DELETE", prefix + id), allow404 = true)
    }
}
