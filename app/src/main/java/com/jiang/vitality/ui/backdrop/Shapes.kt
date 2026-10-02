package com.jiang.vitality.ui.backdrop

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost

@Immutable
class Capsule : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = size.minDimension / 2f
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }

    override fun equals(other: Any?): Boolean = other is Capsule
    override fun hashCode(): Int = "Capsule".hashCode()
    override fun toString(): String = "Capsule()"
}

internal class ShapeProvider(
    private val shapeLambda: () -> Shape
) {
    val shape: Shape get() = shapeLambda()
    val innerShape: Shape get() = shapeLambda()
}

internal fun getCornerRadii(
    shape: Shape,
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray {
    val maxRadius = size.minDimension / 2f
    if (shape is Capsule) {
        return FloatArray(4) { maxRadius }
    }
    val cornerShape = shape as? CornerBasedShape ?: return FloatArray(4) { maxRadius }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft =
        if (isLtr) cornerShape.topStart.toPx(size, density)
        else cornerShape.topEnd.toPx(size, density)
    val topRight =
        if (isLtr) cornerShape.topEnd.toPx(size, density)
        else cornerShape.topStart.toPx(size, density)
    val bottomRight =
        if (isLtr) cornerShape.bottomEnd.toPx(size, density)
        else cornerShape.bottomStart.toPx(size, density)
    val bottomLeft =
        if (isLtr) cornerShape.bottomStart.toPx(size, density)
        else cornerShape.bottomEnd.toPx(size, density)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius)
    )
}
