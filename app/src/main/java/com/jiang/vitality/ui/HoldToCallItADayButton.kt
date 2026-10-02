package com.jiang.vitality.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HoldDurationMillis = 3_000L

@Composable
fun HoldToCallItADayButton(
    onConfirmed: () -> Unit,
    recoveryMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember(context) { context.primaryVibrator() }
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) .955f else 1f,
        animationSpec = spring(dampingRatio = .58f, stiffness = 310f),
        label = "call it a day press"
    )
    val colorPhase = rememberFlowingColorPhase(periodSeconds = 5.8f)
    val palette = if (recoveryMode) CallItADayFlowPalette else DayFlowPalette

    DisposableEffect(vibrator) {
        onDispose { vibrator.cancel() }
    }

    Box(
        modifier = modifier
            .size(194.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 0f
                shape = CircleShape
                clip = false
            }
            .semantics {
                role = Role.Button
                contentDescription = "按住三秒进入 Call it a day 状态"
            }
            .pointerInput(onConfirmed, vibrator) {
                detectTapGestures(
                    onPress = {
                        val startedAt = SystemClock.elapsedRealtime()
                        pressed = true
                        progress.snapTo(0f)
                        vibrator.startRisingVibration()

                        coroutineScope {
                            val progressJob = launch {
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = HoldDurationMillis.toInt(),
                                        easing = LinearEasing
                                    )
                                )
                            }
                            val releasedNormally = tryAwaitRelease()
                            val heldFor = SystemClock.elapsedRealtime() - startedAt
                            progressJob.cancel()
                            pressed = false
                            vibrator.cancel()

                            if (releasedNormally && heldFor >= HoldDurationMillis) {
                                progress.snapTo(1f)
                                vibrator.vibrate(
                                    VibrationEffect.createOneShot(
                                        1_000L,
                                        255
                                    )
                                )
                                // Keep this composable alive until the full-power confirmation
                                // pulse finishes; otherwise entering recovery would dispose it
                                // immediately and cancel the vibrator.
                                delay(1_000L)
                                onConfirmed()
                            } else {
                                progress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = .72f,
                                        stiffness = 330f
                                    )
                                )
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = this.center
            val buttonRadius = 77.dp.toPx()
            val ripplePhase = colorPhase.value
            val liveColors = flowingPaletteSamples(
                palette = palette,
                phase = ripplePhase,
                count = 12
            )

            // The glow is drawn from the same live phase as the button so the
            // light appears to leave the glass instead of following it later.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        liveColors[2].copy(alpha = .24f),
                        liveColors[6].copy(alpha = .12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = buttonRadius * 1.26f
                ),
                radius = buttonRadius * 1.26f,
                center = center
            )
            repeat(3) { index ->
                val wave = (ripplePhase + index / 3f) % 1f
                drawCircle(
                    color = liveColors[(index * 3 + 1) % liveColors.size]
                        .copy(alpha = (1f - wave) * .20f),
                    radius = buttonRadius * (1.02f + wave * .25f),
                    center = center,
                    style = Stroke((1.8f - wave).coerceAtLeast(.55f).dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = liveColors,
                    center = center,
                    radius = buttonRadius
                ),
                radius = buttonRadius,
                center = center
            )
            val ringInset = 4.dp.toPx()
            val ringTopLeft = Offset(center.x - buttonRadius + ringInset, center.y - buttonRadius + ringInset)
            val ringDiameter = buttonRadius * 2f - ringInset * 2f
            val ringSize = Size(ringDiameter, ringDiameter)
            drawArc(
                color = Color.White.copy(alpha = .22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = .65f),
                        Color.White,
                        Color(0xFFB8DDF3),
                        Color.White.copy(alpha = .72f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .17f), Color.Transparent),
                    center = center - Offset(buttonRadius * .28f, buttonRadius * .35f),
                    radius = buttonRadius * .88f
                ),
                center = center - Offset(buttonRadius * .28f, buttonRadius * .35f),
                radius = buttonRadius * .88f
            )
        }
        Text(
            text = "CALL IT\nA DAY",
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun Context.primaryVibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private fun Vibrator.startRisingVibration() {
    val steps = 30
    val timings = LongArray(steps + 1) { index -> if (index == 0) 0L else 100L }
    val amplitudes = IntArray(steps + 1) { index ->
        if (index == 0) 0
        else (18 + (255 - 18) * (index - 1) / (steps - 1)).coerceIn(1, 255)
    }
    vibrate(VibrationEffect.createWaveform(timings, amplitudes, steps))
}
