package id.tukangkilat.app.ui.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/** Lightweight native signature pad that exports a base64 PNG data URL. */
@Composable
fun SignaturePad(onSaved: (String) -> Unit) {
    val paths = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(androidx.compose.ui.graphics.Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current.add(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            current.add(change.position)
                        },
                        onDragEnd = {
                            if (current.size > 1) paths.add(current.toList())
                            current.clear()
                        }
                    )
                }
        ) {
            (paths + listOf(current.toList())).forEach { points ->
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, androidx.compose.ui.graphics.Color.Black, style = Stroke(width = 4f))
                }
            }
        }
        Row {
            TextButton(onClick = {
                if (current.size > 1) paths.add(current.toList())
                current.clear()
            }) { Text("Selesaikan goresan") }
            TextButton(onClick = {
                paths.clear()
                current.clear()
            }) { Text("Hapus") }
            TextButton(onClick = {
                val finished = paths + listOf(current.toList())
                val bitmap = Bitmap.createBitmap(400, 160, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint().apply {
                    color = Color.BLACK
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 5f
                }
                finished.forEach { points ->
                    for (i in 1 until points.size) {
                        canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
                    }
                }
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, bytes)
                onSaved("data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
            }) { Text("Simpan tanda tangan") }
        }
    }
}
