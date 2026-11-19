package id.tukangkilat.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.theme.TKColor

@Composable
fun PendingVerificationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(TKColor.OrangeSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.HourglassEmpty, null, tint = TKColor.Orange, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Menunggu verifikasi admin",
            color = TKColor.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Pendaftaran tukang Anda sudah kami terima. Tim kami akan memeriksa data diri, KTP, dan selfie Anda sebelum akun bisa digunakan.",
            color = TKColor.Mist,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        TKPrimaryButton("Kembali ke halaman masuk", onBack, modifier = Modifier.width(280.dp))
    }
}
