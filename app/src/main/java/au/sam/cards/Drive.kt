package au.sam.cards

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Google Drive v3 client for the hidden per-app folder (appDataFolder). */
class DriveBackend(private val token: String, private val base: String = BuildConfig.MOCK_DRIVE.ifEmpty { "https://www.googleapis.com" }) : Backend {

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

    override fun list(): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var page = ""
        do {
            val q = "/drive/v3/files?spaces=appDataFolder&pageSize=1000&fields=" +
                URLEncoder.encode("nextPageToken,files(id,name)", "UTF-8") + page
            val j = JSONObject(bytes(open("GET", q)).decodeToString())
            val a = j.getJSONArray("files")
            for (i in 0 until a.length()) out += RemoteFile(a.getJSONObject(i).getString("id"), a.getJSONObject(i).getString("name"))
            page = j.optString("nextPageToken").takeIf { it.isNotEmpty() }?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        } while (page.isNotEmpty())
        return out
    }

    override fun download(id: String): ByteArray = bytes(open("GET", "/drive/v3/files/$id?alt=media"))

    override fun upload(name: String, mime: String, data: ByteArray): String {
        val boundary = "cards" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
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

class DriveError(val code: Int, msg: String) : Exception("Drive $code: $msg")
