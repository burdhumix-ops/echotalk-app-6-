package co.znxdnd.echotalk.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WindowSizeClass categories according to Material Design 3 adaptive guidelines.
 */
enum class WindowSizeClass {
    COMPACT,   // Phones in portrait (< 600dp width)
    MEDIUM,    // Foldables, small tablets, phones in landscape (600dp .. 840dp)
    EXPANDED   // Tablets, desktop displays (> 840dp)
}

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * WindowConfiguration encapsulates adaptive screen metrics and provides
 * responsive dimensions for padding, max content widths, grid columns, and dialogs.
 */
data class WindowConfiguration(
    val sizeClass: WindowSizeClass,
    val orientation: DeviceOrientation,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val contentPadding: Dp,
    val cardCornerRadius: Dp,
    val maxContentWidth: Dp,
    val gridColumns: Int,
    val chatBubbleMaxWidthFraction: Float,
    val isTabletOrExpanded: Boolean
)

@Composable
fun rememberWindowConfiguration(): WindowConfiguration {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp

    val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        DeviceOrientation.LANDSCAPE
    } else {
        DeviceOrientation.PORTRAIT
    }

    val sizeClass = when {
        widthDp < 600.dp -> WindowSizeClass.COMPACT
        widthDp < 840.dp -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }

    return remember(widthDp, heightDp, orientation, sizeClass) {
        val isExpanded = sizeClass == WindowSizeClass.EXPANDED || (sizeClass == WindowSizeClass.MEDIUM && orientation == DeviceOrientation.LANDSCAPE)
        val padding = when (sizeClass) {
            WindowSizeClass.COMPACT -> 16.dp
            WindowSizeClass.MEDIUM -> 24.dp
            WindowSizeClass.EXPANDED -> 32.dp
        }
        val maxContentWidth = when (sizeClass) {
            WindowSizeClass.COMPACT -> Dp.Unspecified
            WindowSizeClass.MEDIUM -> 680.dp
            WindowSizeClass.EXPANDED -> 840.dp
        }
        val columns = when (sizeClass) {
            WindowSizeClass.COMPACT -> 1
            WindowSizeClass.MEDIUM -> 2
            WindowSizeClass.EXPANDED -> if (orientation == DeviceOrientation.LANDSCAPE) 4 else 3
        }
        val bubbleFraction = when (sizeClass) {
            WindowSizeClass.COMPACT -> 0.82f
            WindowSizeClass.MEDIUM -> 0.70f
            WindowSizeClass.EXPANDED -> 0.55f
        }

        WindowConfiguration(
            sizeClass = sizeClass,
            orientation = orientation,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            contentPadding = padding,
            cardCornerRadius = if (sizeClass == WindowSizeClass.COMPACT) 16.dp else 24.dp,
            maxContentWidth = maxContentWidth,
            gridColumns = columns,
            chatBubbleMaxWidthFraction = bubbleFraction,
            isTabletOrExpanded = isExpanded
        )
    }
}
