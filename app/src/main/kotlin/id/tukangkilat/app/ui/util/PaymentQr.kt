package id.tukangkilat.app.ui.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Generates a real QR bitmap from a payment payload. No network dependency. */
object PaymentQr {
    fun bitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun image(content: String, size: Int = 512): ImageBitmap = bitmap(content, size).asImageBitmap()
}

/** Builds an EMVCo-style QRIS payload with a valid CRC16 checksum. */
object QrisPayload {
    private fun tlv(tag: String, value: String): String {
        val length = value.toByteArray(Charsets.UTF_8).size.toString().padStart(2, '0')
        return "$tag$length$value"
    }

    private fun crc16(input: String): String {
        var crc = 0xFFFF
        for (byte in input.toByteArray(Charsets.UTF_8)) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x0001 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return String.format("%04X", crc and 0xFFFF)
    }

    /**
     * Dynamic QRIS payload for a simulated payment. Merchant data is illustrative; the QR is
     * structurally valid and can be scanned, but it is not a registered merchant account.
     */
    fun build(amount: Long, reference: String, merchant: String = "TUKANGKILAT"): String {
        val merchantAccount = tlv("00", "ID.CO.TUKANGKILAT") +
            tlv("01", reference.take(25)) +
            tlv("02", "QRIS")
        val body = tlv("00", "01") +
            tlv("01", "12") +
            tlv("26", merchantAccount) +
            tlv("52", "7392") +
            tlv("53", "360") +
            tlv("54", amount.toString()) +
            tlv("58", "ID") +
            tlv("59", merchant.take(25)) +
            tlv("60", "JAKARTA") +
            tlv("62", tlv("05", reference.take(20))) +
            "6304"
        return body + crc16(body)
    }

    /** Simple text payload for bank transfer and e-wallet references. */
    fun buildSimple(provider: String, reference: String, amount: Long): String =
        "TUKANGKILAT|$provider|$reference|$amount"
}
