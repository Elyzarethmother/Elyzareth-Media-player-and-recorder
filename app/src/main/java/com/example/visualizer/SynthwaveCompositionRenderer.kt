package com.example.visualizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.LyricsManager
import com.example.data.model.Song
import com.example.ui.VisualizerStyle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared deterministic rendering engine for ELYZARETH Player Pro v1.0.
 * Used across:
 * 1. Live Player Stage (Jetpack Compose nativeCanvas)
 * 2. User-accessible PNG Frame Export (1280x720 and 720x1280)
 * 3. Watermark-Free MP4 Video Recording (1280x720 and 720x1280)
 */
object SynthwaveCompositionRenderer {

    private const val REF_LANDSCAPE_W = 1280f
    private const val REF_LANDSCAPE_H = 720f

    /**
     * Computes the deterministic vinyl rotation angle in degrees at [songTimeMs] for [rpm].
     */
    fun computeVinylAngleDegrees(songTimeMs: Long, rpm: Float): Float {
        if (rpm <= 0f || songTimeMs <= 0L) return 0f
        val seconds = songTimeMs / 1000.0
        val revolutions = seconds * (rpm / 60.0)
        return ((revolutions * 360.0) % 360.0).toFloat()
    }

    /**
     * Computes the deterministic 3D cylinder rotation phase in radians at [songTimeMs]
     * for bidirectional [rotationSpeed] (-5.0x .. +5.0x).
     */
    fun computeCylinderPhaseRadians(songTimeMs: Long, rotationSpeed: Float): Float {
        val seconds = songTimeMs / 1000.0
        // 1.0x corresponds to 0.45 rad/s
        val radians = seconds * rotationSpeed * 0.45
        return (radians % (2.0 * Math.PI)).toFloat()
    }

    /**
     * Resolves the up-to-20 track labels and the highlighted active index (0..19)
     * for the two-column bottom playlist overlay.
     */
    fun resolvePlaylistOverlayItems(
        songs: List<Song>,
        currentSong: Song?,
        config: VisualCompositionConfig
    ): Pair<List<String>, Int> {
        val customLines = config.customTracklistText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)

        if (customLines.isNotEmpty()) {
            val formatted = customLines.mapIndexed { idx, line ->
                val numPrefix = String.format("%02d.", idx + 1)
                if (line.matches(Regex("^\\d+\\..*"))) line else "$numPrefix $line"
            }
            val activeIdx = if (config.highlightedTrackIndex in formatted.indices) {
                config.highlightedTrackIndex
            } else {
                0
            }
            return Pair(formatted, activeIdx)
        }

        val cappedSongs = songs.take(20)
        val formatted = cappedSongs.mapIndexed { idx, s ->
            String.format("%02d. %s", idx + 1, s.songTitle)
        }
        val autoActiveIdx = if (currentSong != null) {
            cappedSongs.indexOfFirst { it.id == currentSong.id }.let { if (it >= 0) it else 0 }
        } else {
            0
        }
        val activeIdx = if (config.highlightedTrackIndex in formatted.indices) {
            config.highlightedTrackIndex
        } else {
            autoActiveIdx
        }
        return Pair(formatted, activeIdx)
    }

    fun renderFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        song: Song?,
        songs: List<Song>,
        config: VisualCompositionConfig,
        artworkBitmap: Bitmap?,
        wallpaperBitmap: Bitmap?,
        songTimeMs: Long,
        frequencies: FloatArray,
        waveform: FloatArray = FloatArray(0),
        showLyrics: Boolean = true
    ) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val isPortrait = height > width
        val scale = min(w / if (isPortrait) 720f else REF_LANDSCAPE_W, h / if (isPortrait) 1280f else REF_LANDSCAPE_H)
        val offsetScaleX = w / REF_LANDSCAPE_W
        val offsetScaleY = h / REF_LANDSCAPE_H

        // 1. Background Skin Gradient
        val skin = config.curatedSkin
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(skin.bgTopInt, skin.bgMidInt, skin.bgBottomInt),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Custom Background Wallpaper with Opacity (0%..100%)
        if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled && config.wallpaperOpacityPercent > 0) {
            val alpha = (config.wallpaperOpacityPercent.coerceIn(0, 100) * 255) / 100
            val wpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = alpha
            }
            val bw = wallpaperBitmap.width.toFloat().coerceAtLeast(1f)
            val bh = wallpaperBitmap.height.toFloat().coerceAtLeast(1f)
            // Center-crop / cover the full stage canvas
            val coverScale = max(w / bw, h / bh)
            val drawW = bw * coverScale
            val drawH = bh * coverScale
            val left = (w - drawW) / 2f
            val top = (h - drawH) / 2f
            canvas.drawBitmap(wallpaperBitmap, null, RectF(left, top, left + drawW, top + drawH), wpPaint)
        }

        // 3. Right / Center Neon Sunset Visualizer & 3D Cylinder Assembly
        // Default center is right-center in landscape, upper-center in portrait, shifted by sunsetOffsetX/Y
        val defaultSunsetCenterX = if (isPortrait) w * 0.52f else w * 0.66f
        val defaultSunsetCenterY = if (isPortrait) h * 0.44f else h * 0.44f
        val sunsetCenterX = defaultSunsetCenterX + config.sunsetOffsetX * offsetScaleX
        val sunsetCenterY = defaultSunsetCenterY + config.sunsetOffsetY * offsetScaleY

        drawVisualizerAndSunsetLayer(
            canvas = canvas,
            width = w,
            height = h,
            centerX = sunsetCenterX,
            centerY = sunsetCenterY,
            scale = scale,
            config = config,
            songTimeMs = songTimeMs,
            frequencies = frequencies,
            waveform = waveform
        )

        // 4. 3D Rotating Cylinder Typography (Top Ring: Album Title, Bottom Ring: Song Title)
        drawCylinderTypography(
            canvas = canvas,
            centerX = sunsetCenterX,
            centerY = sunsetCenterY,
            scale = scale,
            song = song,
            config = config,
            songTimeMs = songTimeMs
        )

        // 5. Left Cover Art + Spinning Vinyl Record + Neon Tonearm
        drawLeftVinylAndCoverAssembly(
            canvas = canvas,
            width = w,
            height = h,
            isPortrait = isPortrait,
            scale = scale,
            offsetScaleX = offsetScaleX,
            offsetScaleY = offsetScaleY,
            song = song,
            config = config,
            artworkBitmap = artworkBitmap,
            songTimeMs = songTimeMs,
            frequencies = frequencies
        )

        // 6. Synchronized Floating Lyrics (when enabled)
        if (showLyrics && song != null) {
            drawFloatingLyrics(
                canvas = canvas,
                width = w,
                height = h,
                isPortrait = isPortrait,
                scale = scale,
                song = song,
                songTimeMs = songTimeMs,
                hasPlaylistOverlay = config.showPlaylistOverlay
            )
        }

        // 7. Two-Column Playlist Overlay with Active-Track Indicator & Cyan Glow
        if (config.showPlaylistOverlay) {
            drawPlaylistOverlay(
                canvas = canvas,
                width = w,
                height = h,
                scale = scale,
                offsetScaleY = offsetScaleY,
                songs = songs,
                currentSong = song,
                config = config
            )
        }
    }

    private fun drawVisualizerAndSunsetLayer(
        canvas: Canvas,
        width: Float,
        height: Float,
        centerX: Float,
        centerY: Float,
        scale: Float,
        config: VisualCompositionConfig,
        songTimeMs: Long,
        frequencies: FloatArray,
        waveform: FloatArray
    ) {
        val skin = config.curatedSkin
        val rms = if (frequencies.isNotEmpty()) {
            frequencies.average().toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        when (config.visualizerStyle) {
            VisualizerStyle.NEON_SUNSET_GRID -> {
                // Retro Synthwave Sun with scanline slices
                val sunRadius = (115f + rms * 22f) * scale
                val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        centerX, centerY - sunRadius,
                        centerX, centerY + sunRadius,
                        intArrayOf(
                            Color.parseColor("#FFFBEB"),
                            Color.parseColor("#F59E0B"),
                            skin.accentPinkInt,
                            Color.parseColor("#6D28D9")
                        ),
                        floatArrayOf(0f, 0.35f, 0.72f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }

                // Sun outer glow
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        centerX, centerY, sunRadius * 1.55f,
                        intArrayOf(
                            Color.argb(110, Color.red(skin.accentPinkInt), Color.green(skin.accentPinkInt), Color.blue(skin.accentPinkInt)),
                            Color.argb(40, Color.red(skin.primaryCyanInt), Color.green(skin.primaryCyanInt), Color.blue(skin.primaryCyanInt)),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(centerX, centerY, sunRadius * 1.55f, glowPaint)

                // Draw sun with horizontal retro cutouts in lower half
                val saveCount = canvas.saveLayer(
                    centerX - sunRadius,
                    centerY - sunRadius,
                    centerX + sunRadius,
                    centerY + sunRadius,
                    null
                )
                canvas.drawCircle(centerX, centerY, sunRadius, sunPaint)
                val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = skin.bgMidInt
                    alpha = 215
                }
                for (slice in 0 until 6) {
                    val sliceTop = centerY + (slice * 16f - 4f) * scale
                    val sliceHeight = (3.5f + slice * 1.8f) * scale
                    canvas.drawRect(
                        centerX - sunRadius,
                        sliceTop,
                        centerX + sunRadius,
                        sliceTop + sliceHeight,
                        slicePaint
                    )
                }
                canvas.restoreToCount(saveCount)

                // Perspective grid lines beneath the sun driven by timeFlowSpeed
                val horizonY = centerY + sunRadius * 0.78f
                val gridBottomY = (horizonY + 150f * scale).coerceAtMost(height * 0.86f)
                val gridWidth = 420f * scale
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = skin.primaryCyanInt
                    alpha = 115
                    strokeWidth = 1.6f * scale
                    style = Paint.Style.STROKE
                }

                // Radial perspective lines from vanishing point
                val rays = 11
                for (r in -rays / 2..rays / 2) {
                    val topX = centerX + r * 18f * scale
                    val botX = centerX + r * 68f * scale
                    canvas.drawLine(topX, horizonY, botX, gridBottomY, gridPaint)
                }

                // Scrolling horizontal grid lines
                val timePhase = ((songTimeMs / 1000.0) * config.timeFlowSpeed) % 1.0
                for (row in 0 until 6) {
                    val norm = ((row + timePhase) / 6.0).toFloat()
                    val y = horizonY + (norm * norm) * (gridBottomY - horizonY)
                    val halfSpan = (gridWidth * 0.45f) + norm * (gridWidth * 0.55f)
                    gridPaint.alpha = (60 + norm * 140).toInt().coerceIn(40, 200)
                    canvas.drawLine(centerX - halfSpan, y, centerX + halfSpan, y, gridPaint)
                }

                // Audio-reactive spectrum bars along the sunset horizon
                drawEqualizerBars(
                    canvas = canvas,
                    centerX = centerX,
                    baselineY = horizonY,
                    totalWidth = sunRadius * 2.2f,
                    maxBarHeight = 85f * scale,
                    frequencies = frequencies,
                    skin = skin,
                    scale = scale
                )
            }

            VisualizerStyle.SPECTRUM_BARS -> {
                drawEqualizerBars(
                    canvas = canvas,
                    centerX = centerX,
                    baselineY = centerY + 80f * scale,
                    totalWidth = 420f * scale,
                    maxBarHeight = 150f * scale,
                    frequencies = frequencies,
                    skin = skin,
                    scale = scale
                )
            }

            VisualizerStyle.RADIAL_CIRCLE -> {
                val baseRadius = (88f + rms * 24f) * scale
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = skin.primaryCyanInt
                    style = Paint.Style.STROKE
                    strokeWidth = 3.2f * scale
                }
                canvas.drawCircle(centerX, centerY, baseRadius, ringPaint)

                val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 3f * scale
                    strokeCap = Paint.Cap.ROUND
                }
                val count = frequencies.size.coerceAtLeast(1)
                for (i in 0 until count) {
                    val angle = (i * 2.0 * Math.PI) / count
                    val mag = frequencies[i].coerceIn(0f, 1f)
                    val rayLen = (10f + mag * 65f) * scale
                    rayPaint.color = if (i % 2 == 0) skin.accentPinkInt else skin.primaryCyanInt
                    val sx = centerX + baseRadius * cos(angle).toFloat()
                    val sy = centerY + baseRadius * sin(angle).toFloat()
                    val ex = centerX + (baseRadius + rayLen) * cos(angle).toFloat()
                    val ey = centerY + (baseRadius + rayLen) * sin(angle).toFloat()
                    canvas.drawLine(sx, sy, ex, ey, rayPaint)
                }
            }

            VisualizerStyle.MIRRORED_WAVE -> {
                val waveSpan = 420f * scale
                val startX = centerX - waveSpan / 2f
                val samples = if (waveform.isNotEmpty()) waveform else frequencies
                if (samples.isNotEmpty()) {
                    val topPath = Path()
                    val botPath = Path()
                    val step = waveSpan / (samples.size - 1).coerceAtLeast(1)
                    for (i in samples.indices) {
                        val x = startX + i * step
                        val amp = kotlin.math.abs(samples[i]).coerceIn(0f, 1f) * 75f * scale
                        if (i == 0) {
                            topPath.moveTo(x, centerY - amp)
                            botPath.moveTo(x, centerY + amp)
                        } else {
                            topPath.lineTo(x, centerY - amp)
                            botPath.lineTo(x, centerY + amp)
                        }
                    }
                    val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = skin.primaryCyanInt
                        style = Paint.Style.STROKE
                        strokeWidth = 3.5f * scale
                    }
                    canvas.drawPath(topPath, wavePaint)
                    wavePaint.color = skin.accentPinkInt
                    wavePaint.alpha = 180
                    canvas.drawPath(botPath, wavePaint)
                }
            }

            VisualizerStyle.RETRO_HORIZON_WAVE -> {
                val span = 440f * scale
                val startX = centerX - span / 2f
                val count = frequencies.size.coerceAtLeast(1)
                val step = span / count
                val path = Path()
                path.moveTo(startX, centerY + 60f * scale)
                for (i in 0 until count) {
                    val x = startX + i * step
                    val mag = frequencies[i].coerceIn(0f, 1f)
                    val y = centerY + 60f * scale - mag * 110f * scale
                    path.lineTo(x, y)
                }
                path.lineTo(startX + span, centerY + 60f * scale)
                path.close()

                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        centerX, centerY - 60f * scale,
                        centerX, centerY + 60f * scale,
                        skin.accentPinkInt,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                canvas.drawPath(path, fillPaint)
            }
        }
    }

    private fun drawEqualizerBars(
        canvas: Canvas,
        centerX: Float,
        baselineY: Float,
        totalWidth: Float,
        maxBarHeight: Float,
        frequencies: FloatArray,
        skin: CuratedSkin,
        scale: Float
    ) {
        val barCount = frequencies.size.coerceIn(1, 32)
        val spacing = totalWidth / barCount
        val barW = spacing * 0.66f
        val startX = centerX - totalWidth / 2f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until barCount) {
            val mag = if (i < frequencies.size) frequencies[i].coerceIn(0f, 1f) else 0f
            val barH = (mag * maxBarHeight).coerceAtLeast(4f * scale)
            val left = startX + i * spacing + (spacing - barW) / 2f
            val top = baselineY - barH
            barPaint.shader = LinearGradient(
                left, top, left, baselineY,
                intArrayOf(skin.accentPinkInt, skin.primaryCyanInt),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                left,
                top,
                left + barW,
                baselineY,
                4f * scale,
                4f * scale,
                barPaint
            )
        }
    }

    private fun drawCylinderTypography(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        song: Song?,
        config: VisualCompositionConfig,
        songTimeMs: Long
    ) {
        val topTextRaw = config.cylinderTopText.ifBlank {
            song?.album?.ifBlank { "ELYZARETH RECORDS" } ?: "ELYZARETH RECORDS"
        }
        val bottomTextRaw = config.cylinderBottomTextOverride.ifBlank {
            song?.songTitle?.ifBlank { "Open Sky" } ?: "Open Sky"
        }

        val radiusX = 165f * scale
        val radiusY = 26f * scale
        val ringHalfHeight = 52f * scale
        val topRingY = centerY - ringHalfHeight
        val bottomRingY = centerY + ringHalfHeight

        val topColor = config.resolveTopColorInt()
        val bottomColor = config.resolveBottomColorInt()

        // Draw subtle 3D cylinder wireframe rings (matching V1.5 Screenshot 2 right edge)
        val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * scale
            color = topColor
            alpha = 110
        }
        canvas.drawOval(
            RectF(centerX - radiusX, topRingY - radiusY, centerX + radiusX, topRingY + radiusY),
            ringStrokePaint
        )
        ringStrokePaint.color = bottomColor
        ringStrokePaint.alpha = 110
        canvas.drawOval(
            RectF(centerX - radiusX, bottomRingY - radiusY, centerX + radiusX, bottomRingY + radiusY),
            ringStrokePaint
        )

        // Vertical cylinder side edges
        ringStrokePaint.alpha = 70
        canvas.drawLine(centerX - radiusX, topRingY, centerX - radiusX, bottomRingY, ringStrokePaint)
        canvas.drawLine(centerX + radiusX, topRingY, centerX + radiusX, bottomRingY, ringStrokePaint)

        val phase = computeCylinderPhaseRadians(songTimeMs, config.cylinderRotationSpeed)
        val fontSizePx = (config.cylinderFontSizePx.coerceIn(12, 42) * scale).coerceAtLeast(11f)
        val typeface = Typeface.create(config.cylinderFontFamily.typeface, Typeface.BOLD)

        drawCylinderRingText(
            canvas = canvas,
            text = "$topTextRaw   •   ",
            centerX = centerX,
            ringCenterY = topRingY + fontSizePx * 0.35f,
            radiusX = radiusX,
            radiusY = radiusY,
            phaseRadians = phase,
            baseColorInt = topColor,
            fontSizePx = fontSizePx,
            typeface = typeface
        )

        drawCylinderRingText(
            canvas = canvas,
            text = "$bottomTextRaw   •   ",
            centerX = centerX,
            ringCenterY = bottomRingY + fontSizePx * 0.35f,
            radiusX = radiusX,
            radiusY = radiusY,
            phaseRadians = phase + 0.6f,
            baseColorInt = bottomColor,
            fontSizePx = fontSizePx,
            typeface = typeface
        )
    }

    private fun drawCylinderRingText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        ringCenterY: Float,
        radiusX: Float,
        radiusY: Float,
        phaseRadians: Float,
        baseColorInt: Int,
        fontSizePx: Float,
        typeface: Typeface
    ) {
        if (text.isBlank()) return
        val chars = text.toCharArray()
        val totalSlots = chars.size.coerceAtLeast(16)
        val angleStep = (2.0 * Math.PI) / totalSlots

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }

        val r = Color.red(baseColorInt)
        val g = Color.green(baseColorInt)
        val b = Color.blue(baseColorInt)

        // Draw back-facing glyphs first, then front-facing glyphs
        for (pass in 0..1) {
            for (i in chars.indices) {
                val ch = chars[i]
                if (ch == ' ') continue
                val angle = phaseRadians + (i * angleStep).toFloat()
                val depthZ = cos(angle.toDouble()).toFloat() // +1 = closest front, -1 = farthest back
                val isFront = depthZ >= 0f
                if ((pass == 0 && isFront) || (pass == 1 && !isFront)) continue

                val x = centerX + radiusX * sin(angle.toDouble()).toFloat()
                val y = ringCenterY + radiusY * depthZ
                val alpha = if (isFront) {
                    (155 + (depthZ * 100f)).toInt().coerceIn(155, 255)
                } else {
                    (40 + ((1f + depthZ) * 65f)).toInt().coerceIn(30, 105)
                }
                val charScale = 0.78f + (depthZ + 1f) * 0.16f
                paint.textSize = fontSizePx * charScale
                paint.color = Color.argb(alpha, r, g, b)
                if (isFront) {
                    paint.setShadowLayer(8f, 0f, 0f, Color.argb(140, r, g, b))
                } else {
                    paint.clearShadowLayer()
                }
                canvas.drawText(ch.toString(), x, y, paint)
            }
        }
    }

    private fun drawLeftVinylAndCoverAssembly(
        canvas: Canvas,
        width: Float,
        height: Float,
        isPortrait: Boolean,
        scale: Float,
        offsetScaleX: Float,
        offsetScaleY: Float,
        song: Song?,
        config: VisualCompositionConfig,
        artworkBitmap: Bitmap?,
        songTimeMs: Long,
        frequencies: FloatArray
    ) {
        val skin = config.curatedSkin
        val userScale = (config.coverScalePercent.coerceIn(35, 160)) / 100f
        val baseBoxSize = (if (isPortrait) 250f else 285f) * scale * userScale

        val defaultCenterX = if (isPortrait) width * 0.28f else width * 0.21f
        val defaultCenterY = if (isPortrait) height * 0.24f else height * 0.43f
        val centerX = defaultCenterX + config.coverOffsetX * offsetScaleX
        val centerY = defaultCenterY + config.coverOffsetY * offsetScaleY

        val halfBox = baseBoxSize / 2f
        val frameRect = RectF(
            centerX - halfBox,
            centerY - halfBox,
            centerX + halfBox,
            centerY + halfBox
        )
        val cornerRadius = 22f * scale * userScale

        // Outer neon cyan glowing frame (matching V1.5 Screenshot 2)
        val glowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.primaryCyanInt
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * scale
            setShadowLayer(16f * scale, 0f, 0f, skin.primaryCyanInt)
        }
        val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 6, 12, 22)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, boxBgPaint)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, glowBorderPaint)

        // Corner accent dots inside the cyan frame (visible in V1.5 Screenshot 2)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.primaryCyanInt
            style = Paint.Style.FILL
        }
        val dotOffset = 12f * scale * userScale
        val dotRadius = 3.2f * scale * userScale.coerceAtLeast(0.6f)
        canvas.drawCircle(frameRect.left + dotOffset, frameRect.top + dotOffset, dotRadius, dotPaint)
        canvas.drawCircle(frameRect.right - dotOffset, frameRect.bottom - dotOffset, dotRadius, dotPaint)

        // Optional Left Cover Art backdrop inside frame (FIT mode)
        if (artworkBitmap != null && !artworkBitmap.isRecycled) {
            val inset = 8f * scale * userScale
            val innerRect = RectF(
                frameRect.left + inset,
                frameRect.top + inset,
                frameRect.right - inset,
                frameRect.bottom - inset
            )
            val bw = artworkBitmap.width.toFloat().coerceAtLeast(1f)
            val bh = artworkBitmap.height.toFloat().coerceAtLeast(1f)
            val fitScale = min(innerRect.width() / bw, innerRect.height() / bh)
            val dw = bw * fitScale
            val dh = bh * fitScale
            val dx = innerRect.centerX() - dw / 2f
            val dy = innerRect.centerY() - dh / 2f
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 95
            }
            canvas.drawBitmap(artworkBitmap, null, RectF(dx, dy, dx + dw, dy + dh), artPaint)
        }

        // Spinning Vinyl Record Disc
        val rms = if (frequencies.isNotEmpty()) frequencies.average().toFloat().coerceIn(0f, 1f) else 0f
        val vinylRadius = (halfBox * 0.84f) * (1f + rms * 0.03f)
        val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX, centerY, vinylRadius,
                intArrayOf(
                    Color.parseColor("#2B193D"),
                    Color.parseColor("#111726"),
                    Color.parseColor("#080C14")
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, centerY, vinylRadius, vinylPaint)

        // Concentric vinyl grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            color = Color.argb(55, 140, 210, 255)
        }
        for (g in 1..6) {
            val r = vinylRadius * (0.38f + g * 0.09f)
            canvas.drawCircle(centerX, centerY, r, groovePaint)
        }

        // Rotating vinyl ring label & circular text
        val angleDeg = computeVinylAngleDegrees(songTimeMs, config.vinylRpmPreset.rpm)
        canvas.save()
        canvas.rotate(angleDeg, centerX, centerY)

        // Inner label ring
        val labelRadius = vinylRadius * 0.34f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E1534")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, labelRadius, labelPaint)

        val labelRingStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.accentPinkInt
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        }
        canvas.drawCircle(centerX, centerY, labelRadius, labelRingStroke)

        // Circular text around vinyl ("OPEN SKY * ELYZARETH *") matching V1.5 Screenshot 1 & 2
        val vinylTitle = (song?.songTitle ?: "OPEN SKY").uppercase()
        val ringLabel = "$vinylTitle * ELYZARETH * "
        val textRadius = vinylRadius * 0.58f
        val circularTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.primaryCyanInt
            textSize = (10.5f * scale * userScale).coerceAtLeast(7f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val stepDeg = 360f / ringLabel.length.coerceAtLeast(1)
        for (idx in ringLabel.indices) {
            canvas.save()
            canvas.rotate(idx * stepDeg, centerX, centerY)
            canvas.drawText(ringLabel[idx].toString(), centerX, centerY - textRadius, circularTextPaint)
            canvas.restore()
        }

        canvas.restore()

        // Center spindle hole
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawCircle(centerX, centerY, (4.5f * scale * userScale).coerceAtLeast(2.5f), spindlePaint)

        // Neon Pink/Magenta Tonearm (matching V1.5 Screenshot 1 & 2)
        val pivotX = centerX + vinylRadius * 0.90f
        val pivotY = centerY - vinylRadius * 0.72f
        val elbowX = centerX + vinylRadius * 0.68f
        val elbowY = centerY + vinylRadius * 0.14f
        val stylusX = centerX + vinylRadius * 0.52f
        val stylusY = centerY - vinylRadius * 0.04f

        val tonearmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.accentPinkInt
            style = Paint.Style.STROKE
            strokeWidth = (3.4f * scale * userScale).coerceAtLeast(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(12f * scale, 0f, 0f, skin.accentPinkInt)
        }
        val armPath = Path().apply {
            moveTo(pivotX, pivotY)
            lineTo(elbowX, elbowY)
            lineTo(stylusX, stylusY)
        }
        canvas.drawPath(armPath, tonearmPaint)

        // Tonearm pivot & glowing stylus tip
        val stylusGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(10f * scale, 0f, 0f, skin.accentPinkInt)
        }
        canvas.drawCircle(pivotX, pivotY, (4f * scale * userScale).coerceAtLeast(2f), stylusGlowPaint)
        canvas.drawCircle(stylusX, stylusY, (3.2f * scale * userScale).coerceAtLeast(2f), stylusGlowPaint)
    }

    private fun drawFloatingLyrics(
        canvas: Canvas,
        width: Float,
        height: Float,
        isPortrait: Boolean,
        scale: Float,
        song: Song,
        songTimeMs: Long,
        hasPlaylistOverlay: Boolean
    ) {
        val synced = song.syncedLyrics
        val bottomMargin = if (hasPlaylistOverlay) height * 0.22f else height * 0.08f
        val baseY = height - bottomMargin - (if (isPortrait) 64f else 48f) * scale
        val centerX = width * 0.5f

        if (synced.isNotEmpty()) {
            val activeIdx = LyricsManager.getActiveLyricIndex(synced, songTimeMs)
            val activeLine = synced.getOrNull(activeIdx)?.text ?: return
            val nextLine = synced.getOrNull(activeIdx + 1)?.text

            val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 21f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(10f * scale, 0f, 2f * scale, Color.BLACK)
            }
            canvas.drawText(activeLine, centerX, baseY, activePaint)

            if (!nextLine.isNullOrBlank()) {
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(180, 190, 225, 255)
                    textSize = 15f * scale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(6f * scale, 0f, 1f * scale, Color.BLACK)
                }
                canvas.drawText(nextLine, centerX, baseY + 24f * scale, subPaint)
            }
        } else if (!song.lyricsText.isNullOrBlank()) {
            val lines = song.lyricsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val lineIdx = ((songTimeMs / 4000L).toInt()).coerceAtLeast(0) % lines.size
                val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 19f * scale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(10f * scale, 0f, 2f * scale, Color.BLACK)
                }
                canvas.drawText(lines[lineIdx], centerX, baseY, activePaint)
            }
        }
    }

    private fun drawPlaylistOverlay(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        offsetScaleY: Float,
        songs: List<Song>,
        currentSong: Song?,
        config: VisualCompositionConfig
    ) {
        val (items, activeIdx) = resolvePlaylistOverlayItems(songs, currentSong, config)
        val displayItems = if (items.isEmpty()) {
            listOf("01. ${currentSong?.songTitle ?: "ONCE AGAIN"}")
        } else {
            items
        }

        val skin = config.curatedSkin
        val panelHeight = 104f * scale
        val marginX = width * 0.025f
        val panelBottom = (height - 10f * scale) + (config.playlistOffsetY * offsetScaleY)
        val panelTop = panelBottom - panelHeight
        val panelRect = RectF(marginX, panelTop, width - marginX, panelBottom)

        // Dark translucent synth glass box
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 5, 18, 30)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(panelRect, 8f * scale, 8f * scale, bgPaint)

        // Glowing cyan top horizontal border line (matching V1.5 Screenshot 1 & 2)
        val topBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.primaryCyanInt
            strokeWidth = 2.5f * scale
            style = Paint.Style.STROKE
            setShadowLayer(12f * scale, 0f, 0f, skin.primaryCyanInt)
        }
        canvas.drawLine(panelRect.left, panelRect.top, panelRect.right, panelRect.top, topBorderPaint)

        // Subtle outer border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, Color.red(skin.primaryCyanInt), Color.green(skin.primaryCyanInt), Color.blue(skin.primaryCyanInt))
            strokeWidth = 1f * scale
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(panelRect, 8f * scale, 8f * scale, borderPaint)

        // Render two columns: indices 0..9 in left column, 10..19 in right column
        val col1X = panelRect.left + 18f * scale
        val col2X = panelRect.left + panelRect.width() * 0.52f
        val maxRowsPerCol = 5 // 5 visible rows at a time per column so text is crisp and readable
        val startOffset = if (activeIdx >= 5 && activeIdx < 10) {
            (activeIdx - 4).coerceAtLeast(0)
        } else {
            0
        }
        val rowSpacing = 17.5f * scale
        val firstRowY = panelRect.top + 22f * scale

        val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13.5f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(10f * scale, 0f, 0f, skin.primaryCyanInt)
        }

        val inactiveTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6A8EA8")
            textSize = 12.5f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        for (row in 0 until maxRowsPerCol) {
            val leftIndex = startOffset + row
            if (leftIndex < min(10, displayItems.size)) {
                val isCurrent = leftIndex == activeIdx
                val label = if (isCurrent) "▶ ${displayItems[leftIndex].uppercase()}" else "  ${displayItems[leftIndex]}"
                val y = firstRowY + row * rowSpacing
                canvas.drawText(label, col1X, y, if (isCurrent) activeTextPaint else inactiveTextPaint)
            }

            val rightIndex = 10 + startOffset + row
            if (rightIndex < displayItems.size) {
                val isCurrent = rightIndex == activeIdx
                val label = if (isCurrent) "▶ ${displayItems[rightIndex].uppercase()}" else "  ${displayItems[rightIndex]}"
                val y = firstRowY + row * rowSpacing
                canvas.drawText(label, col2X, y, if (isCurrent) activeTextPaint else inactiveTextPaint)
            }
        }
    }
}
