package com.jiang.vitality.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiang.vitality.ui.LocalVitalityColors

/**
 * 液态玻璃外壳：模糊+折射背景 → 高光描边 → 动态折射光泽 → 内容，四层叠出液态玻璃质感。
 * [cornerRadiusPx] 供折射 shader 使用（导航栏 morph 时圆角连续变化），传负数则按胶囊处理。
 */
@Composable
fun LiquidGlassShell(
    shape: Shape,
    cornerRadiusPx: Float,
    modifier: Modifier = Modifier,
    sheenPhase: () -> Float = { 0f },
    blurRadius: Dp = 16.dp,
    elevation: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    Box(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = palette.ink.copy(alpha = .06f),
            spotColor = palette.ink.copy(alpha = .12f)
        )
    ) {
        LiquidGlassBackdrop(
            shape = shape,
            cornerRadiusPx = cornerRadiusPx,
            blurRadius = blurRadius,
            modifier = Modifier.matchParentSize()
        )
        LiquidGlassHighlight(
            shape = shape,
            modifier = Modifier.matchParentSize()
        )
        GlassSheen(shape = shape, phase = sheenPhase, modifier = Modifier.matchParentSize())
        content()
    }
}

/**
 * 玻璃内部折射光泽层：一道柔和的斜向光斑随 phase 缓慢游移，模拟玻璃内光线的流动，
 * 让气泡和导航栏在静止时也有轻微的生命感。
 */
@Composable
fun GlassSheen(
    shape: Shape,
    phase: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val livePhase = phase()
            val travel = livePhase - livePhase.toInt()
            val bandX = size.width * (.18f + travel * .64f)
            val bandY = size.height * (.16f + ((travel * 1.7f) % 1f) * .52f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .20f), Color.Transparent),
                    center = Offset(bandX, bandY),
                    radius = size.maxDimension * .55f
                ),
                radius = size.maxDimension * .55f,
                center = Offset(bandX, bandY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .30f), Color.Transparent),
                    center = Offset(bandX, bandY),
                    radius = size.maxDimension * .24f
                ),
                radius = size.maxDimension * .24f,
                center = Offset(bandX, bandY)
            )
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = .80f), Color.Transparent)
                ),
                start = Offset(size.width * .10f, 1.4.dp.toPx()),
                end = Offset(size.width * .90f, 1.4.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
