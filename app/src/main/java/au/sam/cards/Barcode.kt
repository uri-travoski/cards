package au.sam.cards

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Formats we can draw, as (ZXing name, label). */
val FORMATS = listOf(
    "CODE_128" to "Code 128", "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "UPC_A" to "UPC-A", "UPC_E" to "UPC-E",
    "CODE_39" to "Code 39", "ITF" to "ITF", "CODABAR" to "Codabar", "QR_CODE" to "QR", "PDF_417" to "PDF417",
    "AZTEC" to "Aztec", "DATA_MATRIX" to "Data Matrix",
)

fun formatFromScanner(f: Int) = when (f) {
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_PDF417 -> "PDF_417"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    else -> "CODE_128"
}

private val twoD = setOf("QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX")

fun is2D(format: String) = format in twoD

/** Black-on-white bitmap for [value], or null when the value isn't valid for the format. */
fun renderBarcode(value: String, format: String, width: Int = 1000): Bitmap? = try {
    val f = BarcodeFormat.valueOf(format)
    val h = if (format in twoD) width else width / 3
    val m = MultiFormatWriter().encode(value, f, width, h, mapOf(EncodeHintType.MARGIN to 2))
    val px = IntArray(m.width * m.height) { if (m.get(it % m.width, it / m.width)) Color.BLACK else Color.WHITE }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}
