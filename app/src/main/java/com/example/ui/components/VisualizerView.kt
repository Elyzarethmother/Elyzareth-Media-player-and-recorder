package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VisualizerStyle
import com.example.visualizer.VisualizerFrame
import kotlin.math.cos
import kotlin.math.sin

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
                VisualizerStyle.SPECTRUM_BARS -> {
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
