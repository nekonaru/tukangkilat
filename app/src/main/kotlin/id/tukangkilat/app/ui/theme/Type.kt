package id.tukangkilat.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.tukangkilat.app.R

/** Plus Jakarta Sans is bundled in res/font. The variable file is instanced at every used weight. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.W400),
    Font(R.font.plus_jakarta_sans, FontWeight.W500),
    Font(R.font.plus_jakarta_sans, FontWeight.W600),
    Font(R.font.plus_jakarta_sans, FontWeight.W700),
    Font(R.font.plus_jakarta_sans, FontWeight.W800)
)

private fun style(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp
)

/** Type scale from the Figma foundation: Display 36, Headline 28, Title 20/16, Body 14/12, Label 12 bold. */
val TukangKilatTypography = Typography(
    displayLarge = style(36, FontWeight.W800),
    displayMedium = style(36, FontWeight.W700),
    displaySmall = style(36, FontWeight.W600),
    headlineLarge = style(28, FontWeight.W700),
    headlineMedium = style(28, FontWeight.W600),
    headlineSmall = style(28, FontWeight.W500),
    titleLarge = style(20, FontWeight.W700),
    titleMedium = style(20, FontWeight.W600),
    titleSmall = style(16, FontWeight.W600),
    bodyLarge = style(14, FontWeight.W500),
    bodyMedium = style(14, FontWeight.W400),
    bodySmall = style(12, FontWeight.W400),
    labelLarge = style(12, FontWeight.W700),
    labelMedium = style(12, FontWeight.W600),
    labelSmall = style(12, FontWeight.W500)
)
