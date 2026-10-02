package com.jiang.vitality.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class BubbleSpec(
    val text: String,
    val width: Dp,
    val startX: Float,
    val startY: Float
)

private class BubbleBody(
    val spec: BubbleSpec,
    val widthPx: Float,
    val heightPx: Float,
    initialX: Float,
    initialY: Float,
    random: Random
) {
    val x = mutableFloatStateOf(initialX)
    val y = mutableFloatStateOf(initialY)
    val dismissed = mutableStateOf(false)
    var velocityX = random.nextFloat() * 14f - 7f
    var velocityY = random.nextFloat() * 14f - 7f
    var accelerationX = 0f
    var accelerationY = 0f
    var targetAccelerationX = 0f
    var targetAccelerationY = 0f
    var nextDisturbanceAt = 0L
}

private data class BubbleMood(
    val speed: Float,
    val acceleration: Float,
    val alpha: Float,
    val disturbanceMinMs: Long,
    val disturbanceMaxMs: Long
)

@Composable
fun LivingRestBubbleField(
    vitality: Int,
    onPlayBubbleSound: (callItADay: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mood = remember(vitality) {
        when (vitality) {
            in 80..100 -> BubbleMood(16f, 8f, .96f, 1_100L, 2_600L)
            in 50..79 -> BubbleMood(11f, 5.5f, .84f, 1_700L, 3_800L)
            else -> BubbleMood(6f, 3f, .68f, 2_600L, 5_000L)
        }
    }
    val specs = remember {
        listOf(
            BubbleSpec("务必好好休息", 148.dp, .05f, .29f),
            BubbleSpec("状态不好再努力都白瞎", 214.dp, .37f, .52f),
            BubbleSpec("休息时前进的重要部分", 208.dp, .11f, .74f)
        )
    }
    val density = LocalDensity.current
    // The recovery marker remains spatially fixed while the page moves beneath it.
    val callWidth = 240.dp
    val callHeight = 88.dp
    val callTop = 72.dp

    BoxWithConstraints(modifier) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val bubbleHeight = with(density) { 58.dp.toPx() }
        val callWidthPx = with(density) { callWidth.toPx() }
        val callHeightPx = with(density) { callHeight.toPx() }
        val callLeft = (areaWidth - callWidthPx).coerceAtLeast(0f)
        val callTopPx = with(density) { callTop.toPx() }
        val bodies = remember(areaWidth, areaHeight) {
            val random = Random(System.nanoTime())
            specs.map { spec ->
                val width = with(density) { spec.width.toPx() }
                BubbleBody(
                    spec = spec,
                    widthPx = width,
                    heightPx = bubbleHeight,
                    initialX = (areaWidth - width).coerceAtLeast(0f) * spec.startX,
                    initialY = (areaHeight - bubbleHeight).coerceAtLeast(0f) * spec.startY,
                    random = random
                )
            }
        }

        LaunchedEffect(bodies, mood) {
            val random = Random(System.nanoTime())
            var previousFrame = 0L
            while (isActive) {
                val frame = androidx.compose.runtime.withFrameNanos { it }
                if (previousFrame == 0L) {
                    previousFrame = frame
                    continue
                }
                val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .034f)
                previousFrame = frame
                val nowMs = frame / 1_000_000L
                val maxSpeedPx = with(density) { mood.speed.dp.toPx() }
                val accelerationPx = with(density) { mood.acceleration.dp.toPx() }

                bodies.forEach { body ->
                    if (body.dismissed.value) return@forEach
                    if (nowMs >= body.nextDisturbanceAt) {
                        body.targetAccelerationX = random.nextFloat() * accelerationPx * 2f - accelerationPx
                        body.targetAccelerationY = random.nextFloat() * accelerationPx * 2f - accelerationPx
                        body.nextDisturbanceAt = nowMs + random.nextLong(
                            mood.disturbanceMinMs,
                            mood.disturbanceMaxMs
                        )
                    }
                    val lowPass = (dt * .72f).coerceAtMost(1f)
                    body.accelerationX += (body.targetAccelerationX - body.accelerationX) * lowPass
                    body.accelerationY += (body.targetAccelerationY - body.accelerationY) * lowPass
                    body.velocityX += body.accelerationX * dt
                    body.velocityY += body.accelerationY * dt
                    val damping = exp((-0.20f * dt).toDouble()).toFloat()
                    body.velocityX *= damping
                    body.velocityY *= damping
                    val speed = hypot(body.velocityX, body.velocityY)
                    if (speed > maxSpeedPx) {
                        val ratio = maxSpeedPx / speed
                        body.velocityX *= ratio
                        body.velocityY *= ratio
                    }

                    var nextX = body.x.floatValue + body.velocityX * dt
                    var nextY = body.y.floatValue + body.velocityY * dt
                    val maxX = (areaWidth - body.widthPx).coerceAtLeast(0f)
                    val maxY = (areaHeight - body.heightPx).coerceAtLeast(0f)
                    if (nextX < 0f || nextX > maxX) {
                        nextX = nextX.coerceIn(0f, maxX)
                        body.velocityX = -body.velocityX * .68f
                        body.targetAccelerationX = if (nextX <= 0f) accelerationPx else -accelerationPx
                    }
                    if (nextY < 0f || nextY > maxY) {
                        nextY = nextY.coerceIn(0f, maxY)
                        body.velocityY = -body.velocityY * .68f
                        body.targetAccelerationY = if (nextY <= 0f) accelerationPx else -accelerationPx
                    }
                    body.x.floatValue = nextX
                    body.y.floatValue = nextY
                    repelFromFixedBubble(
                        body,
                        callLeft,
                        callTopPx,
                        callWidthPx,
                        callHeightPx * 1.75f
                    )
                }

                for (firstIndex in 0 until bodies.lastIndex) {
                    for (secondIndex in firstIndex + 1 until bodies.size) {
                        resolveBubbleCollision(bodies[firstIndex], bodies[secondIndex])
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            bodies.forEachIndexed { index, body ->
                if (!body.dismissed.value) {
                    LivingGlassBubble(
                        text = body.spec.text,
                        width = body.spec.width,
                        height = 58.dp,
                        index = index + 1,
                        moodAlpha = mood.alpha,
                        dismissible = true,
                        onPlaySound = { onPlayBubbleSound(false) },
                        onDismiss = { body.dismissed.value = true },
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = body.x.floatValue
                                translationY = body.y.floatValue
                            }
                            .zIndex(2f)
                    )
                }
            }

            LivingGlassBubble(
                text = "CALL IT A DAY",
                width = callWidth,
                height = callHeight,
                index = 0,
                moodAlpha = mood.alpha,
                dismissible = false,
                onPlaySound = { onPlayBubbleSound(true) },
                onDismiss = {},
                fontSize = 20.sp,
                baseRotation = 45f,
                rotationAmplitude = 0f,
                flowingText = true,
                radiantRainbow = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = callTop)
                    .zIndex(4f)
            )
        }
    }
}

@Composable
private fun LivingGlassBubble(
    text: String,
    width: Dp,
    height: Dp,
    index: Int,
    moodAlpha: Float,
    dismissible: Boolean,
    onPlaySound: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    baseRotation: Float = 0f,
    rotationAmplitude: Float = 2f,
    flowingText: Boolean = false,
    radiantRainbow: Boolean = false
) {
    val context = LocalContext.current
    val extra = 24.dp
    val entranceScale = remember { Animatable(.8f) }
    val entranceAlpha = remember { Animatable(0f) }
    val breathing = remember { Animatable(1f) }
    val livingAlpha = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val clickPulse = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    val dismissProgress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var dismissing by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .94f else 1f,
        animationSpec = spring(dampingRatio = .52f, stiffness = 620f),
        label = "bubble-press"
    )
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val textColorPhase = rememberFlowingColorPhase(5.2f, enabled = flowingText || radiantRainbow)

    LaunchedEffect(index) {
        delay(140L + index * 150L)
        launch { entranceAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        entranceScale.animateTo(1f, spring(dampingRatio = .56f, stiffness = 190f))
    }
    LaunchedEffect(index) {
        val random = Random(System.nanoTime() + index * 101L)
        while (isActive) {
            breathing.animateTo(
                .98f + random.nextFloat() * .05f,
                tween(random.nextInt(1_900, 4_100), easing = FastOutSlowInEasing)
            )
        }
    }
    LaunchedEffect(index) {
        val random = Random(System.nanoTime() + index * 307L)
        while (isActive) {
            livingAlpha.animateTo(
                .96f + random.nextFloat() * .04f,
                tween(random.nextInt(2_400, 5_200), easing = FastOutSlowInEasing)
            )
        }
    }
    LaunchedEffect(index) {
        if (rotationAmplitude <= 0f) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        val random = Random(System.nanoTime() + index * 503L)
        while (isActive) {
            rotation.animateTo(
                -rotationAmplitude + random.nextFloat() * rotationAmplitude * 2f,
                tween(random.nextInt(2_600, 5_900), easing = FastOutSlowInEasing)
            )
        }
    }

    fun handleTap() {
        if (dismissing) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onPlaySound()
        scope.launch {
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
            }
            clickPulse.snapTo(1f)
            clickPulse.animateTo(1.10f, spring(dampingRatio = .42f, stiffness = 540f))
            clickPulse.animateTo(1f, spring(dampingRatio = .48f, stiffness = 380f))
        }
        if (!dismissible) return
        val now = SystemClock.elapsedRealtime()
        tapCount = if (now - lastTapAt <= 650L) tapCount + 1 else 1
        lastTapAt = now
        if (tapCount >= 3) {
            dismissing = true
            tapCount = 0
            performMaximumVibration(context)
            scope.launch {
                dismissProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }

    Box(
        modifier
            .size(width + extra * 2, height + extra * 2)
            .graphicsLayer {
                rotationZ = baseRotation + rotation.value
                val scale = entranceScale.value * breathing.value * pressScale * clickPulse.value *
                    (1f - dismissProgress.value * .28f)
                scaleX = scale
                scaleY = scale
                alpha = entranceAlpha.value * livingAlpha.value * moodAlpha *
                    (1f - dismissProgress.value)
            }
            .semantics {
                role = Role.Button
                onClick {
                    handleTap()
                    true
                }
            }
            .pointerInput(dismissible) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { handleTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (radiantRainbow) {
                val phase = textColorPhase.value
                val colors = flowingPaletteSamples(BubbleWashPalette, phase, 12)
                val insetX = extra.toPx()
                val insetY = extra.toPx()
                val bubbleSize = Size(width.toPx(), height.toPx())
                val bubbleCenter = Offset(size.width / 2f, size.height / 2f)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors[2].copy(alpha = .34f),
                            colors[7].copy(alpha = .18f),
                            Color.Transparent
                        ),
                        center = bubbleCenter,
                        radius = size.width * .62f
                    ),
                    topLeft = Offset(insetX * .22f, insetY * .20f),
                    size = Size(size.width - insetX * .44f, size.height - insetY * .40f),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
                repeat(3) { waveIndex ->
                    val wave = (phase + waveIndex / 3f) % 1f
                    val expansion = extra.toPx() * wave * .72f
                    drawRoundRect(
                        color = colors[(waveIndex * 3 + 1) % colors.size]
                            .copy(alpha = (1f - wave) * .24f),
                        topLeft = Offset(insetX - expansion, insetY - expansion),
                        size = Size(
                            bubbleSize.width + expansion * 2f,
                            bubbleSize.height + expansion * 2f
                        ),
                        cornerRadius = CornerRadius((bubbleSize.height + expansion * 2f) / 2f),
                        style = Stroke((1.8f - wave).coerceAtLeast(.6f).dp.toPx())
                    )
                }
            }
            val progress = ripple.value
            if (progress < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    RecoveryOrange.copy(alpha = (1f - progress) * .62f),
                    size.minDimension * (.20f + progress * .42f),
                    center,
                    style = Stroke(width = 3.dp.toPx())
                )
                repeat(8) { particle ->
                    val angle = particle * (Math.PI * 2.0 / 8.0)
                    val distance = size.minDimension * progress * .38f
                    drawCircle(
                        (if (particle % 2 == 0) RecoveryCoral else RecoveryOrange)
                            .copy(alpha = (1f - progress) * .72f),
                        2.6.dp.toPx() * (1f - progress * .45f),
                        center + Offset(cos(angle).toFloat() * distance, sin(angle).toFloat() * distance)
                    )
                }
            }
        }
        val textContent: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                val flowingModifier = if (flowingText) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = flowingPaletteSamples(
                                        if (radiantRainbow) BubbleWashPalette else CallItADayFlowPalette,
                                        textColorPhase.value,
                                        count = if (radiantRainbow) 12 else 7
                                    ),
                                    center = center,
                                    radius = size.maxDimension * .72f
                                ),
                                blendMode = BlendMode.SrcIn
                            )
                        }
                } else Modifier
                if (radiantRainbow) {
                    Text(
                        text,
                        color = Color.White.copy(alpha = .94f),
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.08f,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.blur(8.dp)
                    )
                }
                Text(
                    text,
                    color = if (flowingText) Color.White else Ink,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.08f,
                    fontWeight = if (radiantRainbow) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = flowingModifier
                )
            }
        }
        if (radiantRainbow) {
            Box(Modifier.size(width, height)) {
                Canvas(Modifier.fillMaxSize()) {
                    val colors = flowingPaletteSamples(BubbleWashPalette, textColorPhase.value, 14)
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = colors,
                            center = center,
                            radius = size.maxDimension * .70f
                        ),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = .30f), Color.Transparent, Color.Black.copy(alpha = .06f))
                        ),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = .90f), Color.White.copy(alpha = .18f), Color.White.copy(alpha = .64f))
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(1.2.dp.toPx())
                    )
                }
                textContent()
            }
        } else {
            GlassCard(Modifier.size(width, height)) { textContent() }
        }
    }
}

private fun performMaximumVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0L, 120L, 45L, 160L, 40L, 220L),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1
        )
    )
}

private fun repelFromFixedBubble(
    body: BubbleBody,
    fixedLeft: Float,
    fixedTop: Float,
    fixedWidth: Float,
    fixedHeight: Float
) {
    val right = body.x.floatValue + body.widthPx
    val bottom = body.y.floatValue + body.heightPx
    val fixedRight = fixedLeft + fixedWidth
    val fixedBottom = fixedTop + fixedHeight
    if (right <= fixedLeft || body.x.floatValue >= fixedRight || bottom <= fixedTop || body.y.floatValue >= fixedBottom) return
    val dx = body.x.floatValue + body.widthPx / 2f - (fixedLeft + fixedWidth / 2f)
    val dy = body.y.floatValue + body.heightPx / 2f - (fixedTop + fixedHeight / 2f)
    if (abs(dx / fixedWidth) > abs(dy / fixedHeight)) {
        body.velocityX = if (dx >= 0f) abs(body.velocityX) + 18f else -abs(body.velocityX) - 18f
    } else {
        body.velocityY = if (dy >= 0f) abs(body.velocityY) + 18f else -abs(body.velocityY) - 18f
    }
}

private fun resolveBubbleCollision(first: BubbleBody, second: BubbleBody) {
    if (first.dismissed.value || second.dismissed.value) return
    val firstCenterX = first.x.floatValue + first.widthPx / 2f
    val firstCenterY = first.y.floatValue + first.heightPx / 2f
    val secondCenterX = second.x.floatValue + second.widthPx / 2f
    val secondCenterY = second.y.floatValue + second.heightPx / 2f
    val dx = secondCenterX - firstCenterX
    val dy = secondCenterY - firstCenterY
    val overlapX = (first.widthPx + second.widthPx) * .46f - abs(dx)
    val overlapY = (first.heightPx + second.heightPx) * .62f - abs(dy)
    if (overlapX <= 0f || overlapY <= 0f) return
    if (overlapX < overlapY) {
        val direction = if (dx >= 0f) 1f else -1f
        val impulse = overlapX * .10f
        first.velocityX -= impulse * direction
        second.velocityX += impulse * direction
        first.x.floatValue -= overlapX * .18f * direction
        second.x.floatValue += overlapX * .18f * direction
    } else {
        val direction = if (dy >= 0f) 1f else -1f
        val impulse = overlapY * .18f
        first.velocityY -= impulse * direction
        second.velocityY += impulse * direction
        first.y.floatValue -= overlapY * .22f * direction
        second.y.floatValue += overlapY * .22f * direction
    }
}
