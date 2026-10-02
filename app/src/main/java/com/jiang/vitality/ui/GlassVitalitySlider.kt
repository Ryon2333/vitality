package com.jiang.vitality.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

// One full gradient cycle takes this many seconds; slow enough to feel like light
// moving through glass rather than a looping animation.
private const val FlowPeriodSeconds = 8f

private val TrackHeight = 12.dp
// Thumb sits flush inside the trough: radius == TrackHeight / 2 so its rounded
// edge matches the track's corner radius.
private val ThumbRadius = 6.dp
private val TouchHeight = 48.dp

// Periodic palette (blue -> ice -> cyan -> lavender -> back) so the flowing gradient
// wraps seamlessly: the first and last colours are adjacent on the wheel.
private val FlowCycle = listOf(
    Color(0xFF144BB0), // 主题蓝
    Color(0xFF6E9DF2), // 蓝-冰过渡
    Color(0xFF8EB7F5), // 冰蓝
    Color(0xFF6FC3DC), // 青蓝
    Color(0xFFC3B8F8), // 淡紫
    Color(0xFF8EB7F5)  // 冰蓝（循环回主题蓝）
)

/**
 * Liquid-glass 0–100 vitality slider. The selected part of the track shows a slowly
 * flowing blue→ice→cyan→lavender light band that follows the thumb; the unselected part
 * stays a low-contrast translucent trough. The thumb is a touchable glass lens with a
 * specular rim that softens and glows while dragging.
 *
 * The flowing gradient advances in a draw-phase coroutine (never recomposing the dialog
 * or home screen), pauses in the background via [Lifecycle], and collapses to a static
 * gradient when the system "reduce animations" setting is on.
 */
@Composable
fun GlassVitalitySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntRange = 0..100,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val palette = LocalVitalityColors.current
    val ink = palette.ink
    val accent = palette.accent
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    // Compose @Preview has no LifecycleOwner; fall back to the frame clock there
    // (withFrameNanos also stops by itself when the app is backgrounded).
    val inspectionMode = LocalInspectionMode.current
    val lifecycleOwner: LifecycleOwner? = if (inspectionMode) null else LocalLifecycleOwner.current

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.09f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = .48f, stiffness = 560f),
        label = "glass-slider-press"
    )

    // Draw-only animation state: reading it inside the Canvas invalidates draw only.
    val flowPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycleOwner, reduceMotion, enabled) {
        if (reduceMotion || !enabled) return@LaunchedEffect
        val runFlow: suspend () -> Unit = {
            var previousFrame = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (previousFrame == 0L) {
                    previousFrame = frame
                    continue
                }
                val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .1f)
                previousFrame = frame
                flowPhase.floatValue = (flowPhase.floatValue + dt / FlowPeriodSeconds) % 1f
            }
        }
        if (lifecycleOwner != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { runFlow() }
        } else {
            runFlow()
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val rangeSpan = (valueRange.last - valueRange.first).coerceAtLeast(1)
    val fraction = ((value - valueRange.first).toFloat() / rangeSpan).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(TouchHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = "活力值"
                stateDescription = "$value"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    steps = rangeSpan
                )
                setProgress { target ->
                    val snapped = target.roundToInt().coerceIn(valueRange.first, valueRange.last)
                    currentOnValueChange(snapped)
                    currentOnFinished?.invoke()
                    true
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val width = size.width.toFloat()
                val min = valueRange.first.toFloat()
                val max = valueRange.last.toFloat()
                var lastHapticAt = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    lastHapticAt = 0L
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    fun setFromX(x: Float) {
                        val f = (x / width).coerceIn(0f, 1f)
                        val snapped = (min + f * (max - min)).roundToInt()
                            .coerceIn(valueRange.first, valueRange.last)
                        currentOnValueChange(snapped)
                        val now = SystemClock.elapsedRealtime()
                        // Light haptic while dragging, but throttled so it never fires
                        // once per point.
                        if (now - lastHapticAt >= 260L) {
                            lastHapticAt = now
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    setFromX(down.position.x)
                    drag(down.id) { change ->
                        setFromX(change.position.x)
                        change.consume()
                    }
                    pressed = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnFinished?.invoke()
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val trackH = TrackHeight.toPx()
        val thumbR = ThumbRadius.toPx()
        val centerY = height / 2f
        val phase = flowPhase.floatValue
        val travel = (width - thumbR * 2f).coerceAtLeast(0f)
        val thumbX = thumbR + fraction * travel

        // Soft glass shadow under the trough.
        drawRoundRect(
            color = ink.copy(alpha = .05f),
            topLeft = Offset(0f, centerY - trackH / 2f + 2.dp.toPx()),
            size = Size(width, trackH),
            cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
        )

        // Unselected translucent glass trough (top highlight -> depth).
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = .55f),
                    ink.copy(alpha = .07f),
                    ink.copy(alpha = .13f)
                )
            ),
            topLeft = Offset(0f, centerY - trackH / 2f),
            size = Size(width, trackH),
            cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
        )

        // Selected flowing light band.
        if (enabled && fraction > 0f) {
            val flowColors = List(5) { index -> sampleFlowColor(phase, index, 5) }
            val flowBrush = Brush.linearGradient(
                colors = flowColors,
                start = Offset(0f, 0f),
                end = Offset(width, 0f)
            )
            drawRoundRect(
                brush = flowBrush,
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(thumbX, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
            )
            // Top specular line over the lit band.
            drawRoundRect(
                color = Color.White.copy(alpha = .32f),
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(thumbX, trackH * .30f),
                cornerRadius = CornerRadius(trackH * .15f, trackH * .15f)
            )
        }

        // Glass rim around the whole trough.
        drawRoundRect(
            color = Color.White.copy(alpha = if (enabled) .65f else .38f),
            topLeft = Offset(0f, centerY - trackH / 2f),
            size = Size(width, trackH),
            cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            style = Stroke(width = 1.dp.toPx())
        )

        // Touchable glass-lens thumb.
        drawGlassThumb(
            centerX = thumbX,
            centerY = centerY,
            radius = thumbR * pressScale,
            pressed = pressed,
            enabled = enabled,
            accent = accent,
            ink = ink
        )
    }
}

private fun sampleFlowColor(phase: Float, index: Int, sampleCount: Int): Color {
    val n = FlowCycle.size
    val position = (phase * n + index * (n / sampleCount.toFloat())) % n
    val lower = position.toInt()
    val upper = (lower + 1) % n
    val fraction = position - lower
    return lerp(FlowCycle[lower], FlowCycle[upper], fraction)
}

private fun DrawScope.drawGlassThumb(
    centerX: Float,
    centerY: Float,
    radius: Float,
    pressed: Boolean,
    enabled: Boolean,
    accent: Color,
    ink: Color
) {
    val center = Offset(centerX, centerY)
    val bodyAlpha = if (enabled) 1f else .55f

    // Soft accent glow while dragging (kept inside the touch target).
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = (if (pressed) .32f else .12f) * bodyAlpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 3.2f
        ),
        radius = radius * 3.2f,
        center = center
    )

    // Compact lens sized to the trough so the rounded corners stay matched.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = .98f * bodyAlpha),
                Color.White.copy(alpha = .82f * bodyAlpha),
                accent.copy(alpha = .18f * bodyAlpha),
                accent.copy(alpha = .40f * bodyAlpha)
            ),
            center = center - Offset(radius * .30f, radius * .30f),
            radius = radius * 1.7f
        ),
        radius = radius,
        center = center
    )

    // Subtle inner depth ring.
    drawCircle(
        color = ink.copy(alpha = .08f * bodyAlpha),
        radius = radius * .86f,
        center = center,
        style = Stroke(width = 0.8.dp.toPx())
    )

    // Accent rim.
    drawCircle(
        color = accent.copy(alpha = .45f * bodyAlpha),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )

    // Tiny specular highlight dot.
    drawCircle(
        color = Color.White.copy(alpha = .95f * bodyAlpha),
        radius = radius * .26f,
        center = center - Offset(radius * .34f, radius * .38f)
    )

    // Stronger edge highlight while dragging.
    if (pressed) {
        drawCircle(
            color = accent.copy(alpha = .55f),
            radius = radius * 1.28f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "玻璃滑条 20 / 50 / 80", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun GlassVitalitySliderPreview() {
    VitalityTheme(recoveryMode = false) {
        Surface(color = LocalVitalityColors.current.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                Text(
                    "GlassVitalitySlider",
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                PreviewSliderRow(20)
                PreviewSliderRow(50)
                PreviewSliderRow(80)
            }
        }
    }
}

@Preview(name = "拖动示意（自动滑动）", showBackground = true, widthDp = 390, heightDp = 160)
@Composable
private fun GlassVitalitySliderDragPreview() {
    VitalityTheme(recoveryMode = false) {
        Surface(color = LocalVitalityColors.current.background) {
            var value by remember { mutableIntStateOf(20) }
            LaunchedEffect(Unit) {
                var direction = 1
                while (true) {
                    delay(24)
                    value = (value + direction).coerceIn(0, 100)
                    if (value == 100 || value == 0) direction = -direction
                }
            }
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "$value / 100",
                        color = Blue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    GlassVitalitySlider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSliderRow(initial: Int) {
    var value by remember(initial) { mutableIntStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$value / 100",
            color = Blue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        GlassVitalitySlider(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
