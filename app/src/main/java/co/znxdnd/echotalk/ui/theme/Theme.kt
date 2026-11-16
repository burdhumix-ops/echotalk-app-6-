package co.znxdnd.echotalk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.znxdnd.echotalk.data.repository.AppTheme

fun getColorSchemeForTheme(theme: AppTheme): ColorScheme {
    return when (theme) {
        AppTheme.CLASSIC -> lightColorScheme(
            primary = ClassicPrimary,
            onPrimary = Color.White,
            secondary = ClassicSecondary,
            onSecondary = ClassicPrimary,
            background = ClassicBg,
            surface = ClassicSurface,
            surfaceVariant = Color(0xFFF1F5F9),
            onBackground = ClassicText,
            onSurface = ClassicText,
            onSurfaceVariant = ClassicTextMuted,
            outline = ClassicBorder,
            outlineVariant = Color(0x1AE2E6F0)
        )
        AppTheme.IOS_GLASS -> darkColorScheme(
            primary = IosGlassAccent,
            onPrimary = Color.White,
            secondary = Color(0xFF38BDF8),
            background = IosGlassBg,
            surface = IosGlassSurface,
            surfaceVariant = IosGlassCard,
            onBackground = IosGlassText,
            onSurface = IosGlassText,
            onSurfaceVariant = IosGlassTextMuted,
            outline = IosGlassBorder,
            outlineVariant = Color(0x0DFFFFFF)
        )
        AppTheme.WARM_SAND -> lightColorScheme(
            primary = WarmSandAccent,
            onPrimary = Color.White,
            secondary = Color(0xFFD97706),
            background = WarmSandBg,
            surface = WarmSandSurface,
            surfaceVariant = Color(0xFFEFE3D5),
            onBackground = WarmSandText,
            onSurface = WarmSandText,
            onSurfaceVariant = WarmSandTextMuted,
            outline = WarmSandBorder,
            outlineVariant = Color(0x14C86448)
        )
        AppTheme.MIDNIGHT_EMERALD -> darkColorScheme(
            primary = EmeraldAccent,
            onPrimary = Color.Black,
            secondary = Color(0xFF34D399),
            background = EmeraldBg,
            surface = EmeraldSurface,
            surfaceVariant = EmeraldCard,
            onBackground = EmeraldText,
            onSurface = EmeraldText,
            onSurfaceVariant = EmeraldTextMuted,
            outline = EmeraldBorder,
            outlineVariant = Color(0x1A10B981)
        )
        AppTheme.OBSIDIAN -> darkColorScheme(
            primary = ObsidianAccent,
            onPrimary = Color.White,
            secondary = Color(0xFFA78BFA),
            background = ObsidianBg,
            surface = ObsidianSurface,
            surfaceVariant = ObsidianCard,
            onBackground = ObsidianText,
            onSurface = ObsidianText,
            onSurfaceVariant = ObsidianTextMuted,
            outline = ObsidianBorder,
            outlineVariant = Color(0x1F8B5CF6)
        )
        AppTheme.VELVET_ROSE_GOLD -> darkColorScheme(
            primary = VelvetRoseGoldPrimary,
            onPrimary = VelvetMidnightSlateBg,
            secondary = VelvetRoseGoldSecondary,
            tertiary = VelvetGoldAccent,
            background = VelvetMidnightSlateBg,
            surface = VelvetCharcoalSurface,
            surfaceVariant = VelvetCharcoalCard,
            onBackground = VelvetChampagneText,
            onSurface = VelvetChampagneText,
            onSurfaceVariant = VelvetBlushTextMuted,
            outline = VelvetBorder,
            outlineVariant = Color(0x26E8A5C8)
        )
        AppTheme.MIDNIGHT_SLATE -> darkColorScheme(
            primary = MidnightSlatePrimary,
            onPrimary = MidnightSlateBg,
            secondary = MidnightSlateSecondary,
            tertiary = MidnightSlateAccent,
            background = MidnightSlateBg,
            surface = MidnightSlateSurface,
            surfaceVariant = MidnightSlateCard,
            onBackground = MidnightSlateText,
            onSurface = MidnightSlateText,
            onSurfaceVariant = MidnightSlateTextMuted,
            outline = MidnightSlateBorder,
            outlineVariant = Color(0x2638BDF8)
        )
        AppTheme.PINK_BLUSH -> lightColorScheme(
            primary = PinkBlushPrimary,
            onPrimary = Color.White,
            secondary = PinkBlushDeepRose,
            onSecondary = Color.White,
            tertiary = PinkBlushLavender,
            onTertiary = Color.White,
            background = PinkBlushBg,
            surface = PinkBlushSurface,
            surfaceVariant = PinkBlushCardSecondary,
            onBackground = PinkBlushText,
            onSurface = PinkBlushText,
            onSurfaceVariant = PinkBlushTextSecondary,
            outline = PinkBlushBorder,
            outlineVariant = PinkBlushSelectedBg
        )
        AppTheme.AURORA_BLOOM -> lightColorScheme(
            primary = AuroraLightPrimary,
            onPrimary = Color.White,
            primaryContainer = AuroraLightSelectedSurface,
            onPrimaryContainer = AuroraLightPrimaryDark,
            secondary = AuroraLightAquaAccent,
            onSecondary = Color(0xFF0F3A32),
            secondaryContainer = AuroraLightAquaSurface,
            onSecondaryContainer = Color(0xFF0F3A32),
            tertiary = AuroraLightRoseAccent,
            onTertiary = Color.White,
            background = AuroraLightMainBackground,
            onBackground = AuroraLightMainText,
            surface = AuroraLightMainSurface,
            onSurface = AuroraLightMainText,
            surfaceVariant = AuroraLightSecondarySurface,
            onSurfaceVariant = AuroraLightSecondaryText,
            outline = AuroraLightBorder,
            outlineVariant = AuroraLightSelectedSurface
        )
    }
}

@Composable
fun EchoTalkTheme(
    theme: AppTheme = AppTheme.IOS_GLASS,
    content: @Composable () -> Unit
) {
    DynamicThemeProvider(theme = theme, content = content)
}
