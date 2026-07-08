package com.jimandreas.blinken.flash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun FlashScreen(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blinkenPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.06f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.75f,
            ),
        )
        drawRect(
            color = color.copy(alpha = alpha),
            style = Stroke(width = strokeWidth),
        )
    }
}
