package io.github.eelnicoo.pocketcode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Ray lengths (fraction of radius) give the hand-drawn, irregular starburst look.
private val RAYS = floatArrayOf(1.0f, 0.78f, 0.93f, 0.74f, 0.97f, 0.82f, 0.9f, 0.76f, 1.0f, 0.8f, 0.92f, 0.77f)

/** Claude's starburst mark. When [animated], it slowly turns and pulses (thinking). */
@Composable
fun ClaudeSpark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = LocalClaude.current.accent,
    animated: Boolean = false,
) {
    val rotation: Float
    val pulse: Float
    if (animated) {
        val t = rememberInfiniteTransition(label = "spark")
        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "rot")
        val p by t.animateFloat(0.82f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
        rotation = r
        pulse = p
    } else {
        rotation = 0f
        pulse = 1f
    }
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val stroke = radius * 0.2f
        rotate(rotation) {
            RAYS.forEachIndexed { i, len ->
                val angle = Math.toRadians(i * 30.0 - 90.0)
                val reach = (radius - stroke / 2f) * len * pulse
                val inner = radius * 0.08f
                drawLine(
                    color = color,
                    start = Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()),
                    end = Offset(center.x + (cos(angle) * reach).toFloat(), center.y + (sin(angle) * reach).toFloat()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
