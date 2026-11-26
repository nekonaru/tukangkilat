package id.tukangkilat.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius tokens: 8 fields, 12 media, 16 cards, 24 buttons and sheets. */
object TKShape {
    val Field = RoundedCornerShape(8.dp)
    val Media = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(24.dp)
    val Circle = CircleShape
}

val TukangKilatShapes = Shapes(
    extraSmall = TKShape.Field,
    small = TKShape.Field,
    medium = TKShape.Media,
    large = TKShape.Card,
    extraLarge = TKShape.Button
)
