package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.VisualizerStyle
import com.example.visualizer.SynthwaveCompositionRenderer
import com.example.visualizer.VisualCompositionConfig
import com.example.visualizer.VisualizerFrame
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unified V1.5 Synthwave Monitor Stage Canvas used on the main Player Screen.
 * Renders the spinning vinyl + neon tonearm, 3D rotating cylinder typography,
 * background wallpaper with opacity, audio-reactive visualizer, synchronized lyrics,
 * and two-column playlist overlay using the shared [SynthwaveCompositionRenderer].
 */
@Composable
fun SynthwaveStageCanvas(
    song: Song?,
    songs: List<Song>,
    config: VisualCompositionConfig,
    frame: VisualizerFrame,
    artworkBitmap: Bitmap?,
    wallpaperBitmap: Bitmap?,
    songTimeMs: Long,
    showLyrics: Boolean,
    isFullscreenStage: Boolean,
    onToggleFullscreenStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("synthwave_stage_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Monitor Bezel Frame (inspired by V1.5 Monitor Preview)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF050811))
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(config.curatedSkin.primaryCyanInt).copy(alpha = 0.7f),
                            Color(config.curatedSkin.accentPinkInt).copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(3.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(11.dp))
                    .testTag("synthwave_composition_canvas")
            ) {
                drawIntoCanvas { composeCanvas ->
                    SynthwaveCompositionRenderer.renderFrame(
                        canvas = composeCanvas.nativeCanvas,
                        width = size.width.toInt().coerceAtLeast(1),
                        height = size.height.toInt().coerceAtLeast(1),
                        song = song,
                        songs = songs,
                        config = config,
                        artworkBitmap = artworkBitmap,
                        wallpaperBitmap = wallpaperBitmap,
                        songTimeMs = songTimeMs,
                        frequencies = frame.frequencies,
                        waveform = frame.waveform,
                        showLyrics = showLyrics
                    )
                }
            }

            // Top-right Fullscreen stage toggle button (matches V1.5 monitor expand button)
            Surface(
                color = Color(0xAA0A0E1A),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = onToggleFullscreenStage,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("toggle_fullscreen_stage_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreenStage) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreenStage) "Exit Fullscreen Stage" else "Expand Fullscreen Stage",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Non-blocking FFT attachment notice when RECORD_AUDIO permission is missing
            if (frame.attachmentError != null) {
                Surface(
                    color = Color(0xCC241122),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .testTag("visualizer_attachment_error")
                ) {
                    Text(
                        text = frame.attachmentError,
                        color = Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Subtle Monitor Stand Base when not in fullscreen
        if (!isFullscreenStage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF334155), Color(0xFF94A3B8), Color(0xFF334155))
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF64748B), Color(0xFF1E293B))
                        )
                    )
            )
        }
    }
}

@Composable
fun VisualizerView(
    frame: VisualizerFrame,
    style: VisualizerStyle,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFFB57CFF),
    accentColor: Color = Color(0xFF00F0FF),
    glowColor: Color = Color(0xFFFF52D9)
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (frame.attachmentError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F1429))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("visualizer_attachment_error"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Audio Visualizer Unavailable",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = frame.attachmentError,
                        color = Color(0xFFD4C8EB),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            when (style) {
                VisualizerStyle.NEON_SUNSET_GRID,
                VisualizerStyle.SPECTRUM_BARS,
                VisualizerStyle.RETRO_HORIZON_WAVE -> {
                    SpectrumBarsVisualizer(
                        frame = frame,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        glowColor = glowColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("visualizer_spectrum_bars")
                    )
                }
                VisualizerStyle.RADIAL_CIRCLE -> {
                    RadialCircleVisualizer(
                        frame = frame,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("visualizer_radial_circle")
                    )
                }
                VisualizerStyle.MIRRORED_WAVE -> {
                    MirroredWaveVisualizer(
                        frame = frame,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("visualizer_mirrored_wave")
                    )
                }
            }
        }
    }
}

@Composable
fun SpectrumBarsVisualizer(
    frame: VisualizerFrame,
    primaryColor: Color,
    accentColor: Color,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val width = size.width
        val height = size.height
        val freqs = frame.frequencies
        val count = freqs.size.coerceAtMost(32)
        if (count == 0) return@Canvas

        val spacing = width / count
        val barWidth = spacing * 0.7f

        val gradientBrush = Brush.verticalGradient(
            colors = listOf(glowColor, primaryColor, accentColor),
            startY = 0f,
            endY = height
        )

        for (i in 0 until count) {
            val magnitude = freqs[i].coerceIn(0f, 1f)
            val barHeight = (magnitude * (height - 8f)).coerceAtLeast(4f)
            val left = i * spacing + (spacing - barWidth) / 2f
            val top = height - barHeight

            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

@Composable
fun RadialCircleVisualizer(
    frame: VisualizerFrame,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.height / 3.4f).coerceAtLeast(20f)
        val rms = frame.rmsLevel.coerceIn(0f, 1f)
        val pulseRadius = baseRadius + rms * 14.dp.toPx()

        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = pulseRadius,
            center = center
        )

        drawCircle(
            color = accentColor,
            radius = pulseRadius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        val freqs = frame.frequencies
        val count = freqs.size
        for (i in 0 until count) {
            val angle = (i * (2 * Math.PI) / count)
            val mag = freqs[i].coerceIn(0f, 1f)
            val rayLength = 6.dp.toPx() + mag * 24.dp.toPx()

            val startX = center.x + pulseRadius * cos(angle).toFloat()
            val startY = center.y + pulseRadius * sin(angle).toFloat()
            val endX = center.x + (pulseRadius + rayLength) * cos(angle).toFloat()
            val endY = center.y + (pulseRadius + rayLength) * sin(angle).toFloat()

            drawLine(
                color = if (i % 2 == 0) primaryColor else accentColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun MirroredWaveVisualizer(
    frame: VisualizerFrame,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val waveform = frame.waveform
        if (waveform.isEmpty()) return@Canvas

        val path = Path()
        val step = width / (waveform.size - 1)

        path.moveTo(0f, centerY)
        for (i in waveform.indices) {
            val x = i * step
            val amp = waveform[i] * (height / 2.3f)
            val y = centerY + amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(primaryColor, accentColor, primaryColor)),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        val mirroredPath = Path()
        for (i in waveform.indices) {
            val x = i * step
            val amp = waveform[i] * (height / 2.3f)
            val y = centerY - amp
            if (i == 0) mirroredPath.moveTo(x, y) else mirroredPath.lineTo(x, y)
        }

        drawPath(
            path = mirroredPath,
            color = primaryColor.copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
