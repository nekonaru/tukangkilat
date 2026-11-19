package id.tukangkilat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = TKColor.Orange,
    onPrimary = TKColor.White,
    secondary = TKColor.Mist,
    onSecondary = TKColor.Ink,
    tertiary = TKColor.Orange,
    background = TKColor.Ink,
    onBackground = TKColor.White,
    surface = TKColor.InkSoft,
    onSurface = TKColor.White,
    surfaceVariant = TKColor.InkCard,
    onSurfaceVariant = TKColor.Mist,
    outline = TKColor.InkLine,
    error = TKColor.Red,
    onError = TKColor.White
)

@Composable
fun TukangKilatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
