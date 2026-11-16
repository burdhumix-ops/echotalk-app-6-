package co.znxdnd.echotalk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import co.znxdnd.echotalk.data.personalization.CustomTheme
import co.znxdnd.echotalk.data.personalization.PersonalizationManager
import co.znxdnd.echotalk.data.repository.AppTheme
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Reusable semantic colour tokens as required by Section 3:
 * "primary", "onPrimary", "background", "surface", "surfaceVariant", "onSurface",
 * "outline", "selected", "success", "error", "warning", "info".
 */
@Stable
data class SemanticColorTokens(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val outline: Color,
    val selected: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    val info: Color
)

/**
 * ThemeCustomColors: Extended dynamic colors injected into Compose UI components
 * beyond standard Material 3 colorScheme (e.g. ambient glows, gradient brushes, unread dots).
 */
@Stable
data class ThemeCustomColors(
    val theme: AppTheme,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val gradientBrush: Brush,
    val bgGradient: Brush? = null,
    val cardBackground: Color,
    val surfaceTranslucent: Color,
    val ambientGlowColor: Color,
    val unreadBadgeColor: Color,
    val borderStroke: Color,
    val isDark: Boolean
)

/**
 * SemanticInteractionColors: Centralized theme-aware semantic color tokens (Section 5).
 * Communicates meaning consistently across all app themes:
 * - Accepted / Confirmed: Blue (#3478F6 Light / #75A7FF Dark)
 * - Rejected / Declined: Red (#E5484D Light / #FF777D Dark)
 * - Deleted / Destructive: Deep Red (#D92D3A Light / #FF626C Dark)
 * - Success: Green (#239B69 Light / #55D6A0 Dark)
 * - Warning: Yellow (#E6A23C Light / #F2C16D Dark)
 * - Info: Blue (#3478F6 Light / #75A7FF Dark)
 * - Selected: Pink / Accent (#E85D9E)
 * - Inactive: Gray / Neutral (#B8A5AF Light / #A8B0C2 Dark)
 */
@Stable
data class SemanticInteractionColors(
    val blueConfirm: Color,
    val blueConfirmContainer: Color,
    val onBlueConfirm: Color,

    val redDestructive: Color,
    val redDestructiveContainer: Color,
    val onRedDestructive: Color,

    val redRejected: Color,

    val greenSuccess: Color,
    val greenSuccessContainer: Color,
    val onGreenSuccess: Color,

    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,

    val info: Color,

    val pinkHighlight: Color,
    val pinkHighlightContainer: Color,
    val onPinkHighlight: Color,

    val neutralInactive: Color,
    val neutralInactiveContainer: Color,
    val onNeutralInactive: Color,

    val selectedBorder: Color,
    val selectedGlow: Color
)

val LocalThemeManager = staticCompositionLocalOf<ThemeManager> {
    ThemeManager
}

val LocalThemeCustomColors = staticCompositionLocalOf {
    ThemeManager.getCustomColors(AppTheme.IOS_GLASS)
}

val LocalSemanticInteractionColors = staticCompositionLocalOf {
    ThemeManager.getSemanticColors(AppTheme.IOS_GLASS)
}

val LocalSemanticColorTokens = staticCompositionLocalOf {
    ThemeManager.getSemanticTokens(AppTheme.IOS_GLASS)
}

/**
 * ThemeManager: Centralized manager handling application color presets,
 * providing full theme-aware support for all themes including Classic, iOS Glass,
 * Warm Sand, Midnight Emerald, Obsidian, Velvet Rose Gold, Midnight Slate,
 * and the redesigned Pink Blush & Rose theme, alongside semantic interaction colors.
 */
object ThemeManager {

    private val repository by lazy { EchoTalkRepository.getInstance() }

    val currentTheme: StateFlow<AppTheme>
        get() = repository.theme

    /**
     * Updates the application theme and triggers dynamic recomposition.
     */
    fun setTheme(theme: AppTheme) {
        repository.setTheme(theme)
    }

    /**
     * Resolves the full M3 ColorScheme for any theme preset.
     */
    fun getColorScheme(theme: AppTheme = currentTheme.value): ColorScheme {
        return getColorSchemeForTheme(theme)
    }

    /**
     * Returns whether the given theme is a dark theme.
     * Note: PINK_BLUSH, CLASSIC, WARM_SAND, and AURORA_BLOOM are bright/light themes.
     */
    fun isDark(theme: AppTheme = currentTheme.value): Boolean {
        return theme != AppTheme.CLASSIC && theme != AppTheme.WARM_SAND && theme != AppTheme.PINK_BLUSH && theme != AppTheme.AURORA_BLOOM
    }

    /**
     * Centralized semantic colour tokens as required by Section 3.
     */
    fun getSemanticTokens(theme: AppTheme = currentTheme.value): SemanticColorTokens {
        val cs = getColorScheme(theme)
        val semantic = getSemanticColors(theme)
        return SemanticColorTokens(
            primary = cs.primary,
            onPrimary = cs.onPrimary,
            background = cs.background,
            surface = cs.surface,
            surfaceVariant = cs.surfaceVariant,
            onSurface = cs.onSurface,
            outline = cs.outline,
            selected = semantic.pinkHighlight,
            success = semantic.greenSuccess,
            error = semantic.redDestructive,
            warning = semantic.warning,
            info = semantic.info
        )
    }

    /**
     * Returns theme-aware semantic interaction colors that adapt automatically to the active theme (Section 5).
     */
    fun getSemanticColors(theme: AppTheme = currentTheme.value): SemanticInteractionColors {
        val isDarkTheme = isDark(theme)

        return if (!isDarkTheme) {
            // Light / Pink Theme Semantic Palette (Section 5)
            SemanticInteractionColors(
                blueConfirm = Color(0xFF3478F6),            // Accepted / Confirmed
                blueConfirmContainer = Color(0xFFEFF6FF),
                onBlueConfirm = Color.White,

                redDestructive = Color(0xFFD92D3A),         // Deleted / Destructive
                redDestructiveContainer = Color(0xFFFFE4E6),
                onRedDestructive = Color.White,

                redRejected = Color(0xFFE5484D),            // Rejected / Declined

                greenSuccess = Color(0xFF239B69),           // Success
                greenSuccessContainer = Color(0xFFECFDF5),
                onGreenSuccess = Color.White,

                warning = Color(0xFFE6A23C),                // Warning
                warningContainer = Color(0xFFFFFBEB),
                onWarning = Color(0xFF78350F),

                info = Color(0xFF3478F6),                   // Information

                pinkHighlight = when (theme) {
                    AppTheme.AURORA_BLOOM -> AuroraLightPrimary
                    AppTheme.PINK_BLUSH -> Color(0xFFE85D9E)
                    else -> Color(0xFF4568E8)
                },
                pinkHighlightContainer = when (theme) {
                    AppTheme.AURORA_BLOOM -> AuroraLightSelectedSurface
                    AppTheme.PINK_BLUSH -> Color(0xFFFCE0EC)
                    else -> Color(0xFFE9EDFF)
                },
                onPinkHighlight = Color.White,

                neutralInactive = Color(0xFFB8A5AF),        // Inactive
                neutralInactiveContainer = Color(0xFFF3F4F6),
                onNeutralInactive = Color(0xFF382331),

                selectedBorder = when (theme) {
                    AppTheme.AURORA_BLOOM -> AuroraLightBorder
                    AppTheme.PINK_BLUSH -> Color(0xFFF5C5D9)
                    else -> Color(0xFFE2E6F0)
                },
                selectedGlow = if (theme == AppTheme.AURORA_BLOOM) Color(0x337867D8) else Color(0x33E85D9E)
            )
        } else {
            // Dark Theme Semantic Palette (Section 5)
            SemanticInteractionColors(
                blueConfirm = Color(0xFF75A7FF),            // Accepted / Confirmed (Dark)
                blueConfirmContainer = Color(0xFF1E3A8A),
                onBlueConfirm = Color.White,

                redDestructive = Color(0xFFFF626C),         // Deleted / Destructive (Dark)
                redDestructiveContainer = Color(0xFF4C0519),
                onRedDestructive = Color.White,

                redRejected = Color(0xFFFF777D),            // Rejected / Declined (Dark)

                greenSuccess = Color(0xFF55D6A0),           // Success (Dark)
                greenSuccessContainer = Color(0xFF064E3B),
                onGreenSuccess = Color.Black,

                warning = Color(0xFFF2C16D),                // Warning (Dark)
                warningContainer = Color(0xFF451A03),
                onWarning = Color.Black,

                info = Color(0xFF75A7FF),                   // Information (Dark)

                pinkHighlight = Color(0xFFE85D9E),
                pinkHighlightContainer = Color(0x33E85D9E),
                onPinkHighlight = Color.White,

                neutralInactive = Color(0xFFA8B0C2),
                neutralInactiveContainer = Color(0xFF1E293B),
                onNeutralInactive = Color(0xFFF2F4FA),

                selectedBorder = Color(0x66E85D9E),
                selectedGlow = Color(0x40E85D9E)
            )
        }
    }

    /**
     * Returns rich extended theme colors for dynamic injection into components.
     */
    fun getCustomColors(theme: AppTheme = currentTheme.value): ThemeCustomColors {
        return when (theme) {
            AppTheme.PINK_BLUSH -> ThemeCustomColors(
                theme = theme,
                primaryAccent = PinkBlushPrimary, // #E85D9E
                secondaryAccent = PinkBlushDeepRose, // #C83E7D
                gradientBrush = PinkBlushMainGradient, // #E85D9E -> #C83E7D
                bgGradient = PinkBlushSoftBgGradient, // #FFF4F8 -> #FFEAF2
                cardBackground = PinkBlushSurface, // #FFFFFF
                surfaceTranslucent = PinkBlushSurface.copy(alpha = 0.95f),
                ambientGlowColor = Color(0x33E85D9E),
                unreadBadgeColor = PinkBlushPrimary,
                borderStroke = PinkBlushBorder, // #F5C5D9
                isDark = false
            )
            AppTheme.VELVET_ROSE_GOLD -> ThemeCustomColors(
                theme = theme,
                primaryAccent = VelvetRoseGoldPrimary,
                secondaryAccent = VelvetRoseGoldSecondary,
                gradientBrush = Brush.horizontalGradient(
                    listOf(VelvetRoseGoldPrimary, VelvetRoseGoldSecondary, VelvetGoldAccent)
                ),
                cardBackground = VelvetCharcoalCard,
                surfaceTranslucent = VelvetCharcoalSurface.copy(alpha = 0.85f),
                ambientGlowColor = Color(0x33E8A5C8),
                unreadBadgeColor = VelvetRoseGoldPrimary,
                borderStroke = VelvetBorder,
                isDark = true
            )
            AppTheme.MIDNIGHT_SLATE -> ThemeCustomColors(
                theme = theme,
                primaryAccent = MidnightSlatePrimary,
                secondaryAccent = MidnightSlateSecondary,
                gradientBrush = Brush.horizontalGradient(
                    listOf(MidnightSlatePrimary, MidnightSlateSecondary)
                ),
                cardBackground = MidnightSlateCard,
                surfaceTranslucent = MidnightSlateSurface.copy(alpha = 0.85f),
                ambientGlowColor = Color(0x3338BDF8),
                unreadBadgeColor = MidnightSlatePrimary,
                borderStroke = MidnightSlateBorder,
                isDark = true
            )
            AppTheme.IOS_GLASS -> ThemeCustomColors(
                theme = theme,
                primaryAccent = IosGlassAccent,
                secondaryAccent = Color(0xFF38BDF8),
                gradientBrush = Brush.horizontalGradient(
                    listOf(Color(0xFF6366F1), Color(0xFF38BDF8))
                ),
                cardBackground = IosGlassCard,
                surfaceTranslucent = IosGlassSurface.copy(alpha = 0.85f),
                ambientGlowColor = Color(0x266366F1),
                unreadBadgeColor = Color(0xFFE8A5C8),
                borderStroke = IosGlassBorder,
                isDark = true
            )
            AppTheme.CLASSIC -> ThemeCustomColors(
                theme = theme,
                primaryAccent = ClassicPrimary,
                secondaryAccent = ClassicSecondary,
                gradientBrush = Brush.horizontalGradient(
                    listOf(ClassicPrimary, Color(0xFF60A5FA))
                ),
                cardBackground = ClassicCard,
                surfaceTranslucent = ClassicSurface.copy(alpha = 0.95f),
                ambientGlowColor = Color(0x1A4568E8),
                unreadBadgeColor = ClassicPrimary,
                borderStroke = ClassicBorder,
                isDark = false
            )
            AppTheme.WARM_SAND -> ThemeCustomColors(
                theme = theme,
                primaryAccent = WarmSandAccent,
                secondaryAccent = Color(0xFFD97706),
                gradientBrush = Brush.horizontalGradient(
                    listOf(WarmSandAccent, Color(0xFFF59E0B))
                ),
                cardBackground = WarmSandCard,
                surfaceTranslucent = WarmSandSurface.copy(alpha = 0.95f),
                ambientGlowColor = Color(0x26C86448),
                unreadBadgeColor = WarmSandAccent,
                borderStroke = WarmSandBorder,
                isDark = false
            )
            AppTheme.MIDNIGHT_EMERALD -> ThemeCustomColors(
                theme = theme,
                primaryAccent = EmeraldAccent,
                secondaryAccent = Color(0xFF34D399),
                gradientBrush = Brush.horizontalGradient(
                    listOf(EmeraldAccent, Color(0xFF059669))
                ),
                cardBackground = EmeraldCard,
                surfaceTranslucent = EmeraldSurface.copy(alpha = 0.85f),
                ambientGlowColor = Color(0x3310B981),
                unreadBadgeColor = EmeraldAccent,
                borderStroke = EmeraldBorder,
                isDark = true
            )
            AppTheme.OBSIDIAN -> ThemeCustomColors(
                theme = theme,
                primaryAccent = ObsidianAccent,
                secondaryAccent = Color(0xFFA78BFA),
                gradientBrush = Brush.horizontalGradient(
                    listOf(ObsidianAccent, Color(0xFFC084FC))
                ),
                cardBackground = ObsidianCard,
                surfaceTranslucent = ObsidianSurface.copy(alpha = 0.85f),
                ambientGlowColor = Color(0x338B5CF6),
                unreadBadgeColor = ObsidianAccent,
                borderStroke = ObsidianBorder,
                isDark = true
            )
            AppTheme.AURORA_BLOOM -> ThemeCustomColors(
                theme = theme,
                primaryAccent = AuroraLightPrimary,
                secondaryAccent = AuroraLightAquaAccent,
                gradientBrush = AuroraMainGradient,
                bgGradient = AuroraSoftBgGradient,
                cardBackground = AuroraLightMainSurface,
                surfaceTranslucent = AuroraLightMainSurface.copy(alpha = 0.95f),
                ambientGlowColor = Color(0x337867D8),
                unreadBadgeColor = AuroraLightPrimary,
                borderStroke = AuroraLightBorder,
                isDark = false
            )
        }
    }

    /**
     * Accessor for currently injected theme custom colors in any Composable.
     */
    val colors: ThemeCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeCustomColors.current

    /**
     * Accessor for currently injected semantic interaction colors in any Composable.
     */
    val semanticColors: SemanticInteractionColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticInteractionColors.current

    /**
     * Accessor for currently injected semantic color tokens (Section 3) in any Composable.
     */
    val tokens: SemanticColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColorTokens.current

    /**
     * Resolves ColorScheme for a user-created Custom Theme.
     */
    fun getColorSchemeForCustomTheme(customTheme: CustomTheme): ColorScheme {
        val colors = customTheme.colors
        return if (customTheme.isDark) {
            darkColorScheme(
                primary = colors.primary,
                onPrimary = colors.primaryText,
                primaryContainer = colors.secondaryBackground,
                onPrimaryContainer = colors.primary,
                secondary = colors.secondary,
                onSecondary = Color.White,
                secondaryContainer = colors.cardSurface,
                onSecondaryContainer = colors.secondary,
                tertiary = colors.accent,
                background = colors.mainBackground,
                onBackground = colors.primaryText,
                surface = colors.cardSurface,
                onSurface = colors.primaryText,
                surfaceVariant = colors.secondaryBackground,
                onSurfaceVariant = colors.secondaryText,
                outline = colors.bordersDividers,
                outlineVariant = colors.bordersDividers.copy(alpha = 0.5f)
            )
        } else {
            lightColorScheme(
                primary = colors.primary,
                onPrimary = colors.primaryText,
                primaryContainer = colors.secondaryBackground,
                onPrimaryContainer = colors.primary,
                secondary = colors.secondary,
                onSecondary = Color.White,
                secondaryContainer = colors.cardSurface,
                onSecondaryContainer = colors.secondary,
                tertiary = colors.accent,
                background = colors.mainBackground,
                onBackground = colors.primaryText,
                surface = colors.cardSurface,
                onSurface = colors.primaryText,
                surfaceVariant = colors.secondaryBackground,
                onSurfaceVariant = colors.secondaryText,
                outline = colors.bordersDividers,
                outlineVariant = colors.bordersDividers.copy(alpha = 0.5f)
            )
        }
    }

    /**
     * Resolves ThemeCustomColors for a user-created Custom Theme.
     */
    fun getCustomColorsForCustomTheme(customTheme: CustomTheme): ThemeCustomColors {
        val colors = customTheme.colors
        return ThemeCustomColors(
            theme = AppTheme.IOS_GLASS,
            primaryAccent = colors.primary,
            secondaryAccent = colors.secondary,
            gradientBrush = Brush.horizontalGradient(listOf(colors.primary, colors.secondary)),
            bgGradient = Brush.verticalGradient(listOf(colors.mainBackground, colors.secondaryBackground)),
            cardBackground = colors.cardSurface,
            surfaceTranslucent = colors.cardSurface.copy(alpha = 0.90f),
            ambientGlowColor = colors.accent.copy(alpha = 0.25f),
            unreadBadgeColor = colors.primary,
            borderStroke = colors.bordersDividers,
            isDark = customTheme.isDark
        )
    }

    /**
     * Resolves SemanticInteractionColors for a user-created Custom Theme.
     */
    fun getSemanticColorsForCustomTheme(customTheme: CustomTheme): SemanticInteractionColors {
        val base = getSemanticColors(if (customTheme.isDark) AppTheme.IOS_GLASS else AppTheme.CLASSIC)
        return base.copy(
            pinkHighlight = customTheme.colors.selectionHighlight,
            pinkHighlightContainer = customTheme.colors.selectionHighlight.copy(alpha = 0.2f),
            selectedBorder = customTheme.colors.selectionHighlight.copy(alpha = 0.6f),
            selectedGlow = customTheme.colors.selectionHighlight.copy(alpha = 0.3f)
        )
    }

    /**
     * Resolves SemanticColorTokens for a user-created Custom Theme.
     */
    fun getSemanticTokensForCustomTheme(customTheme: CustomTheme): SemanticColorTokens {
        val cs = getColorSchemeForCustomTheme(customTheme)
        val semantic = getSemanticColorsForCustomTheme(customTheme)
        return SemanticColorTokens(
            primary = cs.primary,
            onPrimary = cs.onPrimary,
            background = cs.background,
            surface = cs.surface,
            surfaceVariant = cs.surfaceVariant,
            onSurface = cs.onSurface,
            outline = cs.outline,
            selected = semantic.pinkHighlight,
            success = semantic.greenSuccess,
            error = semantic.redDestructive,
            warning = semantic.warning,
            info = semantic.info
        )
    }
}

/**
 * DynamicThemeProvider: Injects ThemeManager, dynamic ThemeCustomColors,
 * SemanticInteractionColors, SemanticColorTokens, and scaled curated typography into the Compose tree.
 * Seamlessly switches between the 9 built-in themes and up to 10 user-saved custom themes.
 */
@Composable
fun DynamicThemeProvider(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val personalizationManager = remember { PersonalizationManager.getInstance(context) }
    val personalizationConfig by personalizationManager.config.collectAsState()

    val activeCustomTheme = remember(personalizationConfig.activeCustomThemeId, personalizationConfig.customThemes) {
        personalizationConfig.customThemes.find { it.id == personalizationConfig.activeCustomThemeId }
    }

    val customColors = if (activeCustomTheme != null) {
        ThemeManager.getCustomColorsForCustomTheme(activeCustomTheme)
    } else {
        ThemeManager.getCustomColors(theme)
    }

    val semanticColors = if (activeCustomTheme != null) {
        ThemeManager.getSemanticColorsForCustomTheme(activeCustomTheme)
    } else {
        ThemeManager.getSemanticColors(theme)
    }

    val semanticTokens = if (activeCustomTheme != null) {
        ThemeManager.getSemanticTokensForCustomTheme(activeCustomTheme)
    } else {
        ThemeManager.getSemanticTokens(theme)
    }

    val colorScheme = if (activeCustomTheme != null) {
        ThemeManager.getColorSchemeForCustomTheme(activeCustomTheme)
    } else {
        ThemeManager.getColorScheme(theme)
    }

    val typography = remember(personalizationConfig.activeCuratedFont, personalizationConfig.interfaceTextScale) {
        getCustomTypography(
            fontFamily = personalizationConfig.activeCuratedFont.fontFamily,
            scale = personalizationConfig.interfaceTextScale
        )
    }

    CompositionLocalProvider(
        LocalThemeManager provides ThemeManager,
        LocalThemeCustomColors provides customColors,
        LocalSemanticInteractionColors provides semanticColors,
        LocalSemanticColorTokens provides semanticTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
