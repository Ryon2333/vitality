package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * 独立的液态光源层：不通过「改颜色」标记选中项，而是在玻璃内部绘制一束
 * 柔和彩色光晕，点击其它项时光源像液滴一样流动过去，带动玻璃的折射变化。
 */
@Composable
internal fun LiquidLightSource(
    selectedIndex: Int,
    itemCount: Int,
    recoveryMode: Boolean,
    modifier: Modifier = Modifier
) {
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        position.animateTo(selectedIndex.toFloat(), liquidLightSpec)
    }
    LiquidHighlight(
        position = { position.value },
        velocity = { position.velocity },
        itemCount = itemCount,
        recoveryMode = recoveryMode,
        modifier = modifier
    )
}

@Composable
private fun LiquidHighlight(
    position: () -> Float,
    velocity: () -> Float,
    itemCount: Int,
    recoveryMode: Boolean,
    modifier: Modifier = Modifier
) {
    val dayLights = remember {
        listOf(
            Color(0xFF8EB7F5),
            Color(0xFFB8A9E8),
            Color(0xFF8FCFD0),
            Color(0xFFD7ABC5)
        )
    }
    val recoveryLights = remember {
        listOf(
            Color(0xFFF1B29C),
            Color(0xFFE0B7C8),
            Color(0xFFE8C391),
            Color(0xFFAABFD2)
        )
    }
    Canvas(modifier) {
        val progress = position().coerceIn(0f, (itemCount - 1).toFloat())
        val lights = if (recoveryMode) recoveryLights else dayLights
        val left = floor(progress).toInt().coerceIn(0, lights.lastIndex)
        val right = (left + 1).coerceAtMost(lights.lastIndex)
        val fraction = progress - left
        val mainLight = lerp(lights[left], lights[right], fraction)
        val companion = lerp(lights[(left + 1) % lights.size], lights[(right + 1) % lights.size], fraction)
        val itemWidth = size.width / itemCount
        val center = Offset(itemWidth * (progress + .5f), size.height * .49f)
        val motion = (abs(velocity()) / 5f).coerceIn(0f, .26f)
        val lensWidth = itemWidth * (1.04f + motion)
        val lensHeight = size.height * (.76f - motion * .18f)
        val lensTopLeft = Offset(center.x - lensWidth / 2f, center.y - lensHeight / 2f)

        // 外圈彩色光晕：像一滴发光液体在玻璃内部晕开。
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    mainLight.copy(alpha = .34f),
                    companion.copy(alpha = .15f),
                    Color.Transparent
                ),
                center = center,
                radius = itemWidth * .88f
            ),
            center = center,
            radius = itemWidth * .88f
        )
        // 液滴核心：沿运动方向轻微拉长。
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    companion.copy(alpha = .13f),
                    Color.White.copy(alpha = .33f),
                    mainLight.copy(alpha = .27f)
                ),
                startX = lensTopLeft.x,
                endX = lensTopLeft.x + lensWidth
            ),
            topLeft = lensTopLeft,
            size = Size(lensWidth, lensHeight),
            cornerRadius = CornerRadius(lensHeight / 2f)
        )
        // 玻璃折射内壁高光。
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .72f), Color.White.copy(alpha = .06f)),
                startY = lensTopLeft.y,
                endY = lensTopLeft.y + lensHeight
            ),
            topLeft = lensTopLeft,
            size = Size(lensWidth, lensHeight),
            cornerRadius = CornerRadius(lensHeight / 2f),
            style = Stroke(width = 1.dp.toPx())
        )
        // 液滴上的亮斑。
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .30f), Color.Transparent),
                center = center - Offset(lensWidth * .13f, lensHeight * .22f),
                radius = lensWidth * .42f
            ),
            topLeft = Offset(
                center.x - lensWidth * .36f,
                center.y - lensHeight * .35f
            ),
            size = Size(lensWidth * .72f, lensHeight * .35f)
        )
    }
}
