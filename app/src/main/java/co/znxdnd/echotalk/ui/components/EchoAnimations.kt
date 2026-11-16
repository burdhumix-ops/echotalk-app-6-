package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import co.znxdnd.echotalk.data.personalization.AnimationLevel

/**
 * EchoAnimations: Centralized spring physics, easing curves, and tactile motion tokens
 * for EchoTalks, adhering to specifications and respecting Reduce Animations and AnimationLevel.
 */
object EchoAnimations {

    // Global toggle for accessibility (Reduce Motion) or Lite Performance Profile
    var reduceMotionGlobal: Boolean = false

    // Controlled personalization animation level (Off / Subtle / Normal)
    var animationLevelGlobal: AnimationLevel = AnimationLevel.NORMAL

    fun isMotionReduced(): Boolean = reduceMotionGlobal || animationLevelGlobal == AnimationLevel.OFF

    fun durationScale(): Float = when {
        reduceMotionGlobal -> 0f
        else -> animationLevelGlobal.durationScale
    }

    fun scaledDuration(baseMs: Int): Int {
        val scale = durationScale()
        return if (scale <= 0f) 0 else (baseMs * scale).toInt().coerceAtLeast(16)
    }

    // Button Press Physics: Scale 1.0f -> 0.97f (100–150ms)
    val buttonPressSpec: AnimationSpec<Float>
        get() = tween(
            durationMillis = scaledDuration(120),
            easing = FastOutSlowInEasing
        )

    // Card Selection Physics: Spring stiffness 400f, damping ratio 0.8f
    val cardSelectionSpring: AnimationSpec<Float>
        get() = if (isMotionReduced()) {
            tween(durationMillis = 0)
        } else if (animationLevelGlobal == AnimationLevel.SUBTLE) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 0.8f, stiffness = 400f)
        }

    // Dialog Physics: Spring stiffness 350f, damping ratio 0.85f
    val dialogSpring: AnimationSpec<Float>
        get() = if (isMotionReduced()) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.85f, stiffness = 350f)
        }

    // Smooth Content Expand / Collapse
    val contentExpandSpec: AnimationSpec<Float>
        get() = if (isMotionReduced()) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        }

    // Subtle Selection Highlight Transition (180ms)
    val colorTransitionSpec: AnimationSpec<androidx.compose.ui.graphics.Color>
        get() = tween(
            durationMillis = scaledDuration(180),
            easing = LinearOutSlowInEasing
        )
}

/**
 * Modifier extension: Adds subtle tactile scale bounce on press (1.0 -> 0.97).
 * Automatically bypasses scaling if reduceMotion or AnimationLevel.OFF is active.
 */
fun Modifier.tactilePress(
    targetScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveTarget = if (isPressed && !EchoAnimations.isMotionReduced()) {
        if (EchoAnimations.animationLevelGlobal == AnimationLevel.SUBTLE) 0.985f else targetScale
    } else 1f

    val scale by animateFloatAsState(
        targetValue = effectiveTarget,
        animationSpec = EchoAnimations.buttonPressSpec,
        label = "tactile_press_scale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Modifier extension: Adds spring physics translation on selection or hover.
 */
fun Modifier.springCardElevation(
    isSelected: Boolean
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isSelected && !EchoAnimations.isMotionReduced()) 1.02f else 1.0f,
        animationSpec = EchoAnimations.cardSelectionSpring,
        label = "card_selection_spring"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
