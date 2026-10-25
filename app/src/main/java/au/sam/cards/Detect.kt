package au.sam.cards

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

private val dateRe = Regex("""(?<!\d)(0[1-9]|1[0-2])\s?[/\-]\s?(\d{4}|\d{2})(?!\d)""")

/** Latest (year, month) among the MM/YY dates in [text] ("VALID THRU 12/27" beats "MEMBER SINCE 05/19"), or null. */
private fun latestDate(text: String, from: Pair<Int, Int>? = null): Pair<Int, Int>? {
    val thisYear = java.time.Year.now().value
    var best = from
    for (m in dateRe.findAll(text)) {
        val month = m.groupValues[1].toInt()
        val year = m.groupValues[2].toInt().let { if (m.groupValues[2].length == 2) it + 2000 else it }
        if (year !in 2000..thisYear + 25) continue
        if (best == null || year * 12 + month > best.first * 12 + best.second) best = year to month
    }
    return best
}

suspend fun readExpiry(photos: List<Bitmap>): String? {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var best: Pair<Int, Int>? = null
    try {
        for (p in photos) best = latestDate(recognizer.process(InputImage.fromBitmap(p, 0)).await().text, best)
    } finally {
        recognizer.close()
    }
    return best?.let { formatExpiry(it.first, it.second) }
}

/** What the app could read from one card photo. Every field is a guess and may be wrong. */
class CardGuess(
    val type: String? = null, val brand: String? = null, val number: String? = null, val expiry: String? = null,
    val holder: String? = null, val barcode: String? = null, val format: String? = null,
) {
    val any get() = listOf(number, expiry, holder, barcode).any { it != null }
}

fun luhn(digits: String): Boolean {
    var sum = 0
    for ((i, ch) in digits.reversed().withIndex()) {
        var d = ch - '0'
        if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
        sum += d
    }
    return digits.length in 13..19 && sum % 10 == 0
}

fun brandOf(n: String): String? = when {
    n.startsWith("4") -> "Visa"
    n.take(2).toIntOrNull() in 51..55 || n.take(4).toIntOrNull() in 2221..2720 -> "Mastercard"
    n.startsWith("34") || n.startsWith("37") -> "Amex"
    n.startsWith("6011") || n.startsWith("65") -> "Discover"
    n.take(2).toIntOrNull() in 35..35 -> "JCB"
    n.startsWith("36") || n.startsWith("38") -> "Diners"
    n.startsWith("62") -> "UnionPay"
    else -> null
}

private val numberRe = Regex("""(?<!\d)\d(?:[ -]?\d){11,18}(?!\d)""")
private val notName = Regex(
    "VALID|THRU|FROM|EXPIRES|MEMBER|SINCE|GOOD|DEBIT|CREDIT|BANK|VISA|MASTER|CARD|AMERICAN|EXPRESS|PLATINUM|GOLD|CLASSIC|" +
        "WORLD|SIGNATURE|AUTHORI|CUSTOMER|SERVICE|CONTACTLESS|REWARDS|PREMIER|BUSINESS|ELECTRON|MAESTRO|DISCOVER|UNION|PAY|" +
        "CVV|CVC|INTERNATIONAL|LIMITED|BANKING|TRANSACTION|ACCOUNT|NUMBER|PHONE|WWW|HTTP|\\.COM",
)

/** Reads number, expiry, holder, barcode and a likely type from one photo (a side of a card). */
suspend fun readCard(photo: Bitmap): CardGuess {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var number: String? = null
    var expiry: String? = null
    var holder: String? = null
    var brand: String? = null
    var debit = false
    try {
        val text = recognizer.process(InputImage.fromBitmap(photo, 0)).await()
        val all = text.text.uppercase()
        debit = "DEBIT" in all
        val lines = text.textBlocks.flatMap { it.lines }.map { it.text.uppercase().trim() }
        var numberLine = -1
        for ((i, line) in lines.withIndex()) {
            val m = numberRe.findAll(line).map { it.value.filter(Char::isDigit) }.firstOrNull { luhn(it) } ?: continue
            number = m; numberLine = i; break
        }
        if (number == null) number = numberRe.findAll(all).map { it.value.filter(Char::isDigit) }.firstOrNull { luhn(it) }
        brand = number?.let { brandOf(it) }
        expiry = latestDate(all)?.let { formatExpiry(it.first, it.second) }
        // A name: 2 to 4 words of letters only, not a known card word, preferably below the number.
        val cand = lines.withIndex().filter { (_, l) ->
            Regex("""[A-Z][A-Z.'-]+( [A-Z][A-Z.'-]*){0,2} [A-Z][A-Z.'-]+""").matches(l) && l.length in 5..26 && !notName.containsMatchIn(l)
        }
        holder = (cand.firstOrNull { it.index > numberLine && numberLine >= 0 } ?: cand.firstOrNull())?.value
    } finally {
        recognizer.close()
    }
    val code = readBarcode(listOf(photo))
    val type = when {
        number != null -> if (debit) "bank" else "credit"
        code != null -> "loyalty"
        else -> null
    }
    return CardGuess(type, brand, number, expiry, holder, code?.first, code?.second)
}

/** (value, format) of the first barcode or QR code found in the photos, or null. */
suspend fun readBarcode(photos: List<Bitmap>): Pair<String, String>? {
    val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    try {
        for (p in photos) {
            val b = scanner.process(InputImage.fromBitmap(p, 0)).await().firstOrNull { it.rawValue != null } ?: continue
            return b.rawValue!! to formatFromScanner(b.format)
        }
    } finally {
        scanner.close()
    }
    return null
}
