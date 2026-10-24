package au.sam.cards

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val APK_URL = "https://github.com/uri-travoski/cards/releases/latest/download/cards.apk"

/** Latest released version name on GitHub (for example "0.3.0"), or null when it can't be reached. */
suspend fun latestVersion(): String? = withContext(Dispatchers.IO) {
    try {
        val c = URL("https://api.github.com/repos/uri-travoski/cards/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) null
            else JSONObject(c.inputStream.bufferedReader().readText()).getString("tag_name").removePrefix("v")
        } finally { c.disconnect() }
    } catch (e: Exception) { null }
}

fun isNewer(latest: String, current: String): Boolean {
    val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
