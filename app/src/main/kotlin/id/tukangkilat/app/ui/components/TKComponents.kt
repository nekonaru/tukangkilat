package id.tukangkilat.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tukangkilat.app.R
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

/** Legacy names delegate to the design system so every screen inherits the new theme. */
@Composable
fun TKPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TKButton(text, onClick, modifier, TKButtonVariant.Filled, enabled)
}

@Composable
fun TKSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TKButton(text, onClick, modifier, TKButtonVariant.Tonal, enabled)
}

@Composable
fun TKTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TKButton(text, onClick, modifier, TKButtonVariant.Text)
}

@Composable
fun TKCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    cornerRadius: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = when (cornerRadius) {
        8 -> TKShape.Field
        12 -> TKShape.Media
        24 -> TKShape.Button
        else -> TKShape.Card
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(TKSpacing.Sm),
        content = content
    )
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyView(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {
        Icon(Icons.Filled.Inbox, contentDescription = "Belum ada data", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
    }
) {
    Column(
        modifier = modifier.fillMaxSize().padding(TKSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(TKSpacing.Sm))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(TKSpacing.Xs))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorView(modifier: Modifier = Modifier, message: String = "Terjadi kesalahan", onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(TKSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = "Gagal memuat", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(TKSpacing.Sm))
        Text("Ada kendala sebentar", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(TKSpacing.Xs))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(TKSpacing.Sm))
            TKButton("Coba lagi", onRetry, modifier = Modifier.width(200.dp), variant = TKButtonVariant.Tonal)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = TKSpacing.Xs)
    )
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = TKSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(TKSpacing.MinTouch)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
    }
}

@Composable
fun ModeTukangChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(TKShape.Button)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = TKSpacing.Xs, vertical = 3.dp)
    ) {
        Text("MODE TUKANG", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun AvatarCircle(name: String, size: Int = 44, photoUrl: String? = null) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (size / 2.5).sp)
    }
}

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, starSize: Int = 18) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = rating >= i - 0.25
            Icon(
                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Rating $i dari 5",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(starSize.dp)
            )
        }
        Spacer(Modifier.width(TKSpacing.Xs))
        Text("%.1f".format(rating), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun TextFieldDark(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    TKOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label.orEmpty(),
        modifier = modifier,
        placeholder = placeholder,
        error = isError,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else minLines.coerceAtLeast(2)
    )
}

/** Two-tone brand wordmark: "Tukang" in the given base color, "Kilat" in amber. */
@Composable
fun TKWordmark(
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.ExtraBold)) { append("Tukang") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)) { append("Kilat") }
        },
        modifier = modifier,
        fontSize = fontSize,
        style = MaterialTheme.typography.titleMedium
    )
}
