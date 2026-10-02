package com.jiang.vitality.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

// A full display-frame cadence keeps motion continuous.
private const val GeometryFrameIntervalNanos = 16_000_000L

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
        listOf(
            RiverBody(progress = -.40f, speedScale = 1.00f, lane = 0f,     width = .30f, alpha = .12f, tintIndex = 3, phase = 1.2f),
            RiverBody(progress = -.18f, speedScale = .95f,  lane = 0f,     width = .22f, alpha = .26f, tintIndex = 0, phase = 2.4f),
            RiverBody(progress = .05f,  speedScale = .90f,  lane = -.025f, width = .13f, alpha = .28f, tintIndex = 1, phase = 4.0f),
            RiverBody(progress = .22f,  speedScale = 1.05f, lane = .025f,  width = .08f, alpha = .32f, tintIndex = 2, phase = 5.3f),
            RiverBody(progress = -.05f, speedScale = .85f,  lane = .010f,  width = .045f, alpha = .42f, tintIndex = 0, phase = 0.6f)
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
                    river.progress += mood.flowSpeed * river.speedScale * dt
                    river.phase += dt * .20f
                    if (river.progress > 1.28f) river.progress = -.28f
                }
                frameTick.longValue++
            }
        }
    }

    Canvas(modifier) {
        frameTick.longValue
        drawRect(if (recoveryMode) Color(0xFFFFF0DE) else Color(0xFFF0F3F8))
        val palette = if (recoveryMode) RecoveryTints else DayTints
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

private fun DrawScope.drawRiverBody(
    river: RiverBody,
    tint: Color,
    moodOpacity: Float
) {
    val segmentHalfLength = .22f
    val startT = river.progress - segmentHalfLength
    val endT = river.progress + segmentHalfLength
    val path = Path()
    var firstPoint = Offset.Zero
    var lastPoint = Offset.Zero
    repeat(17) { sample ->
        val t = startT + (endT - startT) * sample / 16f
        val point = riverPoint(t, river.lane, river.phase, size.width, size.height)
        if (sample == 0) {
            path.moveTo(point.x, point.y)
            firstPoint = point
        } else {
            path.lineTo(point.x, point.y)
        }
        lastPoint = point
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                tint.copy(alpha = river.alpha * moodOpacity),
                Color.White.copy(alpha = river.alpha * moodOpacity * .58f),
                tint.copy(alpha = river.alpha * moodOpacity * .82f),
                Color.Transparent
            ),
            start = firstPoint,
            end = lastPoint
        ),
        style = Stroke(
            width = size.minDimension * river.width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
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
