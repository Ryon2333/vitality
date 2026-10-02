package com.jiang.vitality.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val RecoveryCoral = Color(0xFFEC6D4A)
val RecoveryOrange = Color(0xFFFA9C3A)
val RecoveryBlue = Color(0xFF245880)

// Low-saturation "ice" palette generated from the core ink/accent/background.
// These tint the floating geometry and keep the whole surface calm and premium.
val IceBlue = Color(0xFF8EB7F5)
val GrayBlue = Color(0xFFAAB9D5)
val LavenderBlue = Color(0xFFC3B8F8)
val SilverWhite = Color(0xFFE2E8F2)

val RecoveryIce = Color(0xFFEAA58D)
val RecoveryGray = Color(0xFFD7B39E)
val RecoveryLavender = Color(0xFFD5B8C2)
val RecoverySilver = Color(0xFFE8D8CA)

val DayTints = listOf(IceBlue, GrayBlue, LavenderBlue, SilverWhite)
val RecoveryTints = listOf(RecoveryIce, RecoveryGray, RecoveryLavender, RecoverySilver)

@Immutable
data class VitalityColors(
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val background: Color,
    val cardColors: List<Color>,
    val cardBorder: Color
)

private val DayColors = VitalityColors(
    ink = Color(0xFF18243C),
    muted = Color(0xFF18243C).copy(alpha = .62f),
    accent = Color(0xFF144BB0),
    background = Color(0xFFF0F3F8),
    cardColors = listOf(
        Color.White.copy(alpha = .64f),
        Color(0xFFE8EEF7).copy(alpha = .38f),
        Color.White.copy(alpha = .48f)
    ),
    cardBorder = Color.White.copy(alpha = .90f)
)

private val RecoveryColors = VitalityColors(
    ink = Color(0xFF5B332B),
    muted = Color(0xFF8B6054),
    accent = RecoveryCoral,
    background = Color(0xFFFFF0DE),
    cardColors = listOf(
        Color(0xFFFFFBF5).copy(alpha = .62f),
        RecoveryOrange.copy(alpha = .07f),
        Color.White.copy(alpha = .44f)
    ),
    cardBorder = Color.White.copy(alpha = .88f)
)

val LocalVitalityColors = staticCompositionLocalOf { DayColors }

val Ink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalVitalityColors.current.ink

val Muted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalVitalityColors.current.muted

val Blue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalVitalityColors.current.accent

@Composable
fun VitalityTheme(recoveryMode: Boolean, content: @Composable () -> Unit) {
    val colors = if (recoveryMode) RecoveryColors else DayColors
    CompositionLocalProvider(LocalVitalityColors provides colors) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                secondary = if (recoveryMode) RecoveryOrange else Color(0xFF8EB7F5),
                tertiary = if (recoveryMode) RecoveryBlue else Color(0xFFC3B8F8),
                surface = colors.background,
                background = colors.background,
                onSurface = colors.ink,
                onBackground = colors.ink
            ),
            content = content
        )
    }
}

@Composable
fun VitalityRing(
    value: Int,
    modifier: Modifier = Modifier,
    recoveryMode: Boolean = false
) {
    val accent = if (recoveryMode) RecoveryCoral else Blue
    val track = if (recoveryMode) RecoveryOrange.copy(alpha = .18f) else accent.copy(alpha = .11f)
    val ringPalette = if (recoveryMode) CallItADayFlowPalette else DayFlowPalette
    val colorPhase = rememberFlowingColorPhase(
        periodSeconds = if (recoveryMode) 7.2f else 8.4f
    )
    Canvas(modifier.size(202.dp)) {
        val stroke = 13.dp.toPx()
        val inset = stroke / 2
        val diameter = size.minDimension - stroke
        drawArc(track, -90f, 360f, false, Offset(inset, inset), Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
        val sweep = 360f * value.coerceIn(0, 100) / 100f
        drawArc(
            Brush.sweepGradient(
                flowingPaletteSamples(ringPalette, colorPhase.value, count = 8)
            ),
            -90f,
            sweep,
            false,
            Offset(inset, inset),
            Size(diameter, diameter),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun WeekChart(values: List<Int?>, modifier: Modifier = Modifier) {
    val accent = Blue
    Canvas(modifier.fillMaxWidth().height(124.dp)) {
        val gap = size.width / 7f
        val base = size.height - 14.dp.toPx()
        val top = 10.dp.toPx()
        val bar = 16.dp.toPx()
        values.take(7).forEachIndexed { index, value ->
            val x = gap * (index + .5f) - bar / 2
            val corners = androidx.compose.ui.geometry.CornerRadius(bar / 2)
            drawRoundRect(accent.copy(alpha = .10f), Offset(x, top), Size(bar, base - top), corners)
            if (value != null) {
                val height = ((base - top) * value.coerceIn(0, 100) / 100f).coerceAtLeast(5.dp.toPx())
                drawRoundRect(
                    if (index == 6) accent else accent.copy(alpha = .48f),
                    Offset(x, base - height),
                    Size(bar, height),
                    corners
                )
            }
        }
    }
}
