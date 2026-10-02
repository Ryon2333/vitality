package com.jiang.vitality.ui.backdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF0088FF),
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color.White.copy(alpha = 0.075f)
        else Color(0xFF161618).copy(alpha = 0.10f)

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var swipeDistance by remember { mutableFloatStateOf(0f) }
        var swipeStartIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    onTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        // 状态联动：外部 Tab 切换时以物理弹簧滑向目标
        val targetTabIndex = selectedTabIndex()
        LaunchedEffect(targetTabIndex) {
            if (dampedDragAnimation.targetValue.fastRoundToInt() != targetTabIndex) {
                dampedDragAnimation.animateToValue(targetTabIndex.toFloat())
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 1. 底层：苹果液态玻璃外壳胶囊（动态背景模糊 + 球面透镜 + 45° 晶莹高光描边）
        Box(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(5.dp.toPx())
                        lens(
                            refractionHeight = 20.dp.toPx(),
                            refractionAmount = 28.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = if (isLightTheme) 0.96f else 0.56f),
                                angle = 45f,
                                falloff = 1f
                            ),
                            width = 1.25.dp
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 12.dp,
                            color = Color.Black.copy(alpha = if (isLightTheme) 0.05f else 0.15f)
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 5.dp,
                            color = Color.White.copy(alpha = if (isLightTheme) 0.30f else 0.14f)
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .fillMaxWidth()
                .height(64.dp)
        )

        // 2. 中层：悬浮 7 波段光谱色散液态玻璃药丸透镜（在 Tab 图标下方滑动，折射底图）
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(
                            refractionHeight = 14.dp.toPx() + 4.dp.toPx() * progress,
                            refractionAmount = 22.dp.toPx() + 7.dp.toPx() * progress,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = lerp(0.85f, 1f, progress)),
                                angle = 45f,
                                falloff = 1.2f
                            ),
                            width = 1.dp
                        )
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(
                            radius = 6.dp,
                            color = Color.Black.copy(alpha = if (isLightTheme) 0.06f else 0.16f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp,
                            color = Color.White.copy(alpha = lerp(0.35f, 0.60f, progress))
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
                        val glassAlpha = if (isLightTheme) {
                            lerp(0.055f, 0.11f, progress)
                        } else {
                            lerp(0.045f, 0.10f, progress)
                        }
                        drawRect(Color.White.copy(alpha = glassAlpha))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        // 3. 顶层：清晰锐利的 Tab 标签内容层（文字与图标 100% 矢量清晰，零模糊、零重影）
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.15f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .then(interactiveHighlight.modifier)
                    .pointerInput(tabsCount, isLtr) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                swipeDistance = 0f
                                swipeStartIndex = selectedTabIndex()
                                dampedDragAnimation.press()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                swipeDistance += dragAmount
                                val direction = if (isLtr) 1f else -1f
                                dampedDragAnimation.updateValue(
                                    (swipeStartIndex + swipeDistance / tabWidth * direction)
                                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                                )
                                animationScope.launch {
                                    offsetAnimation.snapTo(
                                        (offsetAnimation.value + dragAmount)
                                            .fastCoerceIn(-tabWidth, tabWidth)
                                    )
                                }
                            },
                            onDragEnd = {
                                val delta = when {
                                    swipeDistance > tabWidth * .18f -> if (isLtr) 1 else -1
                                    swipeDistance < -tabWidth * .18f -> if (isLtr) -1 else 1
                                    else -> 0
                                }
                                val target = (swipeStartIndex + delta)
                                    .fastCoerceIn(0, tabsCount - 1)
                                dampedDragAnimation.animateToValue(target.toFloat())
                                dampedDragAnimation.release()
                                onTabSelected(target)
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(.72f, 420f, .5f))
                                }
                            },
                            onDragCancel = {
                                dampedDragAnimation.release()
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(.72f, 420f, .5f))
                                }
                            }
                        )
                    }
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
