package com.jiang.vitality.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// The large river moves slowly enough that 30 Hz remains visually continuous while
// leaving the 60/120 Hz UI thread budget to touch, scrolling and glass interactions.
private const val GeometryFrameIntervalNanos = 32_000_000L

// The single travelling shape morphs between a rounded square (4) and a dodecagon (12).
private const val MinSides = 4
private const val MaxSides = 12

private class GeometryBody(
    sides: Int,
    val radius: Float,
    val aspect: Float,
    val tintIndex: Int,
    x: Float,
    y: Float,
    random: Random
) {
    var sides = sides
        private set
    var previousSides = sides
        private set
    var morphProgress = 1f
    var x = x
    var y = y
    var rotation = random.nextFloat() * 360f
    var scale = .96f + random.nextFloat() * .08f
    var alpha = .80f + random.nextFloat() * .16f
    val flowScale = .85f + random.nextFloat() * .30f
    var velocityX = random.nextFloat() * .006f - .003f
    var velocityY = random.nextFloat() * .006f - .003f
    var accelerationX = 0f
    var accelerationY = 0f
    var targetAccelerationX = 0f
    var targetAccelerationY = 0f
    var rotationVelocity = random.nextFloat() * 2.2f - 1.1f
    var targetRotationVelocity = rotationVelocity
    var targetScale = scale
    var targetAlpha = alpha
    var nextDisturbanceAt = System.nanoTime() / 1_000_000L + random.nextLong(400L, 3_200L)
    var nextSwitchAt = System.nanoTime() / 1_000_000L + random.nextLong(1_600L, 6_800L)
    var morphForward = true

    fun beginMorph(random: Random, nowMs: Long, mood: GeometryMood) {
        previousSides = sides
        sides = if (morphForward) {
            if (sides >= MaxSides) { morphForward = false; sides - 1 } else sides + 1
        } else {
            if (sides <= MinSides) { morphForward = true; sides + 1 } else sides - 1
        }
        morphProgress = 0f
        nextSwitchAt = nowMs + random.nextLong(mood.kindSwitchMinMs, mood.kindSwitchMaxMs)
    }
}

private class RiverBody(
    var progress: Float,
    val speedScale: Float,
    val lane: Float,
    val width: Float,
    val alpha: Float,
    val tintIndex: Int,
    var phase: Float = 0f
)

private data class GeometryMood(
    val maxSpeed: Float,
    val acceleration: Float,
    val opacity: Float,
    val flowSpeed: Float,
    val kindSwitchMinMs: Long,
    val kindSwitchMaxMs: Long,
    val morphSeconds: Float,
    val disturbanceMinMs: Long,
    val disturbanceMaxMs: Long
)

@Composable
fun DynamicGeometryBackground(
    vitality: Int,
    recoveryMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (recoveryMode) {
        OceanSunsetBackground(modifier)
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mood = remember(vitality, recoveryMode) {
        val base = when (vitality) {
            in 80..100 -> GeometryMood(.020f, .0080f, 1f, .110f, 2_200L, 3_800L, 1.0f, 1_600L, 3_600L)
            in 50..79 -> GeometryMood(.013f, .0052f, .82f, .075f, 2_600L, 4_400L, 1.2f, 2_200L, 4_800L)
            else -> GeometryMood(.007f, .0028f, .62f, .045f, 3_000L, 5_200L, 1.5f, 3_000L, 6_200L)
        }
        if (recoveryMode) {
            base.copy(
                maxSpeed = base.maxSpeed * .48f,
                acceleration = base.acceleration * .52f,
                opacity = base.opacity * .68f,
                flowSpeed = base.flowSpeed * .50f,
                kindSwitchMinMs = (base.kindSwitchMinMs * 1.5f).toLong(),
                kindSwitchMaxMs = (base.kindSwitchMaxMs * 1.5f).toLong(),
                morphSeconds = base.morphSeconds * 1.4f,
                disturbanceMinMs = (base.disturbanceMinMs * 1.45f).toLong(),
                disturbanceMaxMs = (base.disturbanceMaxMs * 1.45f).toLong()
            )
        } else base
    }
    val bodies = remember {
        val random = Random(System.nanoTime())
        List(1) {
            GeometryBody(
                sides = MinSides,
                radius = .32f,
                aspect = 1f,
                tintIndex = 0,
                x = 1.08f,
                y = .50f,
                random = random
            )
        }
    }
    val riverBodies = remember {
        // One screen-scale river. Its body is always present; the current moves inside it.
        listOf(
            RiverBody(progress = 0f, speedScale = .72f, lane = 0f, width = .88f, alpha = .37f, tintIndex = 0, phase = 1.0f)
        )
    }
    val frameTick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(lifecycleOwner, bodies, riverBodies, mood) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val random = Random(System.nanoTime())
            var previousUpdate = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (previousUpdate == 0L) {
                    previousUpdate = frame
                    continue
                }
                if (frame - previousUpdate < GeometryFrameIntervalNanos) continue
                val dt = ((frame - previousUpdate) / 1_000_000_000f).coerceIn(0f, .067f)
                previousUpdate = frame
                val nowMs = frame / 1_000_000L

                bodies.forEach { body ->
                    if (nowMs >= body.nextSwitchAt) body.beginMorph(random, nowMs, mood)
                    if (body.morphProgress < 1f) {
                        body.morphProgress = (body.morphProgress + dt / mood.morphSeconds).coerceAtMost(1f)
                    }

                    if (nowMs >= body.nextDisturbanceAt) {
                        body.targetAccelerationX = random.signed(mood.acceleration)
                        body.targetAccelerationY = random.signed(mood.acceleration)
                        body.targetRotationVelocity = random.signed(if (recoveryMode) .42f else 1.15f)
                        body.targetScale = .94f + random.nextFloat() * .14f
                        body.targetAlpha = .80f + random.nextFloat() * .18f
                        body.nextDisturbanceAt = nowMs + random.nextLong(
                            mood.disturbanceMinMs,
                            mood.disturbanceMaxMs
                        )
                    }

                    val steering = (dt * .52f).coerceAtMost(1f)
                    body.accelerationX += (body.targetAccelerationX - body.accelerationX) * steering
                    body.accelerationY += (body.targetAccelerationY - body.accelerationY) * steering
                    body.velocityX += body.accelerationX * dt
                    body.velocityY += body.accelerationY * dt
                    val damping = exp((-dt * .12f).toDouble()).toFloat()
                    body.velocityX *= damping
                    body.velocityY *= damping
                    val speed = hypot(body.velocityX, body.velocityY)
                    if (speed > mood.maxSpeed) {
                        val ratio = mood.maxSpeed / speed
                        body.velocityX *= ratio
                        body.velocityY *= ratio
                    }

                    val flowX = mood.flowSpeed * body.flowScale
                    val flowY = flowX * .85f
                    val nextX = body.x + (body.velocityX - flowX) * dt
                    val nextY = body.y + (body.velocityY - flowY) * dt

                    // Respawn the instant the shape is fully off the upper-left so the
                    // screen never has a moment without a slowly moving shape.
                    val exitX = body.radius * body.scale
                    val exitY = body.radius * body.aspect * body.scale
                    if (nextX < -exitX || nextY < -exitY) {
                        body.x = 1.04f + random.nextFloat() * .20f
                        body.y = .48f + random.nextFloat() * .10f
                        body.velocityX = -flowX * .6f
                        body.velocityY = -flowY * .6f
                        body.rotation = random.nextFloat() * 360f
                        body.scale = .94f + random.nextFloat() * .14f
                        body.alpha = .80f + random.nextFloat() * .18f
                        body.beginMorph(random, nowMs, mood)
                    } else {
                        body.x = nextX
                        body.y = nextY
                    }

                    body.rotationVelocity += (body.targetRotationVelocity - body.rotationVelocity) * dt * .24f
                    body.rotation = (body.rotation + body.rotationVelocity * dt) % 360f
                    body.scale += (body.targetScale - body.scale) * dt * .19f
                    body.alpha += (body.targetAlpha - body.alpha) * dt * .16f
                }
                riverBodies.forEach { river ->
                    river.progress += (.035f + mood.flowSpeed * river.speedScale) * dt
                    river.phase += dt * .10f
                    if (river.progress > 1.28f) river.progress = -.28f
                }
                frameTick.longValue++
            }
        }
    }

    val vitalityAccent by animateColorAsState(
        targetValue = vitalityColor(vitality),
        animationSpec = tween(1_400),
        label = "vitality-background-color"
    )
    Canvas(modifier) {
        frameTick.longValue
        drawRect(
            Brush.verticalGradient(
                listOf(
                    Color(0xFFF0F3F8),
                    lerp(Color(0xFFF0F3F8), vitalityAccent, .13f),
                    lerp(Color(0xFFF0F3F8), vitalityAccent, .07f)
                )
            )
        )
        val palette = listOf(
            lerp(vitalityAccent, Color.White, .38f),
            lerp(vitalityAccent, Color(0xFFC3B8F8), .46f),
            lerp(vitalityAccent, Color(0xFFF0F3F8), .62f),
            lerp(vitalityAccent, Color(0xFFFE9D7B), .60f)
        )
        riverBodies.forEach { river ->
            drawRiverBody(river, palette[river.tintIndex], mood.opacity)
        }
        bodies.forEach { body ->
            val baseAlpha = body.alpha * mood.opacity
            if (body.morphProgress >= 1f) {
                drawRoundedPolygon(body.sides, body, palette[body.tintIndex], baseAlpha)
            } else {
                val progress = body.morphProgress
                drawRoundedPolygon(body.previousSides, body, palette[body.tintIndex], baseAlpha * (1f - progress))
                drawRoundedPolygon(body.sides, body, palette[body.tintIndex], baseAlpha * progress)
            }
        }
    }
}

private fun vitalityColor(value: Int): Color {
    val stops = listOf(
        Color(0xFF750C26),
        Color(0xFFFE9D7B),
        Color(0xFFC3B8F8),
        Color(0xFF8EB7F5),
        Color(0xFF144BB0)
    )
    val scaled = value.coerceIn(0, 100) / 25f
    val index = scaled.toInt().coerceIn(0, stops.lastIndex - 1)
    return lerp(stops[index], stops[index + 1], scaled - index)
}

@Composable
private fun OceanSunsetBackground(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (previous == 0L || frame - previous >= GeometryFrameIntervalNanos) {
                    previous = frame
                    clock.longValue = frame
                }
            }
        }
    }
    Canvas(modifier) {
        val seconds = clock.longValue / 1_000_000_000f
        val horizon = size.height * .46f
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF7E3D48),
                .28f to Color(0xFFEC6D4A),
                .46f to Color(0xFFFA9C3A),
                .47f to Color(0xFF356D91),
                1f to Color(0xFF173F61)
            )
        )
        val sunCenter = Offset(size.width * .72f, horizon - size.minDimension * .085f)
        val sunRadius = size.minDimension * .092f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF4CC), Color(0xFFFFC368).copy(.88f), Color.Transparent),
                sunCenter,
                sunRadius * 2.4f
            ),
            sunRadius * 2.4f,
            sunCenter
        )
        drawCircle(Color(0xFFFFE1A0).copy(.94f), sunRadius, sunCenter)

        // Broad moving water masses make the lower half read as an ocean rather than
        // a stack of decorative sine lines.
        repeat(7) { layer ->
            val baseY = horizon + layer * size.height * .078f
            val amplitude = size.height * (.008f + layer * .0015f)
            val path = Path()
            val samples = 34
            repeat(samples) { index ->
                val x = size.width * index / (samples - 1f)
                val wave = sin(index * .66f + seconds * (.26f + layer * .025f) + layer * 1.31f) * amplitude
                val secondary = sin(index * .21f - seconds * .17f + layer) * amplitude * .42f
                val y = baseY + wave + secondary
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF6E91A5).copy(alpha = .14f + layer * .018f),
                        Color(0xFF245880).copy(alpha = .10f + layer * .025f)
                    ),
                    startY = baseY - amplitude,
                    endY = size.height
                )
            )
        }

        // Broken sunset reflection follows the waves and drifts at a different rate.
        repeat(8) { row ->
            val y = horizon + size.height * (.025f + row * .052f)
            val width = size.width * (.22f - row * .016f).coerceAtLeast(.06f)
            val centerX = sunCenter.x + sin(seconds * .22f + row * 1.7f) * size.width * .025f
            val reflection = Path().apply {
                moveTo(centerX - width, y)
                quadraticTo(centerX, y + sin(seconds * .31f + row) * 7.dp.toPx(), centerX + width, y)
            }
            drawPath(
                reflection,
                Color(0xFFFFC56F).copy(alpha = (.30f - row * .025f).coerceAtLeast(.07f)),
                style = Stroke((3.2f - row * .22f).coerceAtLeast(1f).dp.toPx())
            )
        }
        drawLine(Color.White.copy(.32f), Offset(0f, horizon), Offset(size.width, horizon), .75.dp.toPx())
    }
}

private fun DrawScope.drawRiverBody(
    river: RiverBody,
    tint: Color,
    moodOpacity: Float
) {
    val startT = -.30f
    val endT = 1.30f
    val stablePhase = 1f
    val firstPoint = riverPoint(startT, river.lane, stablePhase, size.width, size.height)
    val lastPoint = riverPoint(endT, river.lane, stablePhase, size.width, size.height)
    val riverWidth = size.minDimension * river.width
    val halfLane = riverWidth / size.height / 2f
    val outerRiver = riverRibbonPath(
        startT, endT, river.lane, stablePhase, halfLane * 1.10f, samples = 25
    )
    val riverPath = riverRibbonPath(
        startT, endT, river.lane, stablePhase, halfLane, samples = 25
    )
    val alpha = river.alpha * moodOpacity

    // Filled ribbons are substantially cheaper than a near-screen-width stroked path.
    drawPath(
        path = outerRiver,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                tint.copy(alpha = alpha * .26f),
                tint.copy(alpha = alpha * .34f),
                Color.Transparent
            ),
            start = firstPoint,
            end = lastPoint
        )
    )

    drawPath(
        path = riverPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                tint.copy(alpha = alpha * .70f),
                Color.White.copy(alpha = alpha * .42f),
                tint.copy(alpha = alpha * .84f),
                Color.Transparent
            ),
            start = firstPoint,
            end = lastPoint
        )
    )

    // Three soft caustics travel inside the river. Radial pools are much cheaper than
    // dashed full-screen paths and read as light moving over a deep sheet of water.
    repeat(3) { index ->
        val period = endT - startT
        val currentT = startT + ((river.progress - startT + index * period / 3f) % period)
        val currentLane = river.lane + (index - 1f) * .07f
        val currentCenter = riverPoint(
            currentT,
            currentLane,
            stablePhase + index * .08f,
            size.width,
            size.height
        )
        val lightRadius = riverWidth * (.20f + index * .025f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha * .36f),
                    tint.copy(alpha = alpha * .24f),
                    Color.Transparent
                ),
                center = currentCenter,
                radius = lightRadius
            ),
            radius = lightRadius,
            center = currentCenter
        )
    }
}

private fun DrawScope.riverRibbonPath(
    startT: Float,
    endT: Float,
    lane: Float,
    phase: Float,
    halfLane: Float,
    samples: Int
): Path = Path().apply {
    repeat(samples) { sample ->
        val t = startT + (endT - startT) * sample / (samples - 1).coerceAtLeast(1).toFloat()
        val point = riverPoint(t, lane - halfLane, phase, size.width, size.height)
        if (sample == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    for (sample in samples - 1 downTo 0) {
        val t = startT + (endT - startT) * sample / (samples - 1).coerceAtLeast(1).toFloat()
        val point = riverPoint(t, lane + halfLane, phase, size.width, size.height)
        lineTo(point.x, point.y)
    }
    close()
}

private fun riverPoint(
    t: Float,
    lane: Float,
    phase: Float,
    width: Float,
    height: Float
): Offset {
    // A meandering river course from the left-middle to the lower-right. The straight
    // diagonal is perturbed by an offset sine pair (90° out of phase) so the band snakes
    // like a real river instead of running as a rigid line.
    val baseX = -.34f + t * 1.62f
    val baseY = .44f + t * .76f + lane
    val k = 2.6f
    val amp = .16f
    val angle = t * k * (2.0 * PI).toFloat() + phase
    val wobbleX = sin(angle) * amp * .55f
    val wobbleY = cos(angle) * amp
    return Offset((baseX + wobbleX) * width, (baseY + wobbleY) * height)
}

private fun Random.signed(magnitude: Float): Float = nextFloat() * magnitude * 2f - magnitude

private fun DrawScope.drawRoundedPolygon(
    sides: Int,
    body: GeometryBody,
    tint: Color,
    opacity: Float
) {
    val center = Offset(size.width * body.x, size.height * body.y)
    val radius = size.minDimension * body.radius * body.scale
    val alpha = opacity
    val fill = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = alpha * .34f),
            tint.copy(alpha = alpha * .78f),
            tint.copy(alpha = alpha * .90f)
        ),
        start = center - Offset(radius, radius),
        end = center + Offset(radius, radius)
    )
    val edge = Color.White.copy(alpha = alpha * .60f)
    val path = polygonPath(center, radius, body.aspect, sides, body.rotation, rounding = .28f, irregularity = 0f)
    drawPath(path, fill)
    drawPath(path, edge, style = Stroke(1.dp.toPx()))
}

private fun polygonPath(
    center: Offset,
    radius: Float,
    aspect: Float,
    sides: Int,
    rotation: Float,
    rounding: Float,
    irregularity: Float,
    alternate: Float = 1f
): Path = roundedPath(
    polygonPoints(center, radius, aspect, sides, rotation, irregularity, alternate),
    rounding
)

private fun polygonPoints(
    center: Offset,
    radius: Float,
    aspect: Float,
    sides: Int,
    rotation: Float,
    irregularity: Float,
    alternate: Float
): List<Offset> = List(sides) { index ->
    val angle = Math.toRadians((rotation - 90f + index * 360f / sides).toDouble())
    val irregular = 1f + irregularity * (((index * 37) % 9) / 8f - .5f)
    val alternating = if (index % 2 == 1) alternate else 1f
    Offset(
        center.x + cos(angle).toFloat() * radius * irregular * alternating,
        center.y + sin(angle).toFloat() * radius * aspect * irregular * alternating
    )
}

private fun roundedPath(points: List<Offset>, rounding: Float): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val previous = points[(index - 1 + points.size) % points.size]
        val next = points[(index + 1) % points.size]
        val start = point.toward(previous, rounding)
        val end = point.toward(next, rounding)
        if (index == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
        path.quadraticTo(point.x, point.y, end.x, end.y)
    }
    path.close()
    return path
}

private fun Offset.toward(other: Offset, amount: Float) = Offset(
    x + (other.x - x) * amount,
    y + (other.y - y) * amount
)
