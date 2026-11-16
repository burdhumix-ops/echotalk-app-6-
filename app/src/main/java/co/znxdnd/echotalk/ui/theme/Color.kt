package co.znxdnd.echotalk.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------
// 1. STANDARD LIGHT THEME (Section 7)
// -------------------------------------------------------------
val ClassicBg = Color(0xFFF7F8FC)
val ClassicSurface = Color(0xFFFFFFFF)
val ClassicCard = Color(0xFFFFFFFF)
val ClassicPrimary = Color(0xFF4568E8)
val ClassicAccent = Color(0xFF4568E8)
val ClassicSecondary = Color(0xFFE9EDFF)
val ClassicText = Color(0xFF20243A)
val ClassicTextMuted = Color(0xFF68708A)
val ClassicBorder = Color(0xFFE2E6F0)

// -------------------------------------------------------------
// 2. STANDARD DARK THEME (Section 7)
// -------------------------------------------------------------
val DarkBg = Color(0xFF10131B)
val DarkSurface = Color(0xFF191E29)
val DarkSurfaceSecondary = Color(0xFF222A38)
val DarkPrimary = Color(0xFF8DA7FF)
val DarkSecondary = Color(0xFF303B58)
val DarkText = Color(0xFFF2F4FA)
val DarkTextMuted = Color(0xFFA8B0C2)
val DarkBorder = Color(0xFF343D4D)

// iOS Glassmorphism (Deep frosted midnight slate with subtle indigo glow)
val IosGlassBg = Color(0xFF0A0F18)
val IosGlassSurface = Color(0xEB121B2B)
val IosGlassCard = Color(0x331E2D47)
val IosGlassText = Color(0xFFF8FAFC)
val IosGlassTextMuted = Color(0xFF9BB1D6)
val IosGlassAccent = Color(0xFF6366F1)
val IosGlassBorder = Color(0x3338BDF8)

// 3. Warm Sand & Cream
val WarmSandBg = Color(0xFFFDFBF7)
val WarmSandSurface = Color(0xFFF5EBE0)
val WarmSandCard = Color(0xFFF5EBE0)
val WarmSandText = Color(0xFF3D2C2E)
val WarmSandTextMuted = Color(0xFF785954)
val WarmSandAccent = Color(0xFFC86448)
val WarmSandBorder = Color(0x26C86448)

// 4. Midnight Emerald
val EmeraldBg = Color(0xFF0D1B1E)
val EmeraldSurface = Color(0xFF162A2D)
val EmeraldCard = Color(0xFF162A2D)
val EmeraldText = Color(0xFFECFDF5)
val EmeraldTextMuted = Color(0xFF86EFAC)
val EmeraldAccent = Color(0xFF10B981)
val EmeraldBorder = Color(0x2610B981)

// 5. Obsidian OLED Black
val ObsidianBg = Color(0xFF000000)
val ObsidianSurface = Color(0xFF121212)
val ObsidianCard = Color(0xFF121212)
val ObsidianText = Color(0xFFFFFFFF)
val ObsidianTextMuted = Color(0xFFA1A1AA)
val ObsidianAccent = Color(0xFF8B5CF6)
val ObsidianBorder = Color(0x268B5CF6)

// 6. Velvet Rose Gold
val VelvetMidnightSlateBg = Color(0xFF0F0E17)
val VelvetCharcoalSurface = Color(0xFF1A1822)
val VelvetCharcoalCard = Color(0xFF221F2C)
val VelvetRoseGoldPrimary = Color(0xFFE8A5C8)
val VelvetRoseGoldSecondary = Color(0xFFE287B2)
val VelvetGoldAccent = Color(0xFFD4AF37)
val VelvetChampagneText = Color(0xFFFFFFF0)
val VelvetBlushTextMuted = Color(0xFFC4A1B3)
val VelvetBorder = Color(0x33E8A5C8)

// 7. Midnight Slate (Deep frosted midnight slate with subtle indigo glow)
val MidnightSlateBg = Color(0xFF0A0F18)
val MidnightSlateSurface = Color(0xFF121B2B)
val MidnightSlateCard = Color(0xFF1A263C)
val MidnightSlatePrimary = Color(0xFF38BDF8)
val MidnightSlateSecondary = Color(0xFF818CF8)
val MidnightSlateAccent = Color(0xFF6366F1)
val MidnightSlateText = Color(0xFFF8FAFC)
val MidnightSlateTextMuted = Color(0xFF9BB1D6)
val MidnightSlateBorder = Color(0x3D38BDF8)

// -------------------------------------------------------------
// 8. EXACT PINK BLUSH & ROSE COLOUR PALETTE (Section 4)
// -------------------------------------------------------------
val PinkBlushBg = Color(0xFFFFF4F8)                 // Main background
val PinkBlushBgSecondary = Color(0xFFFFEAF2)        // Secondary background
val PinkBlushSurface = Color(0xFFFFFFFF)            // Main card surface
val PinkBlushCardSecondary = Color(0xFFFFF0F5)      // Secondary card surface
val PinkBlushCard = Color(0xFFFFFFFF)               // Default card surface
val PinkBlushPrimary = Color(0xFFE85D9E)            // Primary pink
val PinkBlushDeepRose = Color(0xFFC83E7D)           // Deep rose
val PinkBlushSoftRose = Color(0xFFF3A6C8)           // Soft rose
val PinkBlushBorder = Color(0xFFF5C5D9)             // Light pink border
val PinkBlushSelectedBg = Color(0xFFFCE0EC)         // Selected background
val PinkBlushText = Color(0xFF382331)               // Main text
val PinkBlushTextSecondary = Color(0xFF806777)      // Secondary text
val PinkBlushTextMuted = Color(0xFF806777)          // Secondary labels
val PinkBlushTextDisabled = Color(0xFFB8A5AF)       // Disabled text
val PinkBlushTextWhite = Color(0xFFFFFFFF)          // White text
val PinkBlushLavender = Color(0xFFA78BFA)           // Lavender accent

// Exact Gradients
val PinkBlushMainGradient = Brush.horizontalGradient(
    listOf(Color(0xFFE85D9E), Color(0xFFC83E7D))
)
val PinkBlushSoftBgGradient = Brush.verticalGradient(
    listOf(Color(0xFFFFF4F8), Color(0xFFFFEAF2))
)

// Legacy alias helpers for backward compatibility
val PinkBlushSecondary = PinkBlushDeepRose
val PinkBlushTertiary = PinkBlushSoftRose
val PinkBlushCardElevated = PinkBlushCardSecondary
val PinkBlushDustyRose = PinkBlushDeepRose
val PinkBlushLavenderLight = PinkBlushSelectedBg

// -------------------------------------------------------------
// 9. NEW PREMIUM VISUAL IDENTITY — AURORA BLOOM (Exact Specs)
// -------------------------------------------------------------
// Light theme — exact colours
val AuroraLightMainBackground = Color(0xFFF4F2FF)
val AuroraLightSecondaryBackground = Color(0xFFECE9FF)
val AuroraLightMainSurface = Color(0xFFFFFFFF)
val AuroraLightSecondarySurface = Color(0xFFF8F7FF)
val AuroraLightPrimary = Color(0xFF7867D8)
val AuroraLightPrimaryDark = Color(0xFF5143A6)
val AuroraLightPrimarySoft = Color(0xFFB8AEF2)
val AuroraLightAquaAccent = Color(0xFF72CDBB)
val AuroraLightAquaSurface = Color(0xFFE5F7F2)
val AuroraLightRoseAccent = Color(0xFFF2A6C7)
val AuroraLightBorder = Color(0xFFE5E1F2)
val AuroraLightSelectedSurface = Color(0xFFEAE6FF)
val AuroraLightMainText = Color(0xFF29243F)
val AuroraLightSecondaryText = Color(0xFF77718E)
val AuroraLightDisabledText = Color(0xFFAAA6BA)
val AuroraLightWhite = Color(0xFFFFFFFF)

// Dark theme — exact colours
val AuroraDarkMainBackground = Color(0xFF171624)
val AuroraDarkSecondaryBackground = Color(0xFF1E1C30)
val AuroraDarkMainSurface = Color(0xFF252238)
val AuroraDarkSecondarySurface = Color(0xFF2D2943)
val AuroraDarkPrimary = Color(0xFFA99AFB)
val AuroraDarkPrimaryDark = Color(0xFF8978E8)
val AuroraDarkAquaAccent = Color(0xFF80DCC9)
val AuroraDarkRoseAccent = Color(0xFFF5B4D1)
val AuroraDarkBorder = Color(0xFF3A3551)
val AuroraDarkSelectedSurface = Color(0xFF332E4D)
val AuroraDarkMainText = Color(0xFFF6F3FF)
val AuroraDarkSecondaryText = Color(0xFFB9B3CF)
val AuroraDarkDisabledText = Color(0xFF777187)

// Gradients
val AuroraMainGradient = Brush.horizontalGradient(
    listOf(Color(0xFF7867D8), Color(0xFF5143A6))
)
val AuroraSoftBgGradient = Brush.verticalGradient(
    listOf(Color(0xFFF4F2FF), Color(0xFFECE9FF))
)
val AuroraAquaGradient = Brush.horizontalGradient(
    listOf(Color(0xFF72CDBB), Color(0xFFA5E4D5))
)

// Semantic action colours (independent from decorative palette)
val AuroraSemanticConfirm = Color(0xFF3478F6)
val AuroraSemanticDelete = Color(0xFFD92D3A)
val AuroraSemanticError = Color(0xFFE5484D)
val AuroraSemanticSuccess = Color(0xFF239B69)
val AuroraSemanticWarning = Color(0xFFD99427)

