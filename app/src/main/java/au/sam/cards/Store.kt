package au.sam.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Local copy of the cards (JSON + image files) that works offline; [Sync] mirrors it to Drive. */
class Store(private val ctx: Context) {
    private val dir = ctx.filesDir
    private val imgDir = File(dir, "img").apply { mkdirs() }
    private val cardsFile = File(dir, "cards.json")
    private val deletedFile = File(dir, "deleted.json")
    private val prefs = ctx.getSharedPreferences("cards", Context.MODE_PRIVATE)

    var cards by mutableStateOf(listOf<Card>())
        private set

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(v) = prefs.edit().putString("email", v).apply()
    private var signedInState by mutableStateOf(prefs.getBoolean("signedIn", false))
    private var offlineState by mutableStateOf(prefs.getBoolean("offline", false))
    var signedIn: Boolean
        get() = signedInState
        set(v) { signedInState = v; prefs.edit().putBoolean("signedIn", v).apply() }
    var offline: Boolean
        get() = offlineState
        set(v) { offlineState = v; prefs.edit().putBoolean("offline", v).apply() }
    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", true)
        set(v) = prefs.edit().putBoolean("lock", v).apply()
    var lastSync: Long
        get() = prefs.getLong("lastSync", 0)
        set(v) = prefs.edit().putLong("lastSync", v).apply()

    init {
        cards = readCards()
    }

    private fun readCards(): List<Card> = try {
        val a = JSONArray(cardsFile.readText())
        (0 until a.length()).map { Card.fromJson(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    private fun commit(list: List<Card>) {
        cards = list
        val a = JSONArray()
        list.forEach { a.put(it.toJson(local = true)) }
        val tmp = File(dir, "cards.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(cardsFile)
    }

    fun get(id: String) = cards.firstOrNull { it.id == id }

    /** Add or edit a user's card. Stamps it as changed now. */
    @Synchronized
    fun save(card: Card) {
        val old = get(card.id)
        val next = card.copy(updated = maxOf(System.currentTimeMillis(), (old?.updated ?: 0) + 1))
        commit(if (old == null) cards + next else cards.map { if (it.id == card.id) next else it })
        old?.images()?.filter { it !in next.images() }?.forEach { imageFile(it).delete() }
    }

    /** Sync result: put a card in exactly as the cloud has it. */
    @Synchronized
    fun put(card: Card) {
        commit(if (get(card.id) == null) cards + card else cards.map { if (it.id == card.id) card else it })
    }

    @Synchronized
    fun markSynced(id: String) {
        get(id)?.let { put(it.copy(everSynced = true)) }
    }

    @Synchronized
    fun delete(id: String, remembered: Boolean = true) {
        val c = get(id) ?: return
        commit(cards.filter { it.id != id })
        c.images().forEach { imageFile(it).delete() }
        if (remembered && c.everSynced) writeDeleted(deletedIds() + id)
    }

    fun deletedIds(): Set<String> = try {
        val a = JSONArray(deletedFile.readText())
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    @Synchronized
    fun writeDeleted(ids: Set<String>) {
        deletedFile.writeText(JSONArray(ids.toList()).toString())
    }

    fun imageFile(name: String) = File(imgDir, name)

    fun hasImage(name: String) = imageFile(name).exists()

    /** Decode a picked/captured photo, shrink it, and keep it as a new local image file. */
    fun importImage(uri: Uri): String? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 3200) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = "img-" + UUID.randomUUID().toString().replace("-", "").take(16) + ".jpg"
        imageFile(name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    } catch (_: Exception) {
        null
    }

    /** Remove images no card refers to any more (abandoned edits). */
    fun pruneImages() {
        val keep = cards.flatMap { it.images() }.toSet()
        imgDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    fun wipe() {
        commit(emptyList())
        writeDeleted(emptySet())
        imgDir.listFiles()?.forEach { it.delete() }
        lastSync = 0
    }
}
