package com.example.visualizer

import android.graphics.Color
import android.graphics.Typeface
import com.example.ui.VisualizerStyle

enum class CuratedSkin(
    val displayName: String,
    val primaryCyanInt: Int,
    val accentPinkInt: Int,
    val bgTopInt: Int,
    val bgMidInt: Int,
    val bgBottomInt: Int
) {
    RETRO_NEON_DREAM(
        displayName = "1. Retro Neon Dream (Cyan & Pink)",
        primaryCyanInt = Color.parseColor("#00F0FF"),
        accentPinkInt = Color.parseColor("#EC4899"),
        bgTopInt = Color.parseColor("#070B14"),
        bgMidInt = Color.parseColor("#0A0F1D"),
        bgBottomInt = Color.parseColor("#04060C")
    ),
    SUNSET_OUTRUN(
        displayName = "2. Sunset Outrun (Magenta & Gold)",
        primaryCyanInt = Color.parseColor("#F59E0B"),
        accentPinkInt = Color.parseColor("#FF2E93"),
        bgTopInt = Color.parseColor("#180920"),
        bgMidInt = Color.parseColor("#11071A"),
        bgBottomInt = Color.parseColor("#07030C")
    ),
    EMERALD_MATRIX(
        displayName = "3. Emerald Matrix (Cyan & Emerald)",
        primaryCyanInt = Color.parseColor("#10B981"),
        accentPinkInt = Color.parseColor("#00F0FF"),
        bgTopInt = Color.parseColor("#041412"),
        bgMidInt = Color.parseColor("#061C19"),
        bgBottomInt = Color.parseColor("#020A09")
    ),
    WINE_CRIMSON(
        displayName = "4. Wine Crimson (Ruby & Violet)",
        primaryCyanInt = Color.parseColor("#F43F5E"),
        accentPinkInt = Color.parseColor("#A855F7"),
        bgTopInt = Color.parseColor("#1A0812"),
        bgMidInt = Color.parseColor("#120614"),
        bgBottomInt = Color.parseColor("#070208")
    )
}

enum class VinylRpmPreset(val label: String, val rpm: Float) {
    SLOW("SLOW", 4f),
    NORMAL("NORMAL", 8f),   // Established in V1.5 Screenshot 3: NORMAL = 8 RPM
    FAST("FAST", 16f),
    TURBO("TURBO", 24f)
}

enum class StageAspectMode(val label: String) {
    SQUARE_1_1("1:1"),
    WIDESCREEN_16_9("16:9") // Established default in V1.5 Screenshot 3
}

enum class CylinderFontFamily(val displayName: String, val typeface: Typeface) {
    MONOSPACE("Monospace", Typeface.MONOSPACE),
    SANS_SERIF("Sans-Serif", Typeface.SANS_SERIF),
    SERIF("Serif", Typeface.SERIF)
}

/**
 * Immutable configuration state for the ELYZARETH V1.5 Synthwave Visual Composition.
 * Defaults match the values established in the V1.5 reference screenshots:
 * - Skin: 1. Retro Neon Dream (Cyan & Pink)
 * - Visualizer Style: 1. Neon Sunset Grid (Default)
 * - Aspect Ratio: 16:9
 * - Wallpaper Opacity: 80%
 * - Vinyl Rotation Speed: NORMAL (8 RPM)
 * - Time Flow Speed: 2.2x
 * - Top Ring (Album Title): "ELYZARETH RECORDS", Color #EC4899
 * - Bottom Ring (Song Title): active song title (or "Open Sky"), Color #F59E0B
 * - Cylinder Rotation Speed: -3.0x (Bidirectional)
 * - Cylinder Font: Monospace, 20px
 */
data class VisualCompositionConfig(
    val curatedSkin: CuratedSkin = CuratedSkin.RETRO_NEON_DREAM,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.NEON_SUNSET_GRID,
    val stageAspectMode: StageAspectMode = StageAspectMode.WIDESCREEN_16_9,
    val wallpaperUri: String? = null,
    val wallpaperOpacityPercent: Int = 80,
    val vinylRpmPreset: VinylRpmPreset = VinylRpmPreset.NORMAL,
    val sunsetOffsetX: Int = 0,
    val sunsetOffsetY: Int = 0,
    val coverOffsetX: Int = 0,
    val coverOffsetY: Int = 0,
    val coverScalePercent: Int = 100,
    val timeFlowSpeed: Float = 2.2f,
    val audioVolumePercent: Int = 100,
    val cylinderTopText: String = "ELYZARETH RECORDS",
    val cylinderBottomTextOverride: String = "",
    val cylinderRotationSpeed: Float = -3.0f,
    val cylinderFontFamily: CylinderFontFamily = CylinderFontFamily.MONOSPACE,
    val cylinderFontSizePx: Int = 20,
    val cylinderTopColorHex: String = "#EC4899",
    val cylinderBottomColorHex: String = "#F59E0B",
    val showPlaylistOverlay: Boolean = true,
    val playlistOffsetY: Int = 0,
    val customTracklistText: String = "",
    val highlightedTrackIndex: Int = -1 // -1 = auto-sync with active playing song
) {
    fun resolveTopColorInt(): Int {
        return try {
            Color.parseColor(cylinderTopColorHex)
        } catch (_: Exception) {
            Color.parseColor("#EC4899")
        }
    }

    fun resolveBottomColorInt(): Int {
        return try {
            Color.parseColor(cylinderBottomColorHex)
        } catch (_: Exception) {
            Color.parseColor("#F59E0B")
        }
    }

    fun resetPositions(): VisualCompositionConfig {
        return copy(
            sunsetOffsetX = 0,
            sunsetOffsetY = 0,
            coverOffsetX = 0,
            coverOffsetY = 0,
            coverScalePercent = 100,
            playlistOffsetY = 0
        )
    }
}
