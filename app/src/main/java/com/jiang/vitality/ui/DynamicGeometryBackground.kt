package com.jiang.vitality.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
        val sceneOrdinal = rememberSaveable { Random.nextInt(DuskScene.entries.size) }
        val scene = DuskScene.entries[sceneOrdinal]
        DreamyDuskBackground(scene, modifier)
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

private enum class DuskScene {
    OCEAN, CORNFIELD, SUMMER_BEACH, LAKESIDE, LAYERED_HILLS, CLOUD_SEA, ISLAND
}

private data class DuskPalette(
    val zenith: Color,
    val upper: Color,
    val horizon: Color,
    val depth: Color
)

private fun DuskScene.palette(): DuskPalette = when (this) {
    DuskScene.OCEAN -> DuskPalette(Color(0xFF74424D), Color(0xFFEC6D4A), Color(0xFFFA9C3A), Color(0xFF173F61))
    DuskScene.CORNFIELD -> DuskPalette(Color(0xFF5B4050), Color(0xFFD96543), Color(0xFFF5A348), Color(0xFF4D352D))
    DuskScene.SUMMER_BEACH -> DuskPalette(Color(0xFF67506C), Color(0xFFEE8060), Color(0xFFFFBD72), Color(0xFF245880))
    DuskScene.LAKESIDE -> DuskPalette(Color(0xFF4A4968), Color(0xFFCF6C65), Color(0xFFF3AA61), Color(0xFF243F5C))
    DuskScene.LAYERED_HILLS -> DuskPalette(Color(0xFF584662), Color(0xFFD77368), Color(0xFFF0AD73), Color(0xFF3E3D52))
    DuskScene.CLOUD_SEA -> DuskPalette(Color(0xFF5A4A66), Color(0xFFE57B69), Color(0xFFFFC589), Color(0xFF59627A))
    DuskScene.ISLAND -> DuskPalette(Color(0xFF503E55), Color(0xFFD95F4E), Color(0xFFFFA447), Color(0xFF17394F))
}

@Composable
private fun DreamyDuskBackground(scene: DuskScene, modifier: Modifier = Modifier) {
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
        val palette = scene.palette()
        val horizon = size.height * when (scene) {
            DuskScene.CORNFIELD -> .50f
            DuskScene.SUMMER_BEACH -> .43f
            DuskScene.CLOUD_SEA -> .57f
            else -> .46f
        }
        drawRect(
            Brush.verticalGradient(
                0f to palette.zenith,
                .30f to palette.upper,
                .52f to palette.horizon,
                1f to palette.depth
            )
        )
        val sunCenter = Offset(
            size.width * when (scene) {
                DuskScene.CORNFIELD -> .27f
                DuskScene.SUMMER_BEACH -> .68f
                DuskScene.LAKESIDE -> .35f
                DuskScene.LAYERED_HILLS -> .73f
                DuskScene.CLOUD_SEA -> .58f
                DuskScene.ISLAND -> .31f
                else -> .72f
            },
            horizon - size.minDimension * .085f
        )
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

        when (scene) {
            DuskScene.OCEAN -> drawDuskWater(horizon, sunCenter, seconds, Color(0xFF245880), 1f)
            DuskScene.SUMMER_BEACH -> drawSummerBeach(horizon, sunCenter, seconds)
            DuskScene.CORNFIELD -> drawCornfield(horizon, seconds)
            DuskScene.LAKESIDE -> drawLakeside(horizon, sunCenter, seconds)
            DuskScene.LAYERED_HILLS -> drawLayeredHills(horizon, seconds)
            DuskScene.CLOUD_SEA -> drawCloudSea(horizon, seconds)
            DuskScene.ISLAND -> drawIsland(horizon, sunCenter, seconds)
        }
    }
}

private fun DrawScope.drawDuskWater(horizon: Float, sunCenter: Offset, seconds: Float, water: Color, strength: Float) {
    repeat(7) { layer ->
        val baseY = horizon + layer * size.height * .078f
        val amplitude = size.height * (.008f + layer * .0015f)
        val path = Path()
        repeat(34) { index ->
            val x = size.width * index / 33f
            val y = baseY + sin(index * .66f + seconds * (.26f + layer * .025f) + layer * 1.31f) * amplitude +
                sin(index * .21f - seconds * .17f + layer) * amplitude * .42f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(size.width, size.height); path.lineTo(0f, size.height); path.close()
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF93ADBA).copy(.10f * strength + layer * .012f), water.copy(.12f * strength + layer * .022f)), baseY - amplitude, size.height))
    }
    repeat(8) { row ->
        val y = horizon + size.height * (.025f + row * .052f)
        val width = size.width * (.22f - row * .016f).coerceAtLeast(.06f)
        val centerX = sunCenter.x + sin(seconds * .22f + row * 1.7f) * size.width * .025f
        val reflection = Path().apply {
            moveTo(centerX - width, y)
            quadraticTo(centerX, y + sin(seconds * .31f + row) * 7.dp.toPx(), centerX + width, y)
        }
        drawPath(reflection, Color(0xFFFFC56F).copy((.30f - row * .025f).coerceAtLeast(.07f) * strength), style = Stroke((3.2f - row * .22f).coerceAtLeast(1f).dp.toPx()))
    }
    drawLine(Color.White.copy(.28f * strength), Offset(0f, horizon), Offset(size.width, horizon), .75.dp.toPx())
}

private fun DrawScope.drawSummerBeach(horizon: Float, sunCenter: Offset, seconds: Float) {
    drawDuskWater(horizon, sunCenter, seconds, Color(0xFF2E7192), .85f)
    val shore = size.height * .73f
    val sand = Path().apply {
        moveTo(0f, shore + sin(seconds * .18f) * 5.dp.toPx())
        quadraticTo(size.width * .48f, shore - size.height * .035f, size.width, shore + size.height * .025f)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(sand, Brush.verticalGradient(listOf(Color(0xFFE5AF77).copy(.88f), Color(0xFF9B715E).copy(.92f)), shore, size.height))
    repeat(3) { row ->
        val y = shore - row * size.height * .055f + sin(seconds * .24f + row) * 5.dp.toPx()
        val foam = Path().apply {
            moveTo(-size.width * .08f, y)
            cubicTo(size.width * .22f, y - 12.dp.toPx(), size.width * .64f, y + 15.dp.toPx(), size.width * 1.08f, y - 3.dp.toPx())
        }
        drawPath(foam, Color.White.copy(.22f - row * .04f), style = Stroke((2.3f - row * .35f).dp.toPx()))
    }
}

private fun DrawScope.drawCornfield(horizon: Float, seconds: Float) {
    val hill = Path().apply {
        moveTo(0f, horizon + size.height * .05f)
        cubicTo(size.width * .22f, horizon - size.height * .04f, size.width * .68f, horizon + size.height * .02f, size.width, horizon - size.height * .01f)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF85603D).copy(.72f), Color(0xFF3D332C)), horizon, size.height))
    repeat(30) { index ->
        val x = size.width * (index + .35f) / 30f
        val depth = .55f + (index % 6) / 10f
        val base = size.height * (1.02f - (index % 5) * .012f)
        val height = size.height * (.17f + depth * .12f)
        val sway = sin(seconds * .30f + index * .71f) * 5.dp.toPx() * depth
        val top = Offset(x + sway, base - height)
        val stalk = Color(0xFF4A3B28).copy(.44f + depth * .30f)
        drawLine(stalk, Offset(x, base), top, (1.1f + depth).dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawOval(Color(0xFFCCA55B).copy(.55f), Offset(top.x - 2.6.dp.toPx(), top.y), androidx.compose.ui.geometry.Size(5.2.dp.toPx(), 13.dp.toPx()))
        drawLine(stalk, Offset(x + sway * .45f, base - height * .50f), Offset(x + sway * .45f - 8.dp.toPx(), base - height * .61f), 1.2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(stalk, Offset(x + sway * .62f, base - height * .66f), Offset(x + sway * .62f + 7.dp.toPx(), base - height * .75f), 1.2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

private fun DrawScope.drawLakeside(horizon: Float, sunCenter: Offset, seconds: Float) {
    val mountains = Path().apply {
        moveTo(0f, horizon)
        lineTo(size.width * .16f, horizon - size.height * .12f); lineTo(size.width * .34f, horizon - size.height * .025f)
        lineTo(size.width * .55f, horizon - size.height * .17f); lineTo(size.width * .76f, horizon - size.height * .035f)
        lineTo(size.width, horizon - size.height * .10f); lineTo(size.width, horizon); close()
    }
    drawPath(mountains, Color(0xFF39455A).copy(.60f))
    drawDuskWater(horizon, sunCenter, seconds * .72f, Color(0xFF294F68), .72f)
}

private fun DrawScope.drawLayeredHills(horizon: Float, seconds: Float) {
    val colors = listOf(Color(0xFF9C6A70).copy(.58f), Color(0xFF625365).copy(.76f), Color(0xFF343848).copy(.92f))
    repeat(3) { layer ->
        val y = horizon + size.height * (.02f + layer * .13f)
        val shift = sin(seconds * .06f + layer) * size.width * .012f
        val path = Path().apply {
            moveTo(-20.dp.toPx(), y)
            cubicTo(size.width * .20f + shift, y - size.height * (.12f - layer * .015f), size.width * .42f, y + size.height * .04f, size.width * .62f + shift, y - size.height * .08f)
            quadraticTo(size.width * .85f, y - size.height * .14f, size.width + 20.dp.toPx(), y)
            lineTo(size.width + 20.dp.toPx(), size.height); lineTo(-20.dp.toPx(), size.height); close()
        }
        drawPath(path, colors[layer])
    }
}

private fun DrawScope.drawCloudSea(horizon: Float, seconds: Float) {
    drawLayeredHills(horizon + size.height * .10f, seconds * .35f)
    repeat(22) { index ->
        val row = index / 8
        val radius = size.minDimension * (.09f + (index % 4) * .012f)
        val x = size.width * ((index % 8) / 7f) + sin(seconds * .045f + index) * 10.dp.toPx()
        val y = horizon + row * radius * .75f + sin(seconds * .08f + index * .4f) * 4.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0C3).copy(.32f), Color(0xFFD6C2C7).copy(.13f), Color.Transparent), Offset(x, y), radius), radius, Offset(x, y))
    }
}

private fun DrawScope.drawIsland(horizon: Float, sunCenter: Offset, seconds: Float) {
    drawDuskWater(horizon, sunCenter, seconds, Color(0xFF183F56), .84f)
    val islandY = size.height * .70f
    val island = Path().apply {
        moveTo(size.width * .45f, islandY)
        quadraticTo(size.width * .64f, islandY - size.height * .07f, size.width * .84f, islandY)
        quadraticTo(size.width * .65f, islandY + size.height * .035f, size.width * .45f, islandY)
        close()
    }
    drawPath(island, Color(0xFF29352F).copy(.94f))
    val trunkBase = Offset(size.width * .67f, islandY - size.height * .025f)
    val trunkTop = Offset(size.width * .64f + sin(seconds * .16f) * 2.dp.toPx(), islandY - size.height * .17f)
    drawLine(Color(0xFF2B302D), trunkBase, trunkTop, 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
    repeat(6) { leaf ->
        val angle = leaf * PI.toFloat() / 3f + sin(seconds * .13f) * .04f
        val end = Offset(trunkTop.x + cos(angle) * 32.dp.toPx(), trunkTop.y + sin(angle) * 14.dp.toPx())
        drawLine(Color(0xFF26362E).copy(.92f), trunkTop, end, 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
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
