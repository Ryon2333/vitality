package com.jiang.vitality.ui.backdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

private const val SliderFlowPeriodSeconds = 8f

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF0088FF)
) {
    val isLightTheme = !isSystemInDarkTheme()
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.10f)
        else Color(0xFF787880).copy(0.18f)
    val flowPalette = remember(accentColor) {
        listOf(
            accentColor,
            Color(0xFF6E9DF2),
            Color(0xFF8EB7F5),
            Color(0xFF6FC3DC),
            Color(0xFFC3B8F8),
            Color(0xFF8EB7F5)
        )
    }

    val trackBackdrop = rememberLayerBackdrop()
    // This state is consumed only by the track's draw phase, so the flowing
    // gradient does not recompose the surrounding screen or dialog.
    val flowPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (isActive) {
            val frame = withFrameNanos { it }
            if (previousFrame != 0L) {
                val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                flowPhase.floatValue =
                    (flowPhase.floatValue + dt / SliderFlowPeriodSeconds) % 1f
            }
            previousFrame = frame
        }
    }

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(value()) }
        val currentOnValueChange by rememberUpdatedState(onValueChange)
        val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    didDrag = false
                    dragValue = this.value
                    dragging = true
                },
                onDragStopped = {
                    val shouldFinish = didDrag
                    if (shouldFinish) {
                        currentOnValueChange(dragValue)
                    }
                    val committedValue = value().coerceIn(valueRange)
                    snapToValue(committedValue) {
                        dragging = false
                        if (shouldFinish) currentOnValueChangeFinished?.invoke(committedValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    dragValue = (
                        if (isLtr) dragValue + delta
                        else dragValue - delta
                    ).coerceIn(valueRange)
                    currentOnValueChange(dragValue)
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() to dragging }
                .collectLatest { (v, isDragging) ->
                    if (!isDragging && dampedDragAnimation.targetValue != v) {
                        dampedDragAnimation.updateValue(v)
                    }
                }
        }
        val displayProgress = {
            val displayedValue = if (dragging) dragValue else dampedDragAnimation.value
            ((displayedValue - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).fastCoerceIn(0f, 1f)
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            currentOnValueChange(targetValue)
                            currentOnValueChangeFinished?.invoke(targetValue)
                        }
                    }
                    .height(6.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .height(6.dp)
                    .fillMaxWidth()
                    .drawBehind {
                        // Keep the gradient's coordinate space at the full track
                        // width. Progress only clips it, so dragging hides/reveals
                        // the flowing light instead of squeezing the colors.
                        val progress = displayProgress()
                        if (progress > 0f) {
                            val phase = flowPhase.floatValue
                            val colors = List(6) { index ->
                                sampleSliderFlowColor(flowPalette, phase, index, 6)
                            }
                            clipRect(right = size.width * progress) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = colors,
                                        startX = 0f,
                                        endX = size.width
                                    )
                                )
                                drawRect(
                                    color = Color.White.copy(alpha = 0.22f),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width,
                                        size.height * 0.30f
                                    )
                                )
                            }
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * displayProgress())
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = lerp(0.18f, 0.08f, progress)))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

private fun sampleSliderFlowColor(
    palette: List<Color>,
    phase: Float,
    index: Int,
    sampleCount: Int
): Color {
    val count = palette.size
    val position = (phase * count + index * (count / sampleCount.toFloat())) % count
    val lower = position.toInt()
    val upper = (lower + 1) % count
    return lerpColor(palette[lower], palette[upper], position - lower)
}
