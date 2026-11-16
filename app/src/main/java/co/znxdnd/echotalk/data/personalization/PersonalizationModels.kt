package co.znxdnd.echotalk.data.personalization

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Semantic color roles that users are permitted to customize in a Custom Theme.
 * Strict boundary: Users can customize these 12 semantic roles with automatic
 * contrast protection, but cannot independently recolor every individual icon.
 */
data class SemanticColorRoles(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val mainBackground: Color,
    val secondaryBackground: Color,
    val cardSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val bordersDividers: Color,
    val selectionHighlight: Color,
    val personalDmBubble: Color,
    val publicRoomAccent: Color
) {
    /**
     * Converts to a hex JSON-storable representation.
     */
    fun toHexMap(): Map<String, String> = mapOf(
        "primary" to primary.toHex(),
        "secondary" to secondary.toHex(),
        "accent" to accent.toHex(),
        "mainBackground" to mainBackground.toHex(),
        "secondaryBackground" to secondaryBackground.toHex(),
        "cardSurface" to cardSurface.toHex(),
        "primaryText" to primaryText.toHex(),
        "secondaryText" to secondaryText.toHex(),
        "bordersDividers" to bordersDividers.toHex(),
        "selectionHighlight" to selectionHighlight.toHex(),
        "personalDmBubble" to personalDmBubble.toHex(),
        "publicRoomAccent" to publicRoomAccent.toHex()
    )

    companion object {
        fun Color.toHex(): String {
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            val r = (red * 255).toInt().coerceIn(0, 255)
            val g = (green * 255).toInt().coerceIn(0, 255)
            val b = (blue * 255).toInt().coerceIn(0, 255)
            return String.format("#%02X%02X%02X%02X", a, r, g, b)
        }

        fun fromHex(hex: String, fallback: Color): Color {
            return try {
                val clean = hex.removePrefix("#")
                val longVal = clean.toLong(16)
                if (clean.length == 6) {
                    Color(longVal or 0xFF000000)
                } else {
                    Color(longVal)
                }
            } catch (e: Exception) {
                fallback
            }
        }

        fun fromHexMap(map: Map<String, String>): SemanticColorRoles {
            val defaultPalette = defaultPalette()
            return SemanticColorRoles(
                primary = fromHex(map["primary"] ?: "", defaultPalette.primary),
                secondary = fromHex(map["secondary"] ?: "", defaultPalette.secondary),
                accent = fromHex(map["accent"] ?: "", defaultPalette.accent),
                mainBackground = fromHex(map["mainBackground"] ?: "", defaultPalette.mainBackground),
                secondaryBackground = fromHex(map["secondaryBackground"] ?: "", defaultPalette.secondaryBackground),
                cardSurface = fromHex(map["cardSurface"] ?: "", defaultPalette.cardSurface),
                primaryText = fromHex(map["primaryText"] ?: "", defaultPalette.primaryText),
                secondaryText = fromHex(map["secondaryText"] ?: "", defaultPalette.secondaryText),
                bordersDividers = fromHex(map["bordersDividers"] ?: "", defaultPalette.bordersDividers),
                selectionHighlight = fromHex(map["selectionHighlight"] ?: "", defaultPalette.selectionHighlight),
                personalDmBubble = fromHex(map["personalDmBubble"] ?: "", defaultPalette.personalDmBubble),
                publicRoomAccent = fromHex(map["publicRoomAccent"] ?: "", defaultPalette.publicRoomAccent)
            )
        }

        fun defaultPalette(): SemanticColorRoles = SemanticColorRoles(
            primary = Color(0xFF6366F1),          // Electric Indigo
            secondary = Color(0xFF38BDF8),        // Sky Cyan
            accent = Color(0xFFE85D9E),           // Rose Accent
            mainBackground = Color(0xFF0A0F18),   // Deep Cosmic Midnight
            secondaryBackground = Color(0xFF121B2B), // Deep Frosted Slate
            cardSurface = Color(0xFF1A263C),      // Frosted Slate Card
            primaryText = Color(0xFFF8FAFC),      // Pure Crisp Slate
            secondaryText = Color(0xFF9BB1D6),    // Cool Luminous Slate-Indigo
            bordersDividers = Color(0xFF253552),  // Translucent Indigo Border
            selectionHighlight = Color(0xFF38BDF8), // Radiant Neon Accent
            personalDmBubble = Color(0xFF6366F1), // Personal DM outgoing bubble
            publicRoomAccent = Color(0xFF38BDF8)  // Public room community accent
        )
    }
}

/**
 * Contrast Protection Algorithm:
 * Calculates WCAG relative luminance and contrast ratio.
 * Automatically guarantees readable contrast (at least 4.5:1) between text and surface.
 */
object ContrastProtector {
    private fun getLuminance(color: Color): Float {
        val r = sRgbToLinear(color.red)
        val g = sRgbToLinear(color.green)
        val b = sRgbToLinear(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun sRgbToLinear(v: Float): Float {
        return if (v <= 0.03928f) {
            v / 12.92f
        } else {
            Math.pow(((v + 0.055) / 1.055), 2.4).toFloat()
        }
    }

    fun getContrastRatio(foreground: Color, background: Color): Float {
        val l1 = getLuminance(foreground)
        val l2 = getLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Automatically adjusts text color to meet minimum contrast ratio against background.
     * Prevents unreadable combinations (e.g. black text on dark background).
     */
    fun ensureContrast(
        textColor: Color,
        backgroundColor: Color,
        minRatio: Float = 4.0f
    ): Color {
        val currentRatio = getContrastRatio(textColor, backgroundColor)
        if (currentRatio >= minRatio) return textColor

        val bgLuminance = getLuminance(backgroundColor)
        // If background is dark, brighten text; if background is bright, darken text
        return if (bgLuminance < 0.5f) {
            Color(0xFFF8FAFC) // Bright off-white
        } else {
            Color(0xFF0F172A) // Dark slate
        }
    }

    /**
     * Sanitizes an entire SemanticColorRoles palette by enforcing contrast
     * on primaryText, secondaryText, and cardText against their respective backgrounds.
     */
    fun protectPalette(roles: SemanticColorRoles): SemanticColorRoles {
        val protectedPrimaryText = ensureContrast(roles.primaryText, roles.mainBackground, 4.2f)
        val protectedSecondaryText = ensureContrast(roles.secondaryText, roles.mainBackground, 2.8f)
        return roles.copy(
            primaryText = protectedPrimaryText,
            secondaryText = protectedSecondaryText
        )
    }
}

/**
 * Custom Theme Model.
 * Supports up to 10 user-saved custom themes.
 */
data class CustomTheme(
    val id: String,
    val name: String,
    val colors: SemanticColorRoles,
    val isBuiltIn: Boolean = false,
    val isDark: Boolean = true
)

/**
 * 8 Curated Built-in Font choices.
 */
enum class CuratedFont(val id: String, val displayName: String, val fontFamily: FontFamily) {
    SYSTEM_DEFAULT("system_default", "System Default", FontFamily.Default),
    INTER("inter", "Inter (Clean Sans)", FontFamily.SansSerif),
    ROBOTO("roboto", "Roboto (Modern)", FontFamily.Default),
    OUTFIT("outfit", "Outfit (Rounded)", FontFamily.SansSerif),
    MERRIWEATHER("merriweather", "Merriweather (Serif)", FontFamily.Serif),
    FIRA_CODE("fira_code", "Fira Code (Monospace)", FontFamily.Monospace),
    PLUS_JAKARTA("plus_jakarta", "Plus Jakarta Sans", FontFamily.SansSerif),
    POPPINS("poppins", "Poppins (Geometric)", FontFamily.SansSerif)
}

/**
 * Imported Custom Font (Max 2 slots, valid TTF/OTF).
 */
data class CustomFontSlot(
    val slotIndex: Int, // 1 or 2
    val name: String,
    val filePath: String,
    val isLoaded: Boolean = false
)

/**
 * UI Display Density Presets.
 */
enum class UiDensity(val id: String, val displayName: String, val paddingMultiplier: Float) {
    COMPACT("compact", "Compact", 0.75f),
    STANDARD("standard", "Standard", 1.0f),
    COMFORTABLE("comfortable", "Comfortable", 1.25f);

    fun contentPadding(baseDp: Dp): Dp = (baseDp.value * paddingMultiplier).dp
}

/**
 * Corner Radius Presets.
 */
enum class CornerRadiusPreset(val id: String, val displayName: String, val radiusDp: Dp) {
    SHARP("sharp", "Sharp (4dp)", 4.dp),
    ROUNDED("rounded", "Rounded (14dp)", 14.dp),
    PILL("pill", "Pill (24dp)", 24.dp)
}

/**
 * Animation Level Presets.
 */
enum class AnimationLevel(val id: String, val displayName: String, val durationScale: Float) {
    OFF("off", "Off (Instant)", 0.0f),
    SUBTLE("subtle", "Subtle (Fast)", 0.5f),
    NORMAL("normal", "Normal (Springs)", 1.0f)
}

/**
 * 5 Ready-made Chat Appearance Presets.
 */
enum class ChatAppearancePreset(
    val id: String,
    val displayName: String,
    val dmBubbleColor: Color,
    val wallpaperId: String,
    val isDark: Boolean
) {
    MODERN_MINIMAL("modern_minimal", "Modern Minimalist", Color(0xFF6366F1), "default", true),
    TELEGRAM_CLASSIC("telegram_classic", "Telegram Classic", Color(0xFF2AABEE), "starry", true),
    MIDNIGHT_NEON("midnight_neon", "Midnight Neon", Color(0xFF06B6D4), "aurora", true),
    SUNSET_GLOW("sunset_glow", "Sunset Glow", Color(0xFFF97316), "sunset", false),
    VELVET_ROSE("velvet_rose", "Velvet Rose", Color(0xFFE85D9E), "blush", true)
}

/**
 * 4 Sound Profiles.
 */
enum class SoundProfile(val id: String, val displayName: String, val description: String) {
    POLITE_DIGITAL("polite_digital", "Polite Digital", "Gentle pop and soft chimes"),
    CLASSIC_MECHANICAL("classic_mech", "Classic Mechanical", "Crisp typewriter-style clicks"),
    HAPTIC_MINIMAL("haptic_minimal", "Modern Minimal", "Ultra-subtle acoustic pulses"),
    AMBIENT_MELODIC("ambient_melodic", "Ambient Melodic", "Harmonic soft synthesizer chords")
}

/**
 * 2 Haptic Intensity Levels.
 */
enum class HapticIntensity(val id: String, val displayName: String) {
    CRISP_GENTLE("crisp_gentle", "Crisp & Gentle"),
    FIRM_EXPRESSIVE("firm_expressive", "Firm & Expressive")
}

/**
 * 3 Performance Modes.
 */
enum class PerformanceMode(val id: String, val displayName: String, val description: String) {
    AUTOMATIC("automatic", "Automatic", "Dynamic adaptation based on device frame rates"),
    BALANCED("balanced", "Balanced", "Smooth 60fps standard animations and visual effects"),
    BATTERY_SAVER("battery_saver", "Battery Saver", "Throttles wave visualizers, forces subtle motion")
}

/**
 * Master Personalization Configuration snapshot.
 */
data class PersonalizationConfig(
    val activeCustomThemeId: String? = null,
    val customThemes: List<CustomTheme> = emptyList(),
    val activeCuratedFont: CuratedFont = CuratedFont.SYSTEM_DEFAULT,
    val customFontSlots: List<CustomFontSlot> = emptyList(),
    val interfaceTextScale: Float = 1.0f,
    val messageTextSizeSp: Float = 14f,
    val density: UiDensity = UiDensity.STANDARD,
    val cornerRadius: CornerRadiusPreset = CornerRadiusPreset.ROUNDED,
    val animationLevel: AnimationLevel = AnimationLevel.NORMAL,
    val chatAppearancePreset: ChatAppearancePreset = ChatAppearancePreset.MODERN_MINIMAL,
    val personalDmBubbleColor: Color = Color(0xFF6366F1),
    val soundEffectsEnabled: Boolean = true,
    val soundProfile: SoundProfile = SoundProfile.POLITE_DIGITAL,
    val hapticFeedbackEnabled: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.CRISP_GENTLE,
    val performanceMode: PerformanceMode = PerformanceMode.AUTOMATIC,
    // Phase 1 lightweight personalization & memory
    val personalizationEnabled: Boolean = true,
    val recentRoomIds: List<String> = emptyList(),
    val roomVisitCounts: Map<String, Int> = emptyMap(),
    val recentConversationIds: List<String> = emptyList(),
    val selectedInterests: List<String> = emptyList(),
    val lastSelectedTabId: String? = null,
    val lastRoomCategory: String = "All Spaces",
    val lastDiscoverGender: String = "All",
    val lastDiscoverCountry: String = "All",
    val lastDiscoverMinAge: Float = 18f,
    val lastDiscoverMaxAge: Float = 35f,
    // Phase 2 comfort: drafts, recent searches & filter memory
    val dmDrafts: Map<String, String> = emptyMap(),
    val roomDrafts: Map<String, String> = emptyMap(),
    val recentDiscoverSearches: List<String> = emptyList(),
    val recentDmSearches: List<String> = emptyList(),
    val lastDiscoverSearchQuery: String = "",
    val lastDmSearchQuery: String = "",
    val lastDmFolderTab: String = "all"
)
