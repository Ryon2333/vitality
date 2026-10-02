package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jiang.vitality.ui.rememberFlowingColorPhase
import kotlin.math.roundToInt

/**
 * 液态玻璃导航栏入口。完整导航栏与右下角小气泡是**同一个**玻璃物体：
 * 通过一个 [Animatable] 驱动的 morph 进度，连续地改变尺寸、圆角与内容透明度，
 * 让「气泡 → 拉伸 → 导航栏」的形变像一滴液体展开，而不是两个组件切换。
 */
@Composable
fun JiangLiquidNavigationBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    recoveryMode: Boolean,
    collapsed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(88.dp)) {
        val morph = remember { Animatable(if (collapsed) 0f else 1f) }
        LaunchedEffect(collapsed) {
            morph.animateTo(if (collapsed) 0f else 1f, navigationMorphSpec)
        }
        val sheenPhase = rememberFlowingColorPhase(periodSeconds = 7f)
        val density = LocalDensity.current

        val bubbleSize = 62.dp
        val expandedHeight = 88.dp
        val shape = remember { RoundedCornerShape(34.dp) }
        val cornerPx = with(density) { 34.dp.toPx() }

        LiquidGlassShell(
            shape = shape,
            cornerRadiusPx = cornerPx,
            sheenPhase = { sheenPhase.value },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .layout { measurable, constraints ->
                    // Read the animation in measurement, not composition. Only this glass
                    // object is remeasured while morphing; its content is not recomposed.
                    val progress = morph.value.coerceIn(0f, 1f)
                    val bubblePx = bubbleSize.roundToPx()
                    val expandedHeightPx = expandedHeight.roundToPx()
                    val widthPx = (bubblePx + (constraints.maxWidth - bubblePx) * progress)
                        .roundToInt()
                        .coerceIn(bubblePx, constraints.maxWidth)
                    val heightPx = (bubblePx + (expandedHeightPx - bubblePx) * progress)
                        .roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                    layout(widthPx, heightPx) { placeable.placeRelative(0, 0) }
                }
        ) {
            // 导航栏内容（随 morph 进度淡入）。
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = morph.value }
            ) {
                LiquidLightSource(
                    selectedIndex = selectedIndex,
                    itemCount = navigationDestinations.size,
                    recoveryMode = recoveryMode,
                    modifier = Modifier.matchParentSize()
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationDestinations.forEachIndexed { index, destination ->
                        NavigationItem(
                            destination = destination,
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
            // 气泡内容（随 morph 进度淡出）。点击只在气泡态生效：
            // 展开后不再附加 clickable，避免透明的气泡层覆盖在导航项上方拦截点击。
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - morph.value }
                    .then(if (collapsed) Modifier.clickable(onClick = onExpand) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                LiquidBubble(
                    glyph = navigationDestinations[selectedIndex.coerceIn(0, navigationDestinations.lastIndex)].glyph
                )
            }
        }
    }
}
