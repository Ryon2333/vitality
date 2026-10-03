package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.ui.Blue
import com.jiang.vitality.ui.LocalVitalityColors
import com.jiang.vitality.ui.RecoveryCoral
import com.jiang.vitality.ui.backdrop.LiquidBottomTab
import com.jiang.vitality.ui.backdrop.LiquidBottomTabs
import com.jiang.vitality.ui.backdrop.LiquidButton
import com.kyant.backdrop.Backdrop

/**
 * 苹果液态玻璃标准底部导航栏：
 * 采用 Kyant0 AndroidLiquidGlass 标准 `LiquidBottomTabs` 架构：
 * 1. 纯净背景层：Capsule 胶囊 + 动态背景模糊 + 24px 透镜折射；
 * 2. 独立活动透镜：结合 `DampedDragAnimation` 速度拉伸、7 波段真光学色散透镜与内壁法线高光；
 * 3. 收起时平滑 Morph 变成单触点液态气泡。
 */
@Composable
fun JiangLiquidNavigationBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    recoveryMode: Boolean,
    collapsed: Boolean,
    onExpand: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val accentColor = if (recoveryMode) RecoveryCoral else Blue
    val palette = LocalVitalityColors.current

    Box(modifier = modifier.height(64.dp)) {
        val morph = remember { Animatable(if (collapsed) 0f else 1f) }
        LaunchedEffect(collapsed) {
            morph.animateTo(if (collapsed) 0f else 1f, navigationMorphSpec)
        }
        val p = morph.value

        if (p > 0.05f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(1f, .5f)
                        alpha = ((p - .03f) / .82f).coerceIn(0f, 1f)
                        scaleX = .14f + .86f * p
                        scaleY = .82f + .18f * p
                        translationX = (1f - p) * size.width * .43f
                    }
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = onSelected,
                    backdrop = backdrop,
                    tabsCount = navigationDestinations.size,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    navigationDestinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        val itemColor = if (isSelected) accentColor else palette.ink.copy(alpha = 0.62f)
                        LiquidBottomTab(onClick = { onSelected(index) }) {
                            NavigationGlyphView(
                                glyph = destination.glyph,
                                color = itemColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = destination.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = itemColor
                            )
                        }
                    }
                }
            }
        }

        if (p < 0.95f) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        val bubbleProgress = 1f - p
                        alpha = ((bubbleProgress - .02f) / .72f).coerceIn(0f, 1f)
                        val liquidScale = .72f + .28f * bubbleProgress
                        scaleX = liquidScale + p * .30f
                        scaleY = liquidScale - p * .08f
                        translationX = -p * 10.dp.toPx()
                    }
            ) {
                LiquidButton(
                    onClick = onExpand,
                    backdrop = backdrop,
                    modifier = Modifier.size(56.dp)
                ) {
                    LiquidBubble(
                        glyph = navigationDestinations[selectedIndex.coerceIn(0, navigationDestinations.lastIndex)].glyph
                    )
                }
            }
        }
    }
}
