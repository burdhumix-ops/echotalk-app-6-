package co.znxdnd.echotalk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AdaptiveContainer: Automatically applies responsive centering, max width bounding,
 * keyboard insets (imePadding), and window insets for phones, foldables, and tablets.
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * ResponsiveContentPadding: helper providing uniform adaptive padding based on window size class.
 */
@Composable
fun responsivePadding(windowConfig: WindowConfiguration = rememberWindowConfiguration()): PaddingValues {
    return PaddingValues(
        horizontal = windowConfig.contentPadding,
        vertical = if (windowConfig.sizeClass == WindowSizeClass.COMPACT) 12.dp else 20.dp
    )
}
