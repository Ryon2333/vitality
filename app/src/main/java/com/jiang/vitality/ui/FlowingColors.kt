package com.jiang.vitality.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

val CallItADayFlowPalette = listOf(
    Color(0xFFE5836C),
    Color(0xFFD58A95),
    Color(0xFFB992B0),
    Color(0xFFD58A95),
    Color(0xFFE5836C)
)

val BubbleWashPalette = listOf(
    Color(0xFFEDA28A),
    Color(0xFFE0949E),
    Color(0xFFCEA0B8),
    Color(0xFFE0949E),
    Color(0xFFEDA28A)
)

val DayFlowPalette = listOf(
    Color(0xFF5E83D9),
    Color(0xFF7E8CDA),
    Color(0xFFA493D2),
    Color(0xFF7E8CDA),
    Color(0xFF5E83D9)
)

@Composable
fun rememberFlowingColorPhase(
    periodSeconds: Float,
    enabled: Boolean = true
): State<Float> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycleOwner, periodSeconds, enabled) {
        if (!enabled) {
            phase.floatValue = 0f
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousFrame = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (previousFrame == 0L) {
                    previousFrame = frame
                    continue
                }
                val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .1f)
                previousFrame = frame
                phase.floatValue = (phase.floatValue + dt / periodSeconds) % 1f
            }
        }
    }
    return phase
}

fun flowingPaletteSamples(
    palette: List<Color>,
    phase: Float,
    count: Int
): List<Color> = List(count) { index ->
    val position = (phase * palette.size + index * palette.size / count.toFloat()) % palette.size
    val lower = position.toInt().coerceIn(0, palette.lastIndex)
    val upper = (lower + 1) % palette.size
    lerp(palette[lower], palette[upper], position - lower)
}
