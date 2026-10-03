package com.jiang.vitality.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jiang.vitality.ui.backdrop.LocalBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class BubblePhase { Idle, Selecting, Result }

private data class RestBubbleSpec(
    val id: Int,
    val text: String,
    val size: Dp,
    val nx: Float,
    val ny: Float,
    val delay: Int,
    val tint: Color
)

private val BubbleTints = listOf(
    IceBlue,
    LavenderBlue,
    Color(0xFF6FC3DC),
    GrayBlue
)

private fun makeSpecs(rests: List<String>): List<RestBubbleSpec> = rests.mapIndexed { i, text ->
    val size = when (i % 4) {
        0 -> 104.dp
        1 -> 88.dp
        2 -> 116.dp
        else -> 96.dp
    }
    val nx = (.5f + (i - (rests.size - 1) / 2f) * .16f).coerceIn(.07f, .93f)
    val ny = (.42f + (i % 3) * .16f + (if (i % 2 == 0) .03f else -.03f)).coerceIn(.30f, .80f)
    RestBubbleSpec(
        id = i,
        text = text,
        size = size,
        nx = nx,
        ny = ny,
        delay = 90 + i * 110,
        tint = BubbleTints[i % BubbleTints.size]
    )
}

/**
 * "气泡冒出" rest-method picker. A field of soft glass bubbles rises from the bottom,
 * breathes gently, and — on request or on tap — one bubble stays (centred, enlarged)
 * while the others burst like soap bubbles.
 */
@Composable
fun RestBubblePicker(rests: List<String>, modifier: Modifier = Modifier) {
    // Keyed on the list *content* so a 60s background refresh does not reset the picker,
    // but editing the options does.
    val restsKey = rests.joinToString("|")
    val specs = remember(restsKey) { makeSpecs(rests) }
    var phase by remember(restsKey) { mutableStateOf(BubblePhase.Idle) }
    var selectedIndex by remember(restsKey) { mutableStateOf<Int?>(null) }
    var round by remember(restsKey) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun startSelection() {
        if (phase != BubblePhase.Idle || rests.isEmpty()) return
        phase = BubblePhase.Selecting
        scope.launch {
            delay(1300)
            selectedIndex = Random.nextInt(rests.size)
            phase = BubblePhase.Result
        }
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        key(round) {
            BubbleField(
                specs = specs,
                phase = phase,
                selectedIndex = selectedIndex,
                onBubbleTap = { index ->
                    if (phase == BubblePhase.Idle) {
                        selectedIndex = index
                        phase = BubblePhase.Result
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
        }

        when (phase) {
            BubblePhase.Result -> {
                val name = specs.getOrNull(selectedIndex ?: -1)?.text ?: rests.firstOrNull().orEmpty()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("今天适合", color = Muted, fontSize = 13.sp)
                    Text(
                        name,
                        color = Ink,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    GlassButton(
                        onClick = {
                            selectedIndex = null
                            phase = BubblePhase.Idle
                            round++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("再选一次") }
                }
            }
            else -> {
                Text(
                    if (phase == BubblePhase.Idle) "点一下气泡，或开始选择" else "正在挑选…",
                    color = Muted,
                    fontSize = 13.sp
                )
                GlassButton(
                    onClick = ::startSelection,
                    enabled = phase == BubblePhase.Idle && rests.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (phase == BubblePhase.Selecting) "挑选中…" else "开始选择") }
            }
        }
    }
}

@Composable
private fun BubbleField(
    specs: List<RestBubbleSpec>,
    phase: BubblePhase,
    selectedIndex: Int?,
    onBubbleTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        specs.forEachIndexed { index, spec ->
            Bubble(
                spec = spec,
                fieldWidth = width,
                fieldHeight = height,
                phase = phase,
                isSelected = index == selectedIndex,
                onTap = { onBubbleTap(index) }
            )
        }
    }
}

@Composable
private fun Bubble(
    spec: RestBubbleSpec,
    fieldWidth: Float,
    fieldHeight: Float,
    phase: BubblePhase,
    isSelected: Boolean,
    onTap: () -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { spec.size.toPx() }
    val baseX = spec.nx * fieldWidth - sizePx / 2f
    val baseY = spec.ny * fieldHeight - sizePx / 2f
    val centerX = fieldWidth / 2f - sizePx / 2f
    val centerY = fieldHeight / 2f - sizePx / 2f

    val entrance = remember { Animatable(0f) }
    val float = remember { Animatable(0f) }
    val breathe = remember { Animatable(1f) }
    val pulse = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val center = remember { Animatable(0f) }

    // Emerge from below the field with an elastic pop.
    LaunchedEffect(spec.id) {
        delay(spec.delay.toLong())
        entrance.animateTo(1f, spring(dampingRatio = .5f, stiffness = 240f))
    }

    // Gentle float + breathe while idle or selecting.
    LaunchedEffect(spec.id, phase) {
        if (phase == BubblePhase.Result) return@LaunchedEffect
        val random = Random(spec.id * 31L + 7L)
        while (isActive) {
            coroutineScope {
                launch {
                    float.animateTo(
                        random.nextFloat() * 18f - 9f,
                        tween(1900 + random.nextInt(900), easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    breathe.animateTo(
                        .97f + random.nextFloat() * .06f,
                        tween(1700 + random.nextInt(800), easing = FastOutSlowInEasing)
                    )
                }
            }
            delay(1200 + random.nextLong(900))
        }
    }

    // Excitement pulse while a random pick is deciding.
    LaunchedEffect(phase, spec.id) {
        if (phase != BubblePhase.Selecting) return@LaunchedEffect
        delay(spec.id * 24L)
        repeat(2) {
            pulse.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            pulse.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
        }
    }

    // Resolution: the chosen bubble centres and grows; the rest pop like soap bubbles.
    LaunchedEffect(phase, isSelected) {
        if (phase != BubblePhase.Result) return@LaunchedEffect
        if (isSelected) {
            center.animateTo(1f, spring(dampingRatio = .58f, stiffness = 170f))
        } else {
            delay(spec.id * 16L)
            pop.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    BubbleItem(
        text = spec.text,
        size = spec.size,
        tint = spec.tint,
        popProgress = pop.value,
        modifier = Modifier
            .graphicsLayer {
                val e = entrance.value
                val f = float.value
                val b = breathe.value
                val p = pulse.value
                val popV = pop.value
                val c = center.value
                translationX = baseX + (centerX - baseX) * c
                translationY = baseY + (fieldHeight - baseY) * (1f - e) + f * e + (centerY - baseY) * c
                scaleX = e * b * (1f + p * .10f) * (1f + c * .34f) * (1f - popV * .06f)
                scaleY = scaleX
                alpha = e * (1f - popV)
            }
            .zIndex(if (isSelected) 3f else 1f)
            .pointerInput(phase) {
                detectTapGestures(onTap = { if (phase == BubblePhase.Idle) onTap() })
            }
    )
}

@Composable
private fun BubbleItem(
    text: String,
    size: Dp,
    tint: Color,
    popProgress: Float,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalBackdrop.current
    val usesBackdrop = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val glassModifier = if (usesBackdrop) {
        modifier
            .size(size)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        depthEffect = true,
                        // Several bubbles can be visible simultaneously. A single
                        // refracted sample keeps the glass shape without 7-band cost.
                        chromaticAberration = false
                    )
                },
                highlight = {
                    Highlight.Default.copy(
                        width = 1.1.dp,
                        style = HighlightStyle.Default(
                            color = Color.White.copy(alpha = .92f),
                            angle = 42f,
                            falloff = 1.15f
                        )
                    )
                },
                shadow = {
                    Shadow(radius = 7.dp, color = tint.copy(alpha = .10f))
                },
                innerShadow = {
                    InnerShadow(radius = 5.dp, color = Color.White.copy(alpha = .30f))
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = .035f * (1f - popProgress)))
                    drawRect(tint.copy(alpha = .045f * (1f - popProgress)))
                }
            )
    } else {
        modifier.size(size)
    }

    Box(glassModifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            val bodyAlpha = (1f - popProgress).coerceIn(0f, 1f)

            // Soft outer glow.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = .16f * bodyAlpha), Color.Transparent),
                    center = c,
                    radius = r * 1.6f
                ),
                radius = r * 1.6f,
                center = c
            )

            if (!usesBackdrop) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = .26f * bodyAlpha),
                            Color.White.copy(alpha = .08f * bodyAlpha),
                            tint.copy(alpha = .08f * bodyAlpha),
                            tint.copy(alpha = .16f * bodyAlpha)
                        ),
                        center = c - Offset(r * .40f, r * .40f),
                        radius = r * 1.8f
                    ),
                    radius = r,
                    center = c
                )
            }

            // Bright soap-bubble rim.
            drawCircle(
                color = Color.White.copy(alpha = (if (usesBackdrop) .42f else .62f) * bodyAlpha),
                radius = r,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )

            // Specular highlight dot.
            drawCircle(
                color = Color.White.copy(alpha = .88f * bodyAlpha),
                radius = r * .15f,
                center = c - Offset(r * .38f, r * .42f)
            )

            // Soap-pop ring while bursting.
            if (popProgress > 0f && popProgress < 1f) {
                val ringR = r * (1f + popProgress * .9f)
                drawCircle(
                    color = Color.White.copy(alpha = (1f - popProgress) * .85f),
                    radius = ringR,
                    center = c,
                    style = Stroke(width = (3.dp.toPx() * (1f - popProgress)).coerceAtLeast(.5f))
                )
                drawCircle(
                    color = tint.copy(alpha = (1f - popProgress) * .5f),
                    radius = ringR * .80f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
        if (popProgress < .9f) {
            Text(
                text,
                color = Ink,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}
