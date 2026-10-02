package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jiang.vitality.ui.LocalVitalityColors
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 收缩后的小玻璃气泡：真实玻璃质感由外层 [LiquidGlassShell] 提供，
 * 这里只负责「生命感」——缓慢上下漂浮 + 轻微呼吸缩放，以及当前页对应的图标。
 */
@Composable
internal fun LiquidBubble(
    glyph: NavigationGlyph,
    modifier: Modifier = Modifier
) {
    val palette = LocalVitalityColors.current
    val floatY = remember { Animatable(0f) }
    val breathe = remember { Animatable(1f) }

    // 缓慢上下漂浮：自然、柔软，不做机械的匀速移动。
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime() + 7L)
        while (isActive) {
            floatY.animateTo(
                -2.6f,
                tween(random.nextInt(2_100, 3_100), easing = FastOutSlowInEasing)
            )
            floatY.animateTo(
                2.6f,
                tween(random.nextInt(2_100, 3_100), easing = FastOutSlowInEasing)
            )
        }
    }
    // 轻微呼吸缩放。
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime() + 13L)
        while (isActive) {
            breathe.animateTo(
                1.035f,
                tween(random.nextInt(2_300, 3_300), easing = FastOutSlowInEasing)
            )
            breathe.animateTo(
                .965f,
                tween(random.nextInt(2_300, 3_300), easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = floatY.value.dp.toPx()
                scaleX = breathe.value
                scaleY = breathe.value
            },
        contentAlignment = Alignment.Center
    ) {
        NavigationGlyphView(
            glyph = glyph,
            color = palette.ink.copy(alpha = .88f),
            modifier = Modifier.size(25.dp)
        )
    }
}
