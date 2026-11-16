package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import kotlinx.coroutines.launch

/**
 * Bottom padding clearance provided to scrollable screens so their last items/controls
 * remain comfortably reachable above the overlapping frosted bottom navigation pill.
 */
val LocalFloatingBottomBarClearance = compositionLocalOf { 0.dp }

/**
 * NavTab: Core navigation destinations for EchoTalk.
 * The bar displays exactly 3 prominent main tabs in the visible window:
 * Profile, Chats, and Discover.
 * The remaining tabs (Rooms, Arcade, EchoBot AI) appear smoothly after horizontal scroll.
 */
enum class NavTab(val id: String, val label: String, val icon: ImageVector) {
    PROFILE("profile", "Profile", Icons.Default.Person),
    DMS("dms", "Chats", Icons.Default.Forum),
    DISCOVER("discover", "Discover", Icons.Default.Explore),
    ROOMS("rooms", "Rooms", Icons.Default.Radio),
    ARCADE("arcade", "Arcade", Icons.Default.SportsEsports),
    AI("ai", "EchoBot", Icons.Default.AutoAwesome);

    companion object {
        val mainTabs = listOf(PROFILE, DMS, DISCOVER)
        val scrollableTabs = listOf(ROOMS, ARCADE, AI)
        val allTabs = listOf(PROFILE, DMS, DISCOVER, ROOMS, ARCADE, AI)
        val primaryTabs = allTabs
        val CHATS = DMS
    }
}

/**
 * NavTabItem: Renders a single navigation tab with generous touch targets,
 * live user avatar, active pill background highlight, and zero icon or label cropping.
 */
@Composable
private fun NavTabItem(
    tab: NavTab,
    tabWidth: Dp,
    isSelected: Boolean,
    currentUser: User?,
    dmBadgeCount: Int,
    activePillBg: Color,
    activeColor: Color,
    inactiveIconColor: Color,
    inactiveTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedPillColor by animateColorAsState(
        targetValue = if (isSelected) activePillBg else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "activePillColor_${tab.id}"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveIconColor,
        animationSpec = tween(durationMillis = 200),
        label = "contentColor_${tab.id}"
    )

    Box(
        modifier = modifier
            .width(tabWidth)
            .fillMaxHeight()
            .springButton(pressedScale = 0.90f, onClick = onClick)
            .testTag("nav_tab_${tab.id}"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(animatedPillColor, RoundedCornerShape(22.dp))
                .padding(horizontal = 13.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (tab == NavTab.PROFILE) {
                        val avatarUrl = currentUser?.avatarUrl ?: ""
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = tab.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(25.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) activeColor else Color.White.copy(alpha = 0.45f),
                                        shape = CircleShape
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) activeColor else Color(0xFF475569))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) activeColor else Color.White.copy(alpha = 0.45f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (currentUser?.name?.take(1) ?: "U").uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = animatedContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (tab == NavTab.DMS && dmBadgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF229ED9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (dmBadgeCount > 99) "99+" else "$dmBadgeCount",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) activeColor else inactiveTextColor,
                    letterSpacing = 0.15.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Frosted Glass Translucent Floating Pill Bottom Navigation Bar:
 * Floats cleanly above scrolling content so lists move seamlessly underneath.
 * Displays 3 main tabs (Profile, Chats, Discover) with horizontal scroll for Rooms, Arcade, EchoBot.
 */
@Composable
fun BottomNavBar(
    activeTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    dmBadgeCount: Int = 0,
    currentUser: User?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Smooth translucent frosted glass surface that lets scrolling items show softly underneath
    val frostSurfaceBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
            )
        )
    }

    val specularBorder = BorderStroke(
        width = 0.8.dp,
        brush = Brush.verticalGradient(
            listOf(
                if (isDarkTheme) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                if (isDarkTheme) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            )
        )
    )

    val activePillBg = if (isDarkTheme) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
    val inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val isSliding = scrollState.isScrollInProgress || scrollState.value > 10
        val compactBarWidth = 250.dp
        val expandedBarWidth = minOf(maxWidth, 368.dp)

        val animatedBarWidth by animateDpAsState(
            targetValue = if (isSliding) expandedBarWidth else compactBarWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "navBarDynamicWidth"
        )

        val tabWidth = 78.dp
        val barInnerPadding = 8.dp

        LaunchedEffect(activeTab) {
            val index = NavTab.allTabs.indexOf(activeTab)
            if (index >= 0) {
                coroutineScope.launch {
                    val tabWidthPx = with(density) { tabWidth.toPx() }
                    val targetScrollPx = if (index < 3) {
                        0
                    } else {
                        ((index - 2) * tabWidthPx).toInt()
                    }
                    scrollState.animateScrollTo(targetScrollPx.coerceAtLeast(0))
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            border = specularBorder,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .width(animatedBarWidth)
                .height(58.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(frostSurfaceBrush)
                .testTag("floating_bottom_nav_bar")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Subtle top frost sheen line for realistic glass edge highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.30f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = barInnerPadding, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavTab.allTabs.forEach { tab ->
                        NavTabItem(
                            tab = tab,
                            tabWidth = tabWidth,
                            isSelected = activeTab == tab,
                            currentUser = currentUser,
                            dmBadgeCount = dmBadgeCount,
                            activePillBg = activePillBg,
                            activeColor = activeColor,
                            inactiveIconColor = inactiveIconColor,
                            inactiveTextColor = inactiveTextColor,
                            onClick = {
                                if (activeTab != tab) {
                                    EchoHapticManager.getInstance(context).performPixelClick(view)
                                    PoliteSoundSynthesizer.playPopSound()
                                    onTabSelected(tab)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
