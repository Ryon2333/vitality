package com.jiang.vitality.ui.backdrop

import androidx.compose.animation.core.Animatable
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val settledPosition = remember { Animatable(selectedTabIndex().toFloat()) }
        val pressProgress = remember { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableFloatStateOf(selectedTabIndex().toFloat()) }
        val indicatorPosition = { if (dragging) dragPosition else settledPosition.value }

        // 状态联动：外部 Tab 切换时以物理弹簧滑向目标
        val targetTabIndex = selectedTabIndex()
        LaunchedEffect(targetTabIndex) {
            if (!dragging && settledPosition.targetValue != targetTabIndex.toFloat()) {
                settledPosition.animateTo(targetTabIndex.toFloat(), spring(.78f, 520f, .001f))
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (indicatorPosition() + 0.5f) * tabWidth
                        else size.width - (indicatorPosition() + 0.5f) * tabWidth,
                        size.height / 2f
                    )
                }
            )
        }
        // Layer 3 captures the complete navigation surface, including its icons
        // and labels. The selected lens is then drawn last as layer 4 so those
        // pixels are genuinely refracted instead of staying sharp above the lens.
        val navigationContentBackdrop = rememberLayerBackdrop()
        val indicatorBackdrop = rememberCombinedBackdrop(backdrop, navigationContentBackdrop)

        Box(
            Modifier
                .layerBackdrop(navigationContentBackdrop)
                .fillMaxWidth()
                .height(64.dp)
        ) {
            // Layer 3a: highly transparent liquid-glass navigation shell.
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { androidx.compose.foundation.shape.RoundedCornerShape(50) },
                        effects = {
                            vibrancy()
                            // Blur only the page behind the navigation shell. Icons,
                            // labels and the selected lens are rendered afterwards.
                            blur(3.dp.toPx())
                            lens(
                                refractionHeight = 8.dp.toPx(),
                                refractionAmount = 9.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = false
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
                            val progress = pressProgress.value
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .fillMaxWidth()
                    .height(64.dp)
            )

            // Layer 3b: icons and labels are part of the sampled navigation layer.
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.10f, pressProgress.value)
                }
            ) {
                Row(
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .pointerInput(tabsCount, isLtr) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragPosition = selectedTabIndex().toFloat()
                                    animationScope.launch {
                                        settledPosition.stop()
                                        pressProgress.animateTo(1f, spring(.7f, 700f, .001f))
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val direction = if (isLtr) 1f else -1f
                                    dragPosition = (dragPosition + dragAmount / tabWidth * direction)
                                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                                },
                                onDragEnd = {
                                    val releasedPosition = dragPosition
                                    val target = dragPosition.roundToInt().fastCoerceIn(0, tabsCount - 1)
                                    onTabSelected(target)
                                    animationScope.launch {
                                        // Keep rendering the direct pointer position until Animatable has
                                        // taken it over. Otherwise the pill flashes back to its old tab for
                                        // one frame when the finger is released.
                                        settledPosition.snapTo(releasedPosition)
                                        dragging = false
                                        launch { settledPosition.animateTo(target.toFloat(), spring(.72f, 620f, .001f)) }
                                        launch { pressProgress.animateTo(0f, spring(.82f, 520f, .001f)) }
                                    }
                                },
                                onDragCancel = {
                                    val releasedPosition = dragPosition
                                    val target = selectedTabIndex().fastCoerceIn(0, tabsCount - 1)
                                    animationScope.launch {
                                        settledPosition.snapTo(releasedPosition)
                                        dragging = false
                                        launch { settledPosition.animateTo(target.toFloat(), spring(.82f, 520f, .001f)) }
                                        launch { pressProgress.animateTo(0f, spring(.82f, 520f, .001f)) }
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

        // Layer 4: the current-tab lens is painted after layer 3 and therefore
        // refracts the navigation icon/label as well as the page and wallpaper.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) indicatorPosition() * tabWidth
                    else size.width - (indicatorPosition() + 1f) * tabWidth
                }
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(50) },
                    effects = {
                        val progress = pressProgress.value
                        vibrancy()
                        lens(
                            refractionHeight = 6.dp.toPx() + 2.dp.toPx() * progress,
                            refractionAmount = 7.dp.toPx() + 3.dp.toPx() * progress,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = pressProgress.value
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = lerp(0.92f, 1f, progress)),
                                angle = 45f,
                                falloff = 1.2f
                            ),
                            width = 1.2.dp
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 6.dp,
                            color = Color.Black.copy(alpha = if (isLightTheme) 0.06f else 0.16f)
                        )
                    },
                    innerShadow = {
                        val progress = pressProgress.value
                        InnerShadow(
                            radius = 4.dp,
                            color = Color.White.copy(alpha = lerp(0.35f, 0.60f, progress))
                        )
                    },
                    layerBlock = {
                        val lifted = lerp(1f, 78f / 56f, pressProgress.value)
                        scaleX = lifted
                        scaleY = lifted
                    },
                    onDrawSurface = {
                        val progress = pressProgress.value
                        val glassAlpha = if (isLightTheme) {
                            lerp(0.025f, 0.06f, progress)
                        } else {
                            lerp(0.02f, 0.055f, progress)
                        }
                        drawRect(Color.White.copy(alpha = glassAlpha))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
