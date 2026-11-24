package id.tukangkilat.app.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** True when the user asked the system to remove animations. */
@Composable
fun rememberReduceMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }

/** Small tactile press feedback. Transform only, no layout pass. */
fun Modifier.tkPressScale(source: MutableInteractionSource): Modifier = composed {
    val reduce = rememberReduceMotion()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "pressScale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Screen entrance: fade plus a small upward settle, 300ms spring. */
fun Modifier.tkScreenEnter(): Modifier = composed {
    val reduce = rememberReduceMotion()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible || reduce) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 1f),
        label = "screenEnter"
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 18f
    }
}
