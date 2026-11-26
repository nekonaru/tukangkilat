package id.tukangkilat.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Primitive palette from the Figma foundation. Semantic aliases live in [LightColors] and [DarkColors]. */
object TKPalette {
    val Primary = Color(0xFFFFC928)
    val OnPrimary = Color(0xFF14243B)
    val PrimaryContainer = Color(0xFFFFF0B3)
    val OnPrimaryContainer = Color(0xFF473800)
    val Secondary = Color(0xFF193653)
    val OnSecondary = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF8FAFC)
    val SurfaceContainer = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFEAF0F5)
    val OnSurface = Color(0xFF14243B)
    val OnSurfaceVariant = Color(0xFF526174)
    val Outline = Color(0xFF78889A)
    val OutlineVariant = Color(0xFFDAE2EB)
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFCE9E7)
    val Success = Color(0xFF126344)
    val SuccessContainer = Color(0xFFE0F4E9)
    val Warning = Color(0xFF785600)
    val WarningContainer = Color(0xFFFFF2CC)

    val DarkPrimary = Color(0xFFFFC928)
    val DarkOnPrimary = Color(0xFF14243B)
    val DarkPrimaryContainer = Color(0xFF554400)
    val DarkOnPrimaryContainer = Color(0xFFFFF0B3)
    val DarkSecondary = Color(0xFFA8CAE8)
    val DarkOnSecondary = Color(0xFF10304A)
    val DarkSurface = Color(0xFF101C2C)
    val DarkSurfaceContainer = Color(0xFF1A293C)
    val DarkSurfaceVariant = Color(0xFF2B3B4E)
    val DarkOnSurface = Color(0xFFF0F5FA)
    val DarkOnSurfaceVariant = Color(0xFFC1CEDB)
    val DarkOutline = Color(0xFF91A1B4)
    val DarkOutlineVariant = Color(0xFF3F5064)
    val DarkError = Color(0xFFFFB4AB)
    val DarkErrorContainer = Color(0xFF601410)
    val DarkSuccess = Color(0xFF7DDBAC)
    val DarkSuccessContainer = Color(0xFF123B2C)
    val DarkWarning = Color(0xFFF4CB64)
    val DarkWarningContainer = Color(0xFF493600)
}

/** Material 3 has no success or warning roles, so the app supplies them through a theme extension. */
data class TukangKilatColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color
)

val LightTukangKilatColors = TukangKilatColors(
    success = TKPalette.Success,
    successContainer = TKPalette.SuccessContainer,
    warning = TKPalette.Warning,
    warningContainer = TKPalette.WarningContainer
)

val DarkTukangKilatColors = TukangKilatColors(
    success = TKPalette.DarkSuccess,
    successContainer = TKPalette.DarkSuccessContainer,
    warning = TKPalette.DarkWarning,
    warningContainer = TKPalette.DarkWarningContainer
)

val LocalTukangKilatColors = staticCompositionLocalOf { LightTukangKilatColors }

@Composable
fun tukangKilatColors(): TukangKilatColors = LocalTukangKilatColors.current

val LightColors: ColorScheme = lightColorScheme().copy(
    primary = TKPalette.Primary,
    onPrimary = TKPalette.OnPrimary,
    primaryContainer = TKPalette.PrimaryContainer,
    onPrimaryContainer = TKPalette.OnPrimaryContainer,
    secondary = TKPalette.Secondary,
    onSecondary = TKPalette.OnSecondary,
    background = TKPalette.Surface,
    onBackground = TKPalette.OnSurface,
    surface = TKPalette.Surface,
    onSurface = TKPalette.OnSurface,
    surfaceVariant = TKPalette.SurfaceVariant,
    onSurfaceVariant = TKPalette.OnSurfaceVariant,
    surfaceContainer = TKPalette.SurfaceContainer,
    outline = TKPalette.Outline,
    outlineVariant = TKPalette.OutlineVariant,
    error = TKPalette.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = TKPalette.ErrorContainer,
    onErrorContainer = TKPalette.Error,
    secondaryContainer = TKPalette.SurfaceVariant,
    onSecondaryContainer = TKPalette.OnSurface,
    tertiary = TKPalette.Primary,
    onTertiary = TKPalette.OnPrimary,
    tertiaryContainer = TKPalette.PrimaryContainer,
    onTertiaryContainer = TKPalette.OnPrimaryContainer,
    surfaceContainerLow = TKPalette.Surface,
    surfaceContainerHigh = TKPalette.SurfaceContainer,
    surfaceContainerHighest = TKPalette.SurfaceVariant,
    surfaceDim = TKPalette.SurfaceVariant,
    surfaceBright = TKPalette.Surface,
    inverseSurface = TKPalette.Secondary,
    inverseOnSurface = TKPalette.OnSecondary
)

val DarkColors: ColorScheme = darkColorScheme().copy(
    primary = TKPalette.DarkPrimary,
    onPrimary = TKPalette.DarkOnPrimary,
    primaryContainer = TKPalette.DarkPrimaryContainer,
    onPrimaryContainer = TKPalette.DarkOnPrimaryContainer,
    secondary = TKPalette.DarkSecondary,
    onSecondary = TKPalette.DarkOnSecondary,
    background = TKPalette.DarkSurface,
    onBackground = TKPalette.DarkOnSurface,
    surface = TKPalette.DarkSurface,
    onSurface = TKPalette.DarkOnSurface,
    surfaceVariant = TKPalette.DarkSurfaceVariant,
    onSurfaceVariant = TKPalette.DarkOnSurfaceVariant,
    surfaceContainer = TKPalette.DarkSurfaceContainer,
    outline = TKPalette.DarkOutline,
    outlineVariant = TKPalette.DarkOutlineVariant,
    error = TKPalette.DarkError,
    onError = Color(0xFF601410),
    errorContainer = TKPalette.DarkErrorContainer,
    onErrorContainer = TKPalette.DarkError,
    secondaryContainer = TKPalette.DarkSurfaceVariant,
    onSecondaryContainer = TKPalette.DarkOnSurface,
    tertiary = TKPalette.DarkPrimary,
    onTertiary = TKPalette.DarkOnPrimary,
    tertiaryContainer = TKPalette.DarkPrimaryContainer,
    onTertiaryContainer = TKPalette.DarkOnPrimaryContainer,
    surfaceContainerLow = TKPalette.DarkSurface,
    surfaceContainerHigh = TKPalette.DarkSurfaceContainer,
    surfaceContainerHighest = TKPalette.DarkSurfaceVariant,
    surfaceDim = TKPalette.DarkSurfaceVariant,
    surfaceBright = TKPalette.DarkSurface,
    inverseSurface = TKPalette.DarkSecondary,
    inverseOnSurface = TKPalette.DarkOnSecondary
)

/**
 * Compatibility facade. Existing screens keep their old names but now resolve to semantic
 * theme colors, so light and dark themes work without touching business logic.
 */
object TKColor {
    val Ink: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val InkDeep: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val InkSoft: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
    val InkLine: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val Orange: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val OrangeSoft: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    val Mist: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val White: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val Card: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
    val Cream: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    val CreamText: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    val Green: Color @Composable get() = tukangKilatColors().success
    val GreenSoft: Color @Composable get() = tukangKilatColors().successContainer
    val GreenDeep: Color @Composable get() = tukangKilatColors().success
    val Red: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error
    val RedSoft: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
    val RedDeep: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error
    val InkCard: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
}
