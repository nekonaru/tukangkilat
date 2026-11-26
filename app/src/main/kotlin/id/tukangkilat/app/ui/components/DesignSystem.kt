package id.tukangkilat.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.tukangkilat.app.R
import id.tukangkilat.app.ui.theme.*
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderStatus
import kotlinx.coroutines.delay

enum class TKButtonVariant { Filled, Tonal, Outlined, Text }

/** Filled, Tonal, Outlined and Text buttons with loading and disabled states. */
@Composable
fun TKButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TKButtonVariant = TKButtonVariant.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    val colors = when (variant) {
        TKButtonVariant.Filled -> ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.primary.copy(alpha = 0.38f),
            disabledContentColor = scheme.onPrimary.copy(alpha = 0.7f)
        )
        TKButtonVariant.Tonal -> ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            disabledContainerColor = scheme.primaryContainer.copy(alpha = 0.38f),
            disabledContentColor = scheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        TKButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
            contentColor = scheme.primary,
            disabledContentColor = scheme.onSurfaceVariant
        )
        TKButtonVariant.Text -> ButtonDefaults.textButtonColors(
            contentColor = scheme.primary,
            disabledContentColor = scheme.onSurfaceVariant
        )
    }
    val button: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(TKSpacing.Sm),
                color = if (variant == TKButtonVariant.Filled) scheme.onPrimary else scheme.primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(TKSpacing.Xs))
            Text("Memproses...", style = MaterialTheme.typography.labelLarge)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(TKSpacing.Xs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    when (variant) {
        TKButtonVariant.Filled, TKButtonVariant.Tonal -> Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.heightIn(min = TKSpacing.MinTouch),
            shape = TKShape.Button,
            colors = colors,
            content = { button() }
        )
        TKButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.heightIn(min = TKSpacing.MinTouch),
            shape = TKShape.Button,
            colors = colors,
            border = ButtonDefaults.outlinedButtonBorder(enabled = enabled && !loading),
            content = { button() }
        )
        TKButtonVariant.Text -> TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.heightIn(min = TKSpacing.MinTouch),
            shape = TKShape.Button,
            colors = colors,
            content = { button() }
        )
    }
}

/** Outlined text field with normal, focused and error states. */
@Composable
fun TKOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    leadingIcon: ImageVector? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label.isNullOrBlank()) null else { { Text(label) } },
        placeholder = placeholder?.let {
            { Text(it, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        },
        isError = error,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = TKShape.Field,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.onSurface,
            unfocusedBorderColor = scheme.outline,
            focusedLabelColor = scheme.onSurface,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            cursorColor = scheme.onSurface,
            errorBorderColor = scheme.error,
            errorLabelColor = scheme.error,
            errorCursorColor = scheme.error,
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface
        ),
        supportingText = if (error) {
            { Text(errorMessage ?: "Periksa lagi isian kamu", color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        } else null
    )
}

private data class StatusStyle(val label: String, val icon: ImageVector, val container: Color, val content: Color)

@Composable
private fun statusStyle(status: OrderStatus): StatusStyle {
    val scheme = MaterialTheme.colorScheme
    val extended = tukangKilatColors()
    val neutral = StatusStyle(
        label = statusLabel(status),
        icon = Icons.Filled.Schedule,
        container = scheme.surfaceVariant,
        content = scheme.onSurfaceVariant
    )
    return when (status) {
        OrderStatus.MENUNGGU_PEMBAYARAN -> neutral.copy(
            icon = Icons.Filled.Payment, container = extended.warningContainer, content = extended.warning
        )
        OrderStatus.PEMBAYARAN_GAGAL -> neutral.copy(
            icon = Icons.Filled.ErrorOutline, container = scheme.errorContainer, content = scheme.error
        )
        OrderStatus.MENCARI_TEKNISI, OrderStatus.TEKNISI_DITEMUKAN,
        OrderStatus.DALAM_PERJALANAN, OrderStatus.TIBA -> neutral
        OrderStatus.SEDANG_BEKERJA -> neutral.copy(
            icon = Icons.Filled.Build, container = scheme.primaryContainer, content = scheme.onPrimaryContainer
        )
        OrderStatus.MENUNGGU_KONFIRMASI -> neutral.copy(
            icon = Icons.Filled.CheckCircle, container = scheme.primaryContainer, content = scheme.onPrimaryContainer
        )
        OrderStatus.DILAPORKAN -> neutral.copy(
            icon = Icons.Filled.ReportProblem, container = scheme.errorContainer, content = scheme.error
        )
        OrderStatus.KUNJUNGAN_ULANG -> neutral.copy(
            icon = Icons.Filled.Replay, container = extended.warningContainer, content = extended.warning
        )
        OrderStatus.SELESAI -> neutral.copy(
            icon = Icons.Filled.Verified, container = extended.successContainer, content = extended.success
        )
        OrderStatus.DIBATALKAN -> neutral.copy(
            icon = Icons.Filled.Cancel, container = scheme.errorContainer, content = scheme.error
        )
    }
}

private fun statusLabel(status: OrderStatus) = when (status) {
    OrderStatus.MENUNGGU_PEMBAYARAN -> "Menunggu pembayaran"
    OrderStatus.PEMBAYARAN_GAGAL -> "Pembayaran gagal"
    OrderStatus.MENCARI_TEKNISI -> "Mencari teknisi"
    OrderStatus.TEKNISI_DITEMUKAN -> "Dipesan"
    OrderStatus.DALAM_PERJALANAN -> "Teknisi berangkat"
    OrderStatus.TIBA -> "Tiba"
    OrderStatus.SEDANG_BEKERJA -> "Bekerja"
    OrderStatus.MENUNGGU_KONFIRMASI -> "Menunggu konfirmasi"
    OrderStatus.DILAPORKAN -> "Dilaporkan"
    OrderStatus.KUNJUNGAN_ULANG -> "Kunjungan ulang"
    OrderStatus.SELESAI -> "Selesai"
    OrderStatus.DIBATALKAN -> "Dibatalkan"
}

/** Status is always communicated by an icon and a label, never by color alone. */
@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val style = statusStyle(status)
    Surface(
        modifier = modifier,
        shape = TKShape.Button,
        color = style.container,
        contentColor = style.content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TKSpacing.Xs, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(style.label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ServiceCard(icon: ImageVector, name: String, startingPrice: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch),
        shape = TKShape.Card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer, contentColor = scheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TKElevation.Level1)
    ) {
        Row(Modifier.padding(TKSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(scheme.primaryContainer, TKShape.Media),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer) }
            Spacer(Modifier.width(TKSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text("Mulai dari ${Format.rupiah(startingPrice)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TechnicianCard(
    name: String,
    rating: Double,
    jobs: Int,
    skills: String,
    distanceKm: Double,
    verified: Boolean = true,
    busy: Boolean = false,
    photoUrl: String? = null,
    onClick: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val extended = tukangKilatColors()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TKShape.Card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer, contentColor = scheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TKElevation.Level1)
    ) {
        Row(Modifier.padding(TKSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            if (photoUrl.isNullOrBlank()) {
                AvatarCircle(name, size = 48)
            } else {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(photoUrl),
                    contentDescription = "Foto $name",
                    modifier = Modifier.size(48.dp).clip(TKShape.Circle)
                )
            }
            Spacer(Modifier.width(TKSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "★ ${String.format("%.1f", rating)} · $jobs pekerjaan",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                )
                Text(
                    if (busy) "Sedang bertugas · Jadwalkan nanti"
                    else "${if (verified) "Terverifikasi" else "Menunggu verifikasi"} · $skills · ${String.format("%.1f", distanceKm)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (busy) extended.warning else scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    code: String,
    date: String,
    serviceName: String,
    detail: String,
    status: OrderStatus,
    total: Long,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TKShape.Card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer, contentColor = scheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TKElevation.Level1)
    ) {
        Column(Modifier.padding(TKSpacing.Sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(code, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(status)
            }
            Spacer(Modifier.height(TKSpacing.Xs))
            Text("$date · $serviceName", style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.height(TKSpacing.Xs))
            Text(Format.rupiah(total), style = MaterialTheme.typography.titleSmall, color = scheme.primary)
        }
    }
}

@Composable
fun PriceSummary(serviceFee: Long, visitFee: Long, discount: Long, total: Long) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer, TKShape.Card).padding(TKSpacing.Sm)
    ) {
        SummaryRow("Biaya jasa", Format.rupiah(serviceFee))
        SummaryRow("Biaya kunjungan", Format.rupiah(visitFee))
        if (discount > 0) SummaryRow("Diskon", "-${Format.rupiah(discount)}", color = tukangKilatColors().success)
        HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(vertical = TKSpacing.Xs))
        Row(
            Modifier.fillMaxWidth().background(scheme.primaryContainer, TKShape.Button).padding(TKSpacing.Xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total", style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            Text(Format.rupiah(total), style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
fun RatingStars(value: Int, onValueChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
            for (star in 1..5) {
                IconButton(onClick = { onValueChange(star) }, modifier = Modifier.size(TKSpacing.MinTouch)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Beri $star bintang",
                        tint = if (star <= value) scheme.primary else scheme.outlineVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        Text(
            if (value == 0) "Ketuk bintang untuk menilai" else "Bagaimana hasil kerjanya?",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

enum class TimelineState { Done, Active, Waiting }

@Composable
fun TimelineStep(state: TimelineState, title: String, subtitle: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val extended = tukangKilatColors()
    Row(Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(
            when (state) {
                TimelineState.Done -> extended.successContainer
                TimelineState.Active -> scheme.primaryContainer
                TimelineState.Waiting -> scheme.surfaceVariant
            }, CircleShape
        ), contentAlignment = Alignment.Center) {
            when (state) {
                TimelineState.Done -> Icon(Icons.Filled.Check, "Selesai", tint = extended.success, modifier = Modifier.size(16.dp))
                TimelineState.Active -> Box(Modifier.size(10.dp).background(scheme.primary, CircleShape))
                TimelineState.Waiting -> Box(Modifier.size(10.dp).border(1.dp, scheme.outline, CircleShape))
            }
        }
        Spacer(Modifier.width(TKSpacing.Sm))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (state == TimelineState.Waiting) scheme.onSurfaceVariant else scheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun ChatBubble(text: String, time: String, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp).background(
                if (mine) scheme.primaryContainer else scheme.surfaceVariant,
                TKShape.Card
            ).padding(TKSpacing.Xs)
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "$time · ${if (mine) "Terkirim" else "Diterima"}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = if (mine) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NotificationItem(title: String, body: String, time: String, unread: Boolean, onClick: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(
            if (unread) scheme.primaryContainer else scheme.surfaceContainer,
            TKShape.Card
        ).padding(TKSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi", tint = scheme.primary)
        Spacer(Modifier.width(TKSpacing.Sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
            Text(time, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

enum class StateViewKind { Empty, Error, Success, Disabled, Waiting }

@Composable
fun StateView(
    kind: StateViewKind,
    title: String? = null,
    description: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val extended = tukangKilatColors()
    val (icon, defaultTitle, tint) = when (kind) {
        StateViewKind.Empty -> Triple(Icons.Filled.Inbox, "Belum ada pesanan", scheme.onSurfaceVariant)
        StateViewKind.Error -> Triple(Icons.Filled.ErrorOutline, "Ada kendala sebentar", scheme.error)
        StateViewKind.Success -> Triple(Icons.Filled.Verified, "Berhasil!", extended.success)
        StateViewKind.Disabled -> Triple(Icons.Filled.Block, "Belum tersedia", scheme.onSurfaceVariant)
        StateViewKind.Waiting -> Triple(Icons.Filled.HourglassTop, "Menunggu", extended.warning)
    }
    Column(
        Modifier.fillMaxWidth().padding(TKSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(TKSpacing.Sm))
        Text(title ?: defaultTitle, style = MaterialTheme.typography.titleMedium)
        description?.let {
            Spacer(Modifier.height(TKSpacing.Xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (onRetry != null) {
            Spacer(Modifier.height(TKSpacing.Sm))
            TKButton("Coba lagi", onRetry, variant = TKButtonVariant.Tonal)
        }
    }
}

/** Countdown starts at 01:00 and is rendered as mm:ss, never as 00:60. */
@Composable
fun JobOfferCard(
    serviceName: String,
    distanceKm: Double,
    area: String,
    netFee: Long,
    secondsLeft: Int = 60,
    serviceLabel: String = "Perbaikan listrik",
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val seconds = produceState(initialValue = secondsLeft, secondsLeft) {
        var value = secondsLeft
        while (value > 0) {
            delay(1000)
            value -= 1
            this.value = value
        }
    }.value
    val countdown = "${String.format("%02d", seconds / 60)}:${String.format("%02d", seconds % 60)}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TKShape.Card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer, contentColor = scheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TKElevation.Level1)
    ) {
        Column(Modifier.padding(TKSpacing.Sm)) {
            Text(countdown, style = MaterialTheme.typography.titleLarge, color = scheme.primary)
            Text("$serviceLabel · $serviceName", style = MaterialTheme.typography.titleSmall)
            Text("${String.format("%.1f", distanceKm)} km · $area", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text("Estimasi bersih ${Format.rupiah(netFee)}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(TKSpacing.Xs))
            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                TKButton("Terima", onAccept, modifier = Modifier.weight(1f))
                TKButton("Tolak", onReject, modifier = Modifier.weight(1f), variant = TKButtonVariant.Outlined)
            }
        }
    }
}

@Composable
fun TKSnackbar(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(TKSpacing.Sm),
        shape = TKShape.Card,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        action = { TextButton(onClick = onDismiss) { Text("Tutup", color = MaterialTheme.colorScheme.onSecondary) } }
    ) { Text(message) }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String = "Ya", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TKShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TKButton(confirmLabel, onConfirm, variant = TKButtonVariant.Filled) },
        dismissButton = { TKButton("Batal", onDismiss, variant = TKButtonVariant.Text) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TKBottomSheet(title: String, options: List<String>, selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = TKShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = TKSpacing.Sm).padding(bottom = TKSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(TKSpacing.Xs))
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch).clickable { onSelect(option) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option == selected, onClick = { onSelect(option) })
                    Text(option, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ChoiceFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = TKShape.Button,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun SkeletonCard() {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "skeleton")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha"
    )
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (reduceMotion) 0.6f else alpha)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TKShape.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(TKSpacing.Sm)) {
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(12.dp).background(tint, TKShape.Field))
                Spacer(Modifier.height(TKSpacing.Xs))
            }
        }
    }
}

@Composable
fun AvailabilitySwitch(online: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extended = tukangKilatColors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch).background(scheme.surfaceContainer, TKShape.Card)
            .padding(horizontal = TKSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (online) "Kamu sedang online" else "Kamu sedang offline",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = online,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = extended.success,
                uncheckedThumbColor = scheme.onSurfaceVariant,
                uncheckedTrackColor = scheme.surfaceVariant
            )
        )
    }
}

@Composable
fun TKFab(icon: ImageVector, description: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = TKShape.Button,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = TKElevation.Level3)
    ) { Icon(icon, contentDescription = description) }
}

@Composable
fun TKTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showBell: Boolean = true,
    onBell: () -> Unit = {},
    homeVariant: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surface, shadowElevation = TKElevation.Level2) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = TKSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (homeVariant) {
                Icon(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = "Tukang Kilat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(TKSpacing.Xs))
                Text("Tukang Kilat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            } else {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(TKSpacing.MinTouch)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = scheme.onSurface)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (showBell) {
                IconButton(onClick = onBell, modifier = Modifier.size(TKSpacing.MinTouch)) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi", tint = scheme.onSurface)
                }
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun TKNavigationBar(role: String, selectedRoute: String, onNavigate: (String) -> Unit) {
    val items = when (role) {
        "TUKANG" -> listOf(
            NavItem("technician/home", "Beranda", Icons.Filled.Home),
            NavItem("technician/jobs", "Pekerjaan", Icons.Filled.Assignment),
            NavItem("technician/earnings", "Pendapatan", Icons.Filled.Payments),
            NavItem("technician/account", "Akun", Icons.Filled.Person)
        )
        "ADMIN" -> listOf(
            NavItem("admin/verify", "Verifikasi", Icons.Filled.VerifiedUser),
            NavItem("admin/reports", "Laporan", Icons.Filled.Report),
            NavItem("admin/services", "Layanan", Icons.Filled.Build),
            NavItem("admin/promo", "Promo", Icons.Filled.LocalOffer),
            NavItem("admin/summary", "Lainnya", Icons.Filled.MoreHoriz)
        )
        else -> listOf(
            NavItem("consumer/home", "Beranda", Icons.Filled.Home),
            NavItem("consumer/orders", "Pesanan", Icons.Filled.ReceiptLong),
            NavItem("consumer/chats", "Chat", Icons.Filled.Chat),
            NavItem("consumer/account", "Akun", Icons.Filled.Person)
        )
    }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = TKElevation.Level2
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.route == selectedRoute,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun DesignCatalog() {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs),
        contentPadding = PaddingValues(TKSpacing.Sm)
    ) {
        item {
            Text("Komponen Tukang Kilat", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(TKSpacing.Sm))
        }
        item { TKButton("Lanjutkan", {}) }
        item { TKButton("Tonal", {}, variant = TKButtonVariant.Tonal) }
        item { TKButton("Outlined", {}, variant = TKButtonVariant.Outlined) }
        item { TKButton("Teks", {}, variant = TKButtonVariant.Text) }
        item { TKButton("Memproses", {}, loading = true) }
        item { TKOutlinedField("", {}, "Nama layanan", placeholder = "Contoh: Perbaikan listrik") }
        item { TKOutlinedField("salah", {}, "Kode promo", error = true) }
        item { StatusChip(OrderStatus.MENUNGGU_PEMBAYARAN) }
        item { StatusChip(OrderStatus.SEDANG_BEKERJA) }
        item { StatusChip(OrderStatus.SELESAI) }
        item { ServiceCard(Icons.Filled.Bolt, "Listrik", 120000L) {} }
        item { TechnicianCard("Budi Santoso", 4.9, 128, "Listrik & AC", 1.2) }
        item { TechnicianCard("Sari Wulandari", 4.8, 96, "Pipa", 2.4, busy = true) }
        item { OrderCard("TK-261126-01", "26 Nov 2026", "Perbaikan listrik", "Jl. Tebet No. 12", OrderStatus.SELESAI, 125000L) {} }
        item { PriceSummary(120000L, 30000L, 25000L, 125000L) }
        item { RatingStars(4) {} }
        item { TimelineStep(TimelineState.Done, "Pesanan dibuat") }
        item { TimelineStep(TimelineState.Active, "Teknisi menuju lokasi") }
        item { TimelineStep(TimelineState.Waiting, "Pekerjaan selesai") }
        item { ChatBubble("Halo, saya sudah berangkat", "14:20", mine = false) }
        item { ChatBubble("Baik, saya tunggu ya", "14:21", mine = true) }
        item { NotificationItem("Status pesanan diperbarui", "Teknisi sedang menuju lokasi kamu", "14:22", true) }
        item { StateView(StateViewKind.Empty) }
        item { StateView(StateViewKind.Error, onRetry = {}) }
        item { StateView(StateViewKind.Success) }
        item { JobOfferCard("Perbaikan listrik", 1.2, "Tebet", 132000L, onAccept = {}, onReject = {}) }
        item { ChoiceFilterChip("Listrik", true) {} }
        item { ChoiceFilterChip("AC", false) {} }
        item { SkeletonCard() }
        item { AvailabilitySwitch(true) {} }
        item { TKTopAppBar("Judul layar", homeVariant = false) }
        item { TKNavigationBar("KONSUMEN", "c_home") {} }
    }
}

@Preview(name = "Light", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun DesignCatalogLightPreview() {
    TukangKilatTheme { DesignCatalog() }
}

@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DesignCatalogDarkPreview() {
    TukangKilatTheme { DesignCatalog() }
}
