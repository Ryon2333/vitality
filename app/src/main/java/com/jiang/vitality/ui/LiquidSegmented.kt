package com.jiang.vitality.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 液态分段切换通用状态与指示器修饰符。
 * 选中指示药丸从旧格滑动到新格（前缘快后缘慢的双弹簧，行进中自然微拉伸），
 * 替代生硬的直接变色。
 */
class LiquidSegmentState internal constructor() {
    internal val bounds = mutableStateMapOf<Int, Rect>()
    internal val leftEdge = Animatable(Float.NaN)
    internal val rightEdge = Animatable(Float.NaN)
    internal val alpha = Animatable(0f)
    internal var top = 0f
    internal var height = 0f

    internal var containerCoords: LayoutCoordinates? = null
    internal val itemCoords = HashMap<Int, LayoutCoordinates>()

    internal fun refresh(index: Int) {
        val container = containerCoords ?: return
        val item = itemCoords[index] ?: return
        if (!container.isAttached || !item.isAttached) return
        val rect = container.localBoundingBoxOf(item, clipBounds = false)
        if (bounds[index] != rect) bounds[index] = rect
    }

    internal fun refreshAll() {
        itemCoords.keys.forEach { refresh(it) }
    }
}

@Composable
fun rememberLiquidSegmentState(): LiquidSegmentState = remember { LiquidSegmentState() }

@Composable
fun LiquidSegmentAnimation(state: LiquidSegmentState, selectedIndex: Int) {
    val target = state.bounds[selectedIndex]
    LaunchedEffect(selectedIndex, target) {
        if (target == null) {
            state.alpha.animateTo(0f, spring(stiffness = 600f))
            return@LaunchedEffect
        }
        state.top = target.top
        state.height = target.height
        if (state.leftEdge.value.isNaN() || state.alpha.value == 0f) {
            state.leftEdge.snapTo(target.left)
            state.rightEdge.snapTo(target.right)
            state.alpha.animateTo(1f, spring(stiffness = 600f))
            return@LaunchedEffect
        }
        val movingRight = target.left > state.leftEdge.value
        val lead = spring<Float>(dampingRatio = 0.68f, stiffness = 900f)
        val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
        launch { state.alpha.animateTo(1f, spring(stiffness = 600f)) }
        launch { state.leftEdge.animateTo(target.left, if (movingRight) trail else lead) }
        launch { state.rightEdge.animateTo(target.right, if (movingRight) lead else trail) }
    }
}

fun Modifier.liquidSegmentItem(state: LiquidSegmentState, index: Int): Modifier =
    onGloballyPositioned { coords ->
        state.itemCoords[index] = coords
        state.refresh(index)
    }

fun Modifier.liquidSegmentIndicator(
    state: LiquidSegmentState,
    fill: Color,
    cornerRadius: Dp? = null,
    border: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
): Modifier = liquidSegmentIndicatorImpl(
    state, cornerRadius, borderWidth,
    fillColor = fill, borderColor = border, fillBrush = null, borderBrush = null,
)

fun Modifier.liquidSegmentIndicatorBrush(
    state: LiquidSegmentState,
    fill: Brush,
    border: Brush? = null,
    cornerRadius: Dp? = null,
    borderWidth: Dp = 1.dp,
): Modifier = liquidSegmentIndicatorImpl(
    state, cornerRadius, borderWidth,
    fillColor = null, borderColor = null, fillBrush = fill, borderBrush = border,
)

private fun Modifier.liquidSegmentIndicatorImpl(
    state: LiquidSegmentState,
    cornerRadius: Dp?,
    borderWidth: Dp,
    fillColor: Color?,
    borderColor: Color?,
    fillBrush: Brush?,
    borderBrush: Brush?,
): Modifier = onGloballyPositioned { coords ->
    state.containerCoords = coords
    state.refreshAll()
}.drawBehind {
    val a = state.alpha.value
    if (a <= 0.01f) return@drawBehind
    val left = state.leftEdge.value
    val right = state.rightEdge.value
    if (left.isNaN() || right.isNaN() || right <= left) return@drawBehind
    val radiusPx = cornerRadius?.toPx() ?: (state.height / 2f)
    val corner = CornerRadius(radiusPx, radiusPx)
    val topLeft = Offset(left, state.top)
    val size = Size(right - left, state.height)
    if (fillBrush != null) {
        drawRoundRect(brush = fillBrush, topLeft = topLeft, size = size, cornerRadius = corner, alpha = a)
    } else if (fillColor != null) {
        drawRoundRect(
            color = fillColor.copy(alpha = fillColor.alpha * a),
            topLeft = topLeft, size = size, cornerRadius = corner,
        )
    }
    val inset = borderWidth.toPx() / 2f
    val borderTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
    val borderSize = Size(size.width - inset * 2, size.height - inset * 2)
    val borderCorner = CornerRadius(radiusPx - inset, radiusPx - inset)
    if (borderBrush != null) {
        drawRoundRect(
            brush = borderBrush, topLeft = borderTopLeft, size = borderSize,
            cornerRadius = borderCorner, style = Stroke(width = borderWidth.toPx()), alpha = a,
        )
    } else if (borderColor != null && borderColor.alpha > 0f) {
        drawRoundRect(
            color = borderColor.copy(alpha = borderColor.alpha * a),
            topLeft = borderTopLeft, size = borderSize,
            cornerRadius = borderCorner, style = Stroke(width = borderWidth.toPx()),
        )
    }
}
