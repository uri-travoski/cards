package au.sam.cards

import org.json.JSONObject
import java.util.UUID

val TYPES = listOf("bank" to "Bank", "credit" to "Credit", "loyalty" to "Loyalty", "other" to "Other")

val PALETTE = listOf(
    0xFF1E5BD8.toInt(), 0xFF0F8B6D.toInt(), 0xFFC62828.toInt(), 0xFF6A1B9A.toInt(),
    0xFFEF6C00.toInt(), 0xFF37474F.toInt(), 0xFF00838F.toInt(), 0xFFAD1457.toInt(),
    0xFF2E7D32.toInt(), 0xFF111111.toInt(),
)

data class Card(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(16),
    val type: String = "loyalty",
    val name: String = "",
    val number: String = "",
    val holder: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val pin: String = "",
    val barcode: String = "",
    val format: String = "CODE_128",
    val notes: String = "",
    val color: Int = PALETTE[0],
    val front: String = "",
    val back: String = "",
    val updated: Long = System.currentTimeMillis(),
    // Local only: true once this card has existed in the cloud, so a later absence there means "deleted elsewhere".
    val everSynced: Boolean = false,
) {
    fun images() = listOf(front, back).filter { it.isNotEmpty() }

    fun toJson(local: Boolean = false) = JSONObject().apply {
        put("id", id); put("type", type); put("name", name); put("number", number)
        put("holder", holder); put("expiry", expiry); put("cvv", cvv); put("pin", pin)
        put("barcode", barcode); put("format", format); put("notes", notes); put("color", color)
        put("front", front); put("back", back); put("updated", updated)
        if (local) put("everSynced", everSynced)
    }

    companion object {
        fun fromJson(o: JSONObject) = Card(
            id = o.getString("id"), type = o.optString("type", "other"), name = o.optString("name"),
            number = o.optString("number"), holder = o.optString("holder"), expiry = o.optString("expiry"),
            cvv = o.optString("cvv"), pin = o.optString("pin"), barcode = o.optString("barcode"),
            format = o.optString("format", "CODE_128"), notes = o.optString("notes"),
            color = o.optInt("color", PALETTE[0]), front = o.optString("front"), back = o.optString("back"),
            updated = o.optLong("updated"), everSynced = o.optBoolean("everSynced"),
        )
    }
}
