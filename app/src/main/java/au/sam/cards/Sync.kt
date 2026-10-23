package au.sam.cards

import org.json.JSONObject

/**
 * Two-way sync with the Drive appDataFolder. Each card is one file named `card-<id>-<updated>.json`, so the
 * file list alone says which side is newer; images are `img-*.jpg`, named once and never rewritten.
 */
object Sync {
    private val cardName = Regex("""card-([0-9a-f]+)-(\d+)\.json""")

    class Report(val up: Int, val down: Int, val removed: Int)

    @Synchronized
    fun run(store: Store, drive: Drive): Report {
        var up = 0; var down = 0; var removed = 0
        val files = drive.list()
        val versions = HashMap<String, MutableList<Triple<Long, DriveFile, String>>>()
        val images = HashMap<String, DriveFile>()
        for (f in files) {
            val m = cardName.matchEntire(f.name)
            if (m != null) versions.getOrPut(m.groupValues[1]) { mutableListOf() } += Triple(m.groupValues[2].toLong(), f, f.name)
            else if (f.name.startsWith("img-")) images[f.name] = f
        }

        // Deletions made on this device since the last sync.
        val deleted = store.deletedIds()
        for (id in deleted) versions.remove(id)?.forEach { drive.delete(it.second.id); removed++ }
        store.writeDeleted(emptySet())

        // Cards: newest version per id wins; extra versions are leftovers.
        val best = HashMap<String, Pair<Long, DriveFile>>()
        for ((id, list) in versions) {
            val top = list.maxBy { it.first }
            best[id] = top.first to top.second
            list.filter { it !== top }.forEach { drive.delete(it.second.id) }
        }

        for ((id, r) in best) {
            val local = store.get(id)
            if (local == null || r.first > local.updated) {
                val card = Card.fromJson(JSONObject(drive.download(r.second.id).decodeToString())).copy(everSynced = true)
                store.put(card); down++
            } else if (local.updated > r.first) {
                drive.upload("card-$id-${local.updated}.json", "application/json", local.toJson().toString().toByteArray())
                drive.delete(r.second.id); store.markSynced(id); up++
            } else store.markSynced(id)
        }
        for (local in store.cards.toList()) {
            if (local.id in best) continue
            if (local.everSynced) { store.delete(local.id, remembered = false); removed++ } // removed on another device
            else {
                drive.upload("card-${local.id}-${local.updated}.json", "application/json", local.toJson().toString().toByteArray())
                store.markSynced(local.id); up++
            }
        }

        // Images: fetch what cards need and we lack, push what we have and the cloud lacks, drop unreferenced cloud ones.
        val wanted = HashSet<String>()
        for (c in store.cards) for (img in c.images()) {
            wanted += img
            if (!store.hasImage(img)) images[img]?.let { store.imageFile(img).writeBytes(drive.download(it.id)) }
            else if (img !in images) images[img] = DriveFile(drive.upload(img, "image/jpeg", store.imageFile(img).readBytes()), img)
        }
        for ((name, f) in images) if (name !in wanted) drive.delete(f.id)

        store.lastSync = System.currentTimeMillis()
        return Report(up, down, removed)
    }
}
