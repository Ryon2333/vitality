package com.jiang.vitality.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

private const val LIFT_HOLD_MS = 160L
private const val PILL_WIDTH_RATIO = 0.82f
private const val LIFT_SCALE = 0.18f

/**
 * 液态玻璃药丸切换器。
 * 具备双弹簧果冻感滑动、拖拽抬起跟手 (120Hz 逐帧优化)、内容透镜与真实 RGB 色散 Shader、触觉反馈。
 */
@Composable
fun LiquidGlassPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current

        val barWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = barWidthPx / items.size
        val pillWidthPx = itemWidthPx * PILL_WIDTH_RATIO
        fun slotCenter(index: Int) = itemWidthPx * index + itemWidthPx / 2f
        fun indexAt(x: Float) = (x / itemWidthPx).toInt().coerceIn(0, items.size - 1)
        val minCenter = pillWidthPx / 2f
        val maxCenter = barWidthPx - pillWidthPx / 2f

        val leftEdge = remember { Animatable(slotCenter(selectedIndex) - pillWidthPx / 2f) }
        val rightEdge = remember { Animatable(slotCenter(selectedIndex) + pillWidthPx / 2f) }
        var dragging by remember { mutableStateOf(false) }
        var pressedIndex by remember { mutableIntStateOf(-1) }
        var dragCenter by remember { mutableFloatStateOf(slotCenter(selectedIndex)) }
        val lift = animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
            label = "switcher-pill-lift",
        )

        LaunchedEffect(selectedIndex, dragging, itemWidthPx) {
            if (dragging) return@LaunchedEffect
            val targetLeft = slotCenter(selectedIndex) - pillWidthPx / 2f
            val targetRight = targetLeft + pillWidthPx
            val movingRight = targetLeft > leftEdge.value
            val lead = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 340f)
            launch { leftEdge.animateTo(targetLeft, if (movingRight) trail else lead) }
            launch { rightEdge.animateTo(targetRight, if (movingRight) lead else trail) }
        }

        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            var lastNanos = withFrameNanos { it }
            while (dragging) {
                var newLeft = leftEdge.value
                var newRight = rightEdge.value
                withFrameNanos { now ->
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = now
                    val targetLeft = dragCenter - pillWidthPx / 2f
                    val targetRight = dragCenter + pillWidthPx / 2f
                    val movingRight = targetLeft + targetRight > leftEdge.value + rightEdge.value
                    val fast = 1f - exp(-dt * 42f)
                    val slow = 1f - exp(-dt * 16f)
                    newLeft = leftEdge.value +
                        (targetLeft - leftEdge.value) * (if (movingRight) slow else fast)
                    newRight = rightEdge.value +
                        (targetRight - rightEdge.value) * (if (movingRight) fast else slow)
                }
                leftEdge.snapTo(newLeft)
                rightEdge.snapTo(newRight)
            }
        }

        val primary = MaterialTheme.colorScheme.primary
        val pillShape = RoundedCornerShape(percent = 50)
        val pillBrush = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))
                } else {
                    listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.13f))
                },
            )
        }
        val pillBorder = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))
                } else {
                    listOf(Color.White.copy(alpha = 0.90f), primary.copy(alpha = 0.16f))
                },
            )
        }
        val liftGlowColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.28f)
        val pillWidthDp = with(density) { pillWidthPx.toDp() }
        val shadowSpot = Color.Black.copy(alpha = 0.30f)
        val shadowAmbient = Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .width(pillWidthDp)
                .fillMaxHeight()
                .graphicsLayer {
                    val l = leftEdge.value
                    val r = rightEdge.value
                    val liftValue = lift.value
                    translationX = (l + r) / 2f - pillWidthPx / 2f
                    val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                    val liftScale = 1f + LIFT_SCALE * liftValue
                    scaleX = stretch * liftScale
                    scaleY = liftScale
                    shadowElevation = 14.dp.toPx() * liftValue
                    spotShadowColor = shadowSpot
                    ambientShadowColor = shadowAmbient
                    shape = pillShape
                    clip = true
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pillBrush)
                    .border(1.dp, pillBorder, pillShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = lift.value }
                    .background(liftGlowColor),
            )
        }

        val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember { RuntimeShader(PILL_LENS_SHADER) }
        } else {
            null
        }
        val lensModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensShader != null) {
            Modifier.graphicsLayer {
                val l = leftEdge.value
                val r = rightEdge.value
                val liftValue = lift.value
                val liftScale = 1f + LIFT_SCALE * liftValue
                val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                lensShader.setFloatUniform("uCenter", (l + r) / 2f, size.height / 2f)
                lensShader.setFloatUniform(
                    "uHalf",
                    pillWidthPx / 2f * stretch * liftScale,
                    size.height / 2f * liftScale,
                )
                lensShader.setFloatUniform("uZoom", 0.10f + 0.22f * liftValue)
                lensShader.setFloatUniform("uChroma", liftValue)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(lensShader, "content")
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .then(lensModifier),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                SwitcherItem(
                    label = label,
                    selected = index == selectedIndex,
                    pressed = index == pressedIndex && !dragging,
                    onSelect = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .pointerInput(items.size, itemWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        pressedIndex = indexAt(down.position.x)
                        var lifted = false
                        var lastHovered = -1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val x = change.position.x
                                if (!lifted &&
                                    (change.uptimeMillis - down.uptimeMillis >= LIFT_HOLD_MS ||
                                        abs(x - down.position.x) > slop)
                                ) {
                                    lifted = true
                                    lastHovered = indexAt(x)
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    dragging = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (lifted && change.positionChanged()) {
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    val hovered = indexAt(dragCenter)
                                    if (hovered != lastHovered) {
                                        lastHovered = hovered
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (lifted) {
                                        val target = indexAt(dragCenter)
                                        dragging = false
                                        if (target != selectedIndex) onSelect(target)
                                    } else {
                                        onSelect(indexAt(down.position.x))
                                    }
                                    break
                                }
                            }
                        } finally {
                            pressedIndex = -1
                            if (dragging) dragging = false
                        }
                    }
                },
        )
    }
}

@Composable
private fun SwitcherItem(
    label: String,
    selected: Boolean,
    pressed: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "switcher-item-press-scale",
    )

    val isSelected = selected
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = isSelected
                onClick {
                    onSelect()
                    true
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
