package com.jiang.vitality.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun NowPlayingBubble(
    title: String?,
    playing: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Blue
    val driftX = remember { Animatable(0f) }
    val driftY = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .91f else 1f,
        animationSpec = spring(dampingRatio = JiangMotion.PressDamping, stiffness = JiangMotion.PressStiffness),
        label = "now-playing-press"
    )
    LaunchedEffect(title) {
        val random = Random(System.nanoTime())
        while (isActive && title != null) {
            val duration = random.nextInt(1_800, 3_600)
            coroutineScope {
                launch { driftX.animateTo(random.nextFloat() * 6f - 3f, tween(duration, easing = FastOutSlowInEasing)) }
                launch { driftY.animateTo(random.nextFloat() * 7f - 3.5f, tween(duration + 300, easing = FastOutSlowInEasing)) }
            }
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
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "正在播放 ${title.orEmpty()}，轻触打开播放器，长按停止"
                    onClick { onOpen(); true }
                    onLongClick { confirmStop = true; true }
                }
                .pointerInput(title) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onOpen() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirmStop = true
                        }
                    )
                },
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
    if (confirmStop && title != null) {
        GlassDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("关闭音乐？", color = Ink, fontWeight = FontWeight.SemiBold) },
            text = { Text("将停止《${title.substringBeforeLast('.', title)}》并清除当前播放进度。", color = Muted) },
            actions = {
                GlassActionButton("继续播放", { confirmStop = false })
                GlassActionButton("停止音乐", {
                    confirmStop = false
                    onStop()
                })
            }
        )
    }
}
