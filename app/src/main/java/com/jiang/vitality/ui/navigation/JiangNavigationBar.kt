package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.jiang.vitality.ui.rememberFlowingColorPhase

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
    BoxWithConstraints(modifier = modifier.height(88.dp)) {
        val morph = remember { Animatable(if (collapsed) 0f else 1f) }
        LaunchedEffect(collapsed) {
            morph.animateTo(if (collapsed) 0f else 1f, navigationMorphSpec)
        }
        val p = morph.value
        val sheenPhase = rememberFlowingColorPhase(periodSeconds = 7f).value

        val bubbleSize = 62.dp
        val width = lerp(bubbleSize, maxWidth, p)
        val height = lerp(bubbleSize, 88.dp, p)
        // 气泡阶段圆角 = 半宽（圆形），拉长后圆角封顶 34dp，形成胶囊 → 圆角条的液态形变。
        val corner = minOf(width / 2f, 34.dp)
        val shape = RoundedCornerShape(corner)

        LiquidGlassShell(
            shape = shape,
            sheenPhase = sheenPhase,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(width)
                .height(height)
        ) {
            // 导航栏内容（随 morph 进度淡入）。
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = p }
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
                    .graphicsLayer { alpha = 1f - p }
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
