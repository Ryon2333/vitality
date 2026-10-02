package com.jiang.vitality.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun NowPlayingBubble(
    title: String?,
    playing: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Blue
    val driftX = remember { Animatable(0f) }
    val driftY = remember { Animatable(0f) }
    LaunchedEffect(title) {
        val random = Random(System.nanoTime())
        while (isActive && title != null) {
            val duration = random.nextInt(1_800, 3_600)
            driftX.animateTo(random.nextFloat() * 6f - 3f, tween(duration, easing = FastOutSlowInEasing))
            driftY.animateTo(random.nextFloat() * 7f - 3.5f, tween(duration + 300, easing = FastOutSlowInEasing))
        }
    }
    AnimatedVisibility(
        visible = title != null,
        modifier = modifier,
        enter = fadeIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
            scaleIn(spring(dampingRatio = JiangMotion.FluidDamping, stiffness = JiangMotion.FluidStiffness), initialScale = .28f) +
            slideInVertically(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 2 },
        exit = fadeOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
            scaleOut(spring(dampingRatio = JiangMotion.FluidDamping, stiffness = JiangMotion.FluidStiffness), targetScale = .28f) +
            slideOutVertically(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 2 }
    ) {
        val progress = if (durationMillis > 0L) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
        GlassCard(
            Modifier
                .size(72.dp)
                .graphicsLayer {
                    translationX = driftX.value.dp.toPx()
                    translationY = driftY.value.dp.toPx()
                }
                .clickable(onClick = onOpen),
            shape = CircleShape,
            elevation = 15.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        color = accent.copy(alpha = .16f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(DayFlowPalette),
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(if (playing) "♫" else "Ⅱ", color = Blue, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
