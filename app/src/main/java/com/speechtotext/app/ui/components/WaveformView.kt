package com.speechtotext.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.GoldAccent
import kotlin.random.Random

@Composable
fun WaveformView(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barCount: Int = 36
) {
    val barAmplitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }

    LaunchedEffect(amplitude, isRecording) {
        if (isRecording) {
            val nextAmp = (amplitude * 1.5f + Random.nextFloat() * 0.15f).coerceIn(0.08f, 1.0f)
            if (barAmplitudes.size >= barCount) {
                barAmplitudes.removeAt(0)
            }
            barAmplitudes.add(nextAmp)
        } else {
            // Gradually decay
            for (i in 0 until barAmplitudes.size) {
                barAmplitudes[i] = (barAmplitudes[i] * 0.85f).coerceAtLeast(0.08f)
            }
        }
    }

    val animatedAmp by animateFloatAsState(
        targetValue = if (isRecording) amplitude else 0.05f,
        animationSpec = tween(100),
        label = "amp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = 4.dp.toPx()
        val barWidth = ((totalWidth - (barCount - 1) * barSpacing) / barCount).coerceAtLeast(2.dp.toPx())

        for (i in 0 until barCount) {
            val amp = if (i < barAmplitudes.size) barAmplitudes[i] else 0.1f
            val barHeight = (canvasHeight * amp).coerceIn(6.dp.toPx(), canvasHeight)
            val left = i * (barWidth + barSpacing)
            val top = (canvasHeight - barHeight) / 2f

            val brush = Brush.verticalGradient(
                colors = listOf(CyanNeon, GoldAccent),
                startY = top,
                endY = top + barHeight
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
