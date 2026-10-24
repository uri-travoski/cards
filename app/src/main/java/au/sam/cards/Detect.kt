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

/** Latest MM/YY found in the photos' text ("VALID THRU 12/27" beats "MEMBER SINCE 05/19"), or null. */
suspend fun readExpiry(photos: List<Bitmap>): String? {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val thisYear = java.time.Year.now().value
    var best: Pair<Int, Int>? = null
    try {
        for (p in photos) {
            val text = recognizer.process(InputImage.fromBitmap(p, 0)).await().text
            for (m in dateRe.findAll(text)) {
                val month = m.groupValues[1].toInt()
                val year = m.groupValues[2].toInt().let { if (m.groupValues[2].length == 2) it + 2000 else it }
                if (year !in 2000..thisYear + 25) continue
                if (best == null || year * 12 + month > best.first * 12 + best.second) best = year to month
            }
        }
    } finally {
        recognizer.close()
    }
    return best?.let { formatExpiry(it.first, it.second) }
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
