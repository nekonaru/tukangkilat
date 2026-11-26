package id.tukangkilat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme

enum class ThemeMode { System, Light, Dark }

@Composable
fun TukangKilatTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkTukangKilatColors else LightTukangKilatColors
    CompositionLocalProvider(LocalTukangKilatColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TukangKilatTypography,
            shapes = TukangKilatShapes,
            content = content
        )
    }
}
