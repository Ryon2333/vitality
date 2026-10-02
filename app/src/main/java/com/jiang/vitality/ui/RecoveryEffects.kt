package com.jiang.vitality.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun RecoveryCelebrationOverlay(
    onFinished: () -> Unit,
    onPlaySound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onPlaySound()
        progress.animateTo(1f, tween(1900, easing = FastOutSlowInEasing))
        delay(250)
        onFinished()
    }

    val value = progress.value
    Box(modifier.zIndex(50f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height * .43f)
            drawRect(RecoveryOrange.copy(alpha = ((1f - value) * .58f).coerceAtLeast(0f)))
            drawCircle(
                color = RecoveryCoral.copy(alpha = (1f - value) * .72f),
                radius = size.maxDimension * value * .74f,
                center = center,
                style = Stroke(width = (18f * (1f - value) + 3f).coerceAtLeast(3f))
            )
            drawCircle(
                color = RecoveryOrange.copy(alpha = (1f - value) * .55f),
                radius = size.maxDimension * value * .53f,
                center = center,
                style = Stroke(width = 5f)
            )
            repeat(30) { index ->
                val angle = (index / 30f) * (PI * 2.0) + index * .17
                val distance = size.maxDimension * (.10f + value * (.48f + (index % 5) * .035f))
                val particle = Offset(
                    center.x + cos(angle).toFloat() * distance,
                    center.y + sin(angle).toFloat() * distance
                )
                val color = when (index % 3) {
                    0 -> RecoveryCoral
                    1 -> RecoveryOrange
                    else -> RecoveryBlue
                }
                drawCircle(
                    color = color.copy(alpha = (1f - value).coerceIn(0f, 1f)),
                    radius = 3f + (index % 4) * 2.2f,
                    center = particle
                )
                if (index % 4 == 0) {
                    drawLine(
                        color = color.copy(alpha = (1f - value) * .75f),
                        start = particle,
                        end = particle + Offset(0f, 18f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        val messageAlpha = ((1f - kotlin.math.abs(value - .42f) * 2.3f)).coerceIn(0f, 1f)
        Text(
            "DAY COMPLETE\n今天，完成。",
            color = Color.White.copy(alpha = messageAlpha),
            fontSize = 29.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = messageAlpha
                scaleX = .72f + value.coerceAtMost(.45f) * .72f
                scaleY = scaleX
            }
        )
    }
}
