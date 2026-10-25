package au.sam.cards

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Google Drive v3 client. Cards live in a normal, visible folder called "My Cards App" in My Drive (scope
 * drive.file: the app only sees files it created). Cards that older versions kept in the hidden appDataFolder are
 * moved into the visible folder the first time it is created (scope drive.appdata, used for that move only).
 */
class DriveBackend(
    private val token: String,
    private val savedFolderId: String = "",
    private val onFolder: (String) -> Unit = {},
    private val base: String = BuildConfig.MOCK_DRIVE.ifEmpty { "https://www.googleapis.com" },
) : Backend {

    private fun open(method: String, path: String, contentType: String? = null, body: ByteArray? = null): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15000
        c.readTimeout = 60000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.outputStream.use { it.write(body) }
        }
        return c
    }

    private fun bytes(c: HttpURLConnection): ByteArray {
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.readBytes()?.decodeToString().orEmpty().take(300)
            throw DriveError(code, err)
        }
        return c.inputStream.use { it.readBytes() }
    }

    private var folderId: String? = null
    private var created = false

    override fun createdNewStorage() = created

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun folder(): String = folderId ?: findOrCreateFolder().also { folderId = it; onFolder(it) }

    private fun firstFolder(q: String): String? {
        val a = JSONObject(bytes(open("GET", "/drive/v3/files?q=${enc(q)}&fields=${enc("files(id)")}")).decodeToString()).getJSONArray("files")
        return if (a.length() > 0) a.getJSONObject(0).getString("id") else null
    }

    /**
     * The cards folder wherever it is in Drive, so it can be moved or renamed. In order: the id this phone remembers,
     * a folder carrying this app's marker, a folder named "My Cards App" (the app only sees folders it created), and
     * only then a new one.
     */
    private fun findOrCreateFolder(): String {
        if (savedFolderId.isNotEmpty()) {
            try {
                val j = JSONObject(bytes(open("GET", "/drive/v3/files/$savedFolderId?fields=${enc("id,trashed")}")).decodeToString())
                if (!j.optBoolean("trashed")) return savedFolderId
            } catch (e: DriveError) {
                if (e.code != 404 && e.code != 403) throw e
            }
        }
        firstFolder("mimeType='$FOLDER_MIME' and appProperties has { key='$MARKER' and value='1' } and trashed=false")?.let { return it }
        firstFolder("mimeType='$FOLDER_MIME' and name='$FOLDER_NAME' and trashed=false")?.let { id ->
            val c = open("PATCH", "/drive/v3/files/$id?fields=id", "application/json; charset=UTF-8", marker().toString().toByteArray())
            bytes(c) // mark it so a later rename does not hide it
            return id
        }
        val meta = marker().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME).put("parents", org.json.JSONArray().put("root"))
        val c = open("POST", "/drive/v3/files?fields=id", "application/json; charset=UTF-8", meta.toString().toByteArray())
        val id = JSONObject(bytes(c).decodeToString()).getString("id")
        created = true
        moveHiddenCards(id)
        return id
    }

    private fun marker() = JSONObject().put("appProperties", JSONObject().put(MARKER, "1"))

    /** One-time move of cards from the old hidden app folder into [to]. Failing here never blocks the new folder. */
    private fun moveHiddenCards(to: String) {
        try {
            val old = JSONObject(bytes(open("GET", "/drive/v3/files?spaces=appDataFolder&pageSize=1000&fields=${enc("files(id,name)")}")).decodeToString()).getJSONArray("files")
            for (i in 0 until old.length()) {
                val f = old.getJSONObject(i)
                val name = f.getString("name")
                upload(name, if (name.endsWith(".json")) "application/json" else "image/jpeg", download(f.getString("id")), to)
                delete(f.getString("id"))
            }
        } catch (e: Exception) {
            // No access to the hidden folder (scope not granted) or nothing there: nothing to move.
        }
    }

    override fun list(): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var page = ""
        val parent = folder()
        do {
            val q = "/drive/v3/files?q=${enc("'$parent' in parents and trashed=false")}&pageSize=1000&fields=" +
                URLEncoder.encode("nextPageToken,files(id,name)", "UTF-8") + page
            val j = JSONObject(bytes(open("GET", q)).decodeToString())
            val a = j.getJSONArray("files")
            for (i in 0 until a.length()) out += RemoteFile(a.getJSONObject(i).getString("id"), a.getJSONObject(i).getString("name"))
            page = j.optString("nextPageToken").takeIf { it.isNotEmpty() }?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        } while (page.isNotEmpty())
        return out
    }

    override fun download(id: String): ByteArray = bytes(open("GET", "/drive/v3/files/$id?alt=media"))

    override fun upload(name: String, mime: String, data: ByteArray): String = upload(name, mime, data, folder())

    private fun upload(name: String, mime: String, data: ByteArray, parent: String): String {
        val boundary = "cards" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(parent)).toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(data)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val c = open("POST", "/upload/drive/v3/files?uploadType=multipart&fields=id", "multipart/related; boundary=$boundary", body)
        return JSONObject(bytes(c).decodeToString()).getString("id")
    }

    override fun delete(id: String) {
        val c = open("DELETE", "/drive/v3/files/$id")
        if (c.responseCode !in 200..299 && c.responseCode != 404) throw DriveError(c.responseCode, "delete")
        c.disconnect()
    }
}

const val FOLDER_NAME = "My Cards App"
private const val MARKER = "cardsFolder"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"

class DriveError(val code: Int, msg: String) : Exception("Drive $code: $msg")
