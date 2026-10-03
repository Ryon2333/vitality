package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.Reading
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class MemoryRange(val title: String, val days: Long, val limit: Int) {
    WEEK("周", 7, 28), MONTH("月", 31, 64), YEAR("年", 366, 120)
}

private data class MemoryStar(
    val reading: Reading,
    val date: LocalDate,
    val targetX: Float,
    val targetY: Float,
    val sourceX: Float,
    val sourceY: Float,
    val radiusDp: Float,
    val phase: Float
)

@Composable
fun MemoryConstellationScreen(
    readings: List<Reading>,
    onBack: () -> Unit,
    onOpenDate: (LocalDate) -> Unit
) {
    BackHandler(onBack = onBack)
    var range by remember { mutableStateOf(MemoryRange.WEEK) }
    val assembly = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val palette = LocalVitalityColors.current
    val stars = remember(readings, range) { createMemoryStars(readings, range, zone) }
    val thumbnails = remember(stars) {
        stars.associate { star ->
            star.reading.time to star.reading.photoPaths.firstOrNull()?.let { decodeConstellationThumbnail(it) }
        }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        assembly.animateTo(1f, spring(dampingRatio = .74f, stiffness = 170f))
    }
    LaunchedEffect(Unit) {
        val random = Random(0x4A49414E)
        while (true) {
            rotation.animateTo(
                rotation.value + random.nextFloat() * 5f + 3f,
                tween(random.nextInt(7_500, 12_500), easing = LinearEasing)
            )
        }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.size(48.dp).clickable(onClick = onBack), shape = CircleShape) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text("‹", color = Ink, fontSize = 32.sp)
                }
            }
            GlassPageHeader("我的集合", Modifier.weight(1f), "每颗星，都是你认真生活过的一刻。")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MemoryRange.entries.forEach { item ->
                GlassActionButton(
                    text = if (item == range) "● ${item.title}" else item.title,
                    onClick = {
                        if (item != range) scope.launch {
                            assembly.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                            range = item
                            assembly.snapTo(0f)
                            assembly.animateTo(1f, spring(dampingRatio = .70f, stiffness = 155f))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth().weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(38.dp), elevation = 12.dp) {
            Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(stars, canvasSize) {
                            detectTapGestures { tap ->
                                if (assembly.value < .72f || canvasSize == IntSize.Zero) return@detectTapGestures
                                val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                val hit = stars.minByOrNull { star ->
                                    (constellationPosition(star, size, assembly.value, rotation.value) - tap).getDistance()
                                }
                                if (hit != null) {
                                    val center = constellationPosition(hit, size, assembly.value, rotation.value)
                                    val hitRadius = hit.radiusDp.dp.toPx() + 14.dp.toPx()
                                    if ((center - tap).getDistance() <= hitRadius) onOpenDate(hit.date)
                                }
                            }
                        }
                ) {
                    drawConstellation(stars, thumbnails, assembly.value, rotation.value, palette.accent, palette.ink)
                }
                if (stars.isEmpty()) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text("这段时间还没有星星", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        androidx.compose.material3.Text("记录一次状态，它就会从远处来到这里。", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun createMemoryStars(readings: List<Reading>, range: MemoryRange, zone: ZoneId): List<MemoryStar> {
    val firstDay = LocalDate.now().minusDays(range.days - 1)
    val selected = readings.asReversed().filter {
        !Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate().isBefore(firstDay)
    }.take(range.limit)
    val total = selected.size.coerceAtLeast(1)
    val golden = PI * (3.0 - sqrt(5.0))
    return selected.mapIndexed { index, reading ->
        val seed = (reading.time xor (reading.value.toLong() shl 17)).toInt()
        val random = Random(seed)
        val ring = sqrt((index + 1f) / (total + 1f))
        val angle = index * golden + random.nextFloat() * .42f
        val targetX = .5f + cos(angle).toFloat() * .42f * ring
        val targetY = .51f + sin(angle).toFloat() * .39f * ring
        val edge = (seed and Int.MAX_VALUE) % 4
        val along = .08f + random.nextFloat() * .84f
        val (sourceX, sourceY) = when (edge) {
            0 -> -.16f to along
            1 -> 1.16f to along
            2 -> along to -.14f
            else -> along to 1.14f
        }
        MemoryStar(
            reading = reading,
            date = Instant.ofEpochMilli(reading.time).atZone(zone).toLocalDate(),
            targetX = targetX,
            targetY = targetY,
            sourceX = sourceX,
            sourceY = sourceY,
            radiusDp = when {
                reading.photoPaths.isNotEmpty() -> 46f
                reading.note.isNotBlank() -> 39f
                else -> 32f
            },
            phase = random.nextFloat() * (2f * PI.toFloat())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellation(
    stars: List<MemoryStar>,
    thumbnails: Map<Long, ImageBitmap?>,
    assembly: Float,
    rotation: Float,
    accent: Color,
    ink: Color
) {
    val p = assembly.coerceIn(0f, 1f)
    val alpha = p.pow(1.45f)
    if (alpha <= .01f) return
    val centers = stars.map { constellationPosition(it, size, p, rotation) }
    centers.zipWithNext().forEachIndexed { index, pair ->
        drawLine(
            color = accent.copy(alpha = (.08f + (index % 3) * .025f) * alpha),
            start = pair.first,
            end = pair.second,
            strokeWidth = 1.dp.toPx()
        )
    }
    if (centers.size > 5) {
        for (index in centers.indices step 4) {
            drawLine(
                color = Color.White.copy(alpha = .16f * alpha),
                start = centers[index],
                end = centers[(index + 3) % centers.size],
                strokeWidth = .75.dp.toPx()
            )
        }
    }

    stars.forEachIndexed { index, star ->
        val center = centers[index]
        val radius = star.radiusDp.dp.toPx() * (.72f + .28f * p)
        val image = thumbnails[star.reading.time]
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(.72f * alpha), accent.copy(.22f * alpha), Color(0xFFB9A7E8).copy(.16f * alpha)),
                center = center,
                radius = radius * 1.45f
            ),
            radius = radius * 1.22f,
            center = center
        )
        if (image != null) {
            val clip = Path().apply { addOval(Rect(center - Offset(radius, radius), center + Offset(radius, radius))) }
            clipPath(clip) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
                    dstSize = IntSize((radius * 2).toInt(), (radius * 2).toInt()),
                    alpha = alpha
                )
                drawCircle(Color(0xFF10213D).copy(alpha = .28f * alpha), radius, center)
            }
        } else {
            drawCircle(Color.White.copy(alpha = .24f * alpha), radius, center)
        }
        drawCircle(Color.White.copy(alpha = .88f * alpha), radius, center, style = Stroke(1.15.dp.toPx()))
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val mainColor = if (image != null) android.graphics.Color.WHITE else ink.toArgb()
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mainColor
                textAlign = Paint.Align.CENTER
                textSize = (if (radius > 40.dp.toPx()) 11.sp else 9.sp).toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                this.alpha = (255 * alpha).toInt()
            }
            native.drawText(star.date.format(DateTimeFormatter.ofPattern("M.d")), center.x, center.y - 2.dp.toPx(), datePaint)
            if (radius >= 36.dp.toPx()) {
                val note = star.reading.note.ifBlank { "状态 ${star.reading.value}" }.replace('\n', ' ').take(8)
                val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = mainColor
                    textAlign = Paint.Align.CENTER
                    textSize = 8.sp.toPx()
                    this.alpha = (210 * alpha).toInt()
                }
                native.drawText(note, center.x, center.y + 13.dp.toPx(), notePaint)
            }
        }
    }
}

private fun constellationPosition(star: MemoryStar, size: Size, assembly: Float, rotationDegrees: Float): Offset {
    val p = 1f - (1f - assembly.coerceIn(0f, 1f)).pow(3)
    val raw = Offset(
        (star.sourceX + (star.targetX - star.sourceX) * p) * size.width,
        (star.sourceY + (star.targetY - star.sourceY) * p) * size.height
    )
    val center = Offset(size.width / 2f, size.height / 2f)
    val angle = Math.toRadians(rotationDegrees + sin(star.phase.toDouble()) * .7)
    val delta = raw - center
    return center + Offset(
        (delta.x * cos(angle) - delta.y * sin(angle)).toFloat(),
        (delta.x * sin(angle) + delta.y * cos(angle)).toFloat()
    )
}

private fun decodeConstellationThumbnail(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
