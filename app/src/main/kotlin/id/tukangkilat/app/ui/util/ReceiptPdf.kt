package id.tukangkilat.app.ui.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderDto
import java.io.File

/** Builds a shareable PDF receipt using only Android's built-in PDF APIs. */
object ReceiptPdf {

    fun createAndShare(context: Context, order: OrderDto) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val ink = Paint().apply { color = Color.rgb(27, 39, 77); isAntiAlias = true }
        val orange = Paint().apply { color = Color.rgb(232, 104, 30); isAntiAlias = true }
        val grey = Paint().apply { color = Color.rgb(120, 130, 145); isAntiAlias = true }
        val title = Paint(ink).apply { textSize = 26f }
        val heading = Paint(ink).apply { textSize = 15f }
        val body = Paint(ink).apply { textSize = 12f }
        val small = Paint(grey).apply { textSize = 11f }
        val totalPaint = Paint(ink).apply { textSize = 16f }

        var y = 60f
        canvas.drawText("TukangKilat", 48f, y, title)
        y += 22f
        canvas.drawText("Ahlinya rumah, siap membantu", 48f, y, small)
        y += 44f
        canvas.drawText("Struk ${order.code}", 48f, y, heading)
        y += 28f
        canvas.drawText("Status: SELESAI", 48f, y, small)
        y += 30f

        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, 48f, y, paint)
            val right = 547f - paint.measureText(value)
            canvas.drawText(value, right, y, paint)
            y += 26f
        }

        line("Layanan", order.serviceName)
        line("Tanggal", order.scheduledDate)
        line("Slot", order.slotLabel)
        y += 10f
        val addressLines = wrap(order.addressFull, 60)
        addressLines.forEach { text ->
            canvas.drawText(text, 48f, y, small)
            y += 18f
        }
        y += 20f

        canvas.drawLine(48f, y, 547f, y, orange)
        y += 28f
        line("Biaya jasa", Format.rupiah(order.serviceFee))
        line("Biaya kunjungan", Format.rupiah(order.visitFee))
        if (order.discount > 0) line("Diskon", "-${Format.rupiah(order.discount)}")
        y += 8f
        canvas.drawLine(48f, y, 547f, y, orange)
        y += 34f
        canvas.drawText("Total pembayaran", 48f, y, body)
        val totalText = Format.rupiah(order.total)
        canvas.drawText(totalText, 547f - totalPaint.measureText(totalText), y, totalPaint)
        y += 32f
        canvas.drawText("Lunas", 48f, y, orange)

        document.finishPage(page)
        val file = File(context.cacheDir, "struk-${order.code}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Struk ${order.code}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Bagikan struk PDF"))
    }

    private fun wrap(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val result = mutableListOf<String>()
        var rest = text
        while (rest.length > limit) {
            var cut = limit
            while (cut > 0 && rest[cut] != ' ') cut--
            if (cut == 0) cut = limit
            result += rest.substring(0, cut)
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) result += rest
        return result
    }
}
