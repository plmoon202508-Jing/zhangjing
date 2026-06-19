package com.asiainfo.satellite.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

/** 全屏粒子星空背景（与 Web 原型一致的视觉） */
@Composable
fun Starfield(modifier: Modifier = Modifier, count: Int = 160) {
    val stars = remember {
        List(count) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                r = Random.nextFloat() * 1.6f + 0.4f,
                phase = Random.nextFloat() * 6.28f,
                speed = 0.5f + Random.nextFloat() * 1.5f
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "stars")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        stars.forEach { s ->
            val alpha = 0.35f + 0.45f * sin(t * s.speed + s.phase)
            drawCircle(
                color = Color(0xFFB4DCFF).copy(alpha = (alpha * 0.7f).coerceIn(0f, 1f)),
                radius = s.r,
                center = Offset(s.x * size.width, s.y * size.height)
            )
        }
    }
}
