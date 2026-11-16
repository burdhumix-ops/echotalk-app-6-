package co.znxdnd.echotalk.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.ui.theme.ThemeManager

enum class SemanticActionRole {
    CONFIRM_BLUE,       // Accepted, approved, confirmed, selected, or successfully completed actions (#3478F6)
    DESTRUCTIVE_RED,    // Rejected, declined, deleted, destructive actions, or errors (#D92D3A / #E5484D)
    SUCCESS_GREEN,      // Successful operations, completed tasks, and positive confirmations (#239B69)
    ACCENT_PINK,        // Pink-theme accent interactions, selected controls, positive highlights (#E85D9E)
    WARNING_YELLOW,     // Warnings and caution (#E6A23C)
    NEUTRAL             // Ordinary inactive controls and default states
}

/**
 * Modifier.interactiveSemanticClick: Reusable Compose modifier implementing
 * the requested semantic interaction colors, ripple effects, subtle pressed-state
 * scale animation, and 150-200ms color transitions across all app screens.
 */
fun Modifier.interactiveSemanticClick(
    role: SemanticActionRole = SemanticActionRole.NEUTRAL,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    val semanticColors = ThemeManager.semanticColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed && enabled && !EchoAnimations.isMotionReduced()) 0.96f else 1.0f,
        animationSpec = EchoAnimations.cardSelectionSpring,
        label = "InteractiveClickScale"
    )

    val targetBg = when {
        isSelected -> semanticColors.pinkHighlightContainer
        isPressed -> when (role) {
            SemanticActionRole.CONFIRM_BLUE -> semanticColors.blueConfirm.copy(alpha = 0.15f)
            SemanticActionRole.DESTRUCTIVE_RED -> semanticColors.redDestructive.copy(alpha = 0.15f)
            SemanticActionRole.SUCCESS_GREEN -> semanticColors.greenSuccess.copy(alpha = 0.15f)
            SemanticActionRole.ACCENT_PINK -> semanticColors.pinkHighlight.copy(alpha = 0.15f)
            SemanticActionRole.WARNING_YELLOW -> semanticColors.warning.copy(alpha = 0.15f)
            SemanticActionRole.NEUTRAL -> Color.Black.copy(alpha = 0.05f)
        }
        else -> Color.Transparent
    }

    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = EchoAnimations.colorTransitionSpec,
        label = "InteractiveBgTransition"
    )

    this
        .scale(scaleAnim)
        .clip(shape)
        .background(animatedBg)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = when (role) {
                SemanticActionRole.CONFIRM_BLUE -> semanticColors.blueConfirm
                SemanticActionRole.DESTRUCTIVE_RED -> semanticColors.redDestructive
                SemanticActionRole.SUCCESS_GREEN -> semanticColors.greenSuccess
                SemanticActionRole.ACCENT_PINK -> semanticColors.pinkHighlight
                SemanticActionRole.WARNING_YELLOW -> semanticColors.warning
                SemanticActionRole.NEUTRAL -> MaterialTheme.colorScheme.onSurface
            }),
            enabled = enabled
        ) {
            val hapticMgr = co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(view.context)
            when (role) {
                SemanticActionRole.CONFIRM_BLUE, SemanticActionRole.SUCCESS_GREEN ->
                    hapticMgr.performPixelConfirm(view)
                SemanticActionRole.DESTRUCTIVE_RED ->
                    hapticMgr.performPixelHeavyClick(view)
                else ->
                    hapticMgr.performPixelClick(view)
            }
            onClick()
        }
}

/**
 * InteractiveSemanticButton: Theme-aware action button providing smooth 150-200ms color transitions,
 * subtle pressed-state scale animations, tactile haptic feedback, and semantic clarity.
 */
@Composable
fun InteractiveSemanticButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: SemanticActionRole = SemanticActionRole.NEUTRAL,
    text: String? = null,
    icon: ImageVector? = null,
    isConfirmed: Boolean = false,
    enabled: Boolean = true,
    fontSize: TextUnit = 11.sp,
    cornerRadius: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
) {
    val view = LocalView.current
    val semanticColors = ThemeManager.semanticColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed && enabled && !EchoAnimations.isMotionReduced()) 0.94f else 1.0f,
        animationSpec = EchoAnimations.cardSelectionSpring,
        label = "SemanticButtonScale"
    )

    // Resolve target container and content colors from active theme's semantic tokens
    val (targetContainer, targetContent, targetBorder) = when (role) {
        SemanticActionRole.CONFIRM_BLUE -> Triple(
            semanticColors.blueConfirm,
            semanticColors.onBlueConfirm,
            Color.Transparent
        )
        SemanticActionRole.DESTRUCTIVE_RED -> Triple(
            semanticColors.redDestructive,
            semanticColors.onRedDestructive,
            Color.Transparent
        )
        SemanticActionRole.SUCCESS_GREEN -> Triple(
            semanticColors.greenSuccess,
            semanticColors.onGreenSuccess,
            Color.Transparent
        )
        SemanticActionRole.ACCENT_PINK -> Triple(
            semanticColors.pinkHighlight,
            semanticColors.onPinkHighlight,
            Color.Transparent
        )
        SemanticActionRole.WARNING_YELLOW -> Triple(
            semanticColors.warning,
            Color.White,
            Color.Transparent
        )
        SemanticActionRole.NEUTRAL -> Triple(
            semanticColors.neutralInactiveContainer,
            semanticColors.onNeutralInactive,
            semanticColors.selectedBorder.copy(alpha = 0.2f)
        )
    }

    val animatedContainer by animateColorAsState(
        targetValue = if (enabled) targetContainer else targetContainer.copy(alpha = 0.38f),
        animationSpec = EchoAnimations.colorTransitionSpec,
        label = "ButtonContainerColor"
    )

    val animatedContent by animateColorAsState(
        targetValue = if (enabled) targetContent else targetContent.copy(alpha = 0.45f),
        animationSpec = EchoAnimations.colorTransitionSpec,
        label = "ButtonContentColor"
    )

    Surface(
        modifier = modifier
            .scale(scaleAnim)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = targetContent)
            ) {
                val hapticMgr = co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(view.context)
                when (role) {
                    SemanticActionRole.CONFIRM_BLUE, SemanticActionRole.SUCCESS_GREEN ->
                        hapticMgr.performPixelConfirm(view)
                    SemanticActionRole.DESTRUCTIVE_RED ->
                        hapticMgr.performPixelHeavyClick(view)
                    else ->
                        hapticMgr.performPixelClick(view)
                }
                onClick()
            },
        shape = RoundedCornerShape(cornerRadius),
        color = animatedContainer,
        border = if (targetBorder != Color.Transparent) BorderStroke(1.dp, targetBorder) else null
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConfirmed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirmed",
                    tint = animatedContent,
                    modifier = Modifier.size(14.dp)
                )
                if (!text.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedContent,
                    modifier = Modifier.size(14.dp)
                )
                if (!text.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            if (!text.isNullOrBlank()) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = animatedContent
                )
            }
        }
    }
}

/**
 * InteractiveFilterPill: Theme-aware selectable pill for filter categories, rooms, and settings.
 * Features smooth 150-200ms transitions, active highlight borders, and tactile responsiveness.
 */
@Composable
fun InteractiveFilterPill(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingBadge: String? = null
) {
    val view = LocalView.current
    val semanticColors = ThemeManager.semanticColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed && !EchoAnimations.isMotionReduced()) 0.95f else 1.0f,
        animationSpec = EchoAnimations.cardSelectionSpring,
        label = "FilterPillScale"
    )

    val targetBg = if (isSelected) semanticColors.pinkHighlight else semanticColors.neutralInactiveContainer
    val targetContent = if (isSelected) semanticColors.onPinkHighlight else semanticColors.onNeutralInactive
    val targetBorder = if (isSelected) semanticColors.selectedBorder else Color.Transparent

    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = EchoAnimations.colorTransitionSpec,
        label = "PillBg"
    )
    val animatedContent by animateColorAsState(
        targetValue = targetContent,
        animationSpec = EchoAnimations.colorTransitionSpec,
        label = "PillContent"
    )

    Surface(
        modifier = modifier
            .scale(scaleAnim)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = targetContent)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSelect()
            },
        shape = RoundedCornerShape(16.dp),
        color = animatedBg,
        border = if (isSelected) BorderStroke(1.dp, targetBorder) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedContent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = animatedContent
            )
            if (trailingBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedContent.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = trailingBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = animatedContent
                    )
                }
            }
        }
    }
}

/**
 * DestructiveConfirmationDialog: Dedicated confirmation dialog for destructive actions
 * (e.g., deleting a message, deleting account, clearing chat history).
 * Prominently presents a distinct red destructive action button so users never mistake
 * a destructive action for an ordinary selection.
 */
@Composable
fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val semanticColors = ThemeManager.semanticColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, semanticColors.redDestructive.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(semanticColors.redDestructiveContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = semanticColors.redDestructive,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This action cannot be undone",
                            fontSize = 10.sp,
                            color = semanticColors.redDestructive
                        )
                    }
                }

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            color = semanticColors.onNeutralInactive
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    InteractiveSemanticButton(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        role = SemanticActionRole.DESTRUCTIVE_RED,
                        text = confirmLabel,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lightweight, deduplicated confirmation controller built on Material3 SnackbarHostState.
 * Prevents repeated notifications for the same message within a short cooldown window,
 * fails safely, and supports optional Undo/Retry actions.
 */
class EchoConfirmationController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    private var lastMessage: String? = null
    private var lastShownAtMs: Long = 0L

    fun show(
        message: String,
        actionLabel: String? = null,
        cooldownMs: Long = 1400L,
        onAction: (() -> Unit)? = null
    ) {
        if (message.isBlank()) return
        val now = System.currentTimeMillis()
        if (actionLabel == null && message == lastMessage && (now - lastShownAtMs) < cooldownMs) {
            return
        }
        lastMessage = message
        lastShownAtMs = now
        scope.launch {
            try {
                hostState.currentSnackbarData?.dismiss()
                val res = hostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    onAction?.invoke()
                }
            } catch (_: Exception) {
                // Fail-safe: never block caller if snackbar host is unavailable
            }
        }
    }
}

@Composable
fun rememberEchoConfirmationController(
    hostState: SnackbarHostState = remember { SnackbarHostState() }
): EchoConfirmationController {
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) {
        EchoConfirmationController(hostState, scope)
    }
}

/**
 * Theme-compatible lightweight confirmation banner positioned safely without obscuring controls.
 */
@Composable
fun EchoConfirmationSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary
        )
    }
}

/**
 * Clear, accessible divider indicating where unread/new messages begin in a conversation or room.
 */
@Composable
fun ChatNewMessagesDivider(
    label: String = "New messages",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}

/**
 * Compact jump-to-latest-message floating button shown when the user scrolls away from bottom.
 */
@Composable
fun JumpToLatestButton(
    visible: Boolean,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (EchoAnimations.isMotionReduced()) fadeIn(tween(0)) else fadeIn(tween(150)) + scaleIn(tween(150)),
        exit = if (EchoAnimations.isMotionReduced()) fadeOut(tween(0)) else fadeOut(tween(120)) + scaleOut(tween(120)),
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Jump to latest message",
                    modifier = Modifier.size(20.dp)
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount new",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

