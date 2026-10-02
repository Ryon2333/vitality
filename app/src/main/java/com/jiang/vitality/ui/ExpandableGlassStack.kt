package com.jiang.vitality.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val stackSpring = spring<Float>(
    dampingRatio = JiangMotion.SpatialDamping,
    stiffness = JiangMotion.SpatialStiffness
)

/** A compact glass deck that unfolds using the same physical timing as the navigation morph. */
@Composable
fun <T> ExpandableGlassStack(
    items: List<T>,
    key: (T) -> Any,
    onLongPress: (T) -> Unit,
    onAdd: (() -> Unit)? = null,
    onDelete: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 72.dp,
    itemContent: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val step = cardHeight + 10.dp
    val collapsedTail = (items.size.coerceAtMost(4) - 1).coerceAtLeast(0) * 12
    val targetHeight = if (items.isEmpty()) {
        if (onAdd != null) 54.dp else 0.dp
    } else if (expanded) {
        step * items.size + if (onAdd != null) 58.dp else 0.dp
    } else {
        cardHeight + collapsedTail.dp + if (onAdd != null) 54.dp else 0.dp
    }
    val height by animateDpAsState(targetHeight, spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), label = "stack-height")

    Box(modifier.fillMaxWidth().height(height)) {
        items.forEachIndexed { index, item ->
            val density = LocalDensity.current
            val revealPx = with(density) { 76.dp.toPx() }
            var dragging by remember(key(item)) { mutableStateOf(false) }
            var revealed by remember(key(item)) { mutableStateOf(false) }
            var dragX by remember(key(item)) { mutableFloatStateOf(0f) }
            val targetX = if (dragging) dragX else if (revealed) -revealPx else 0f
            val animatedX by animateFloatAsState(targetX, stackSpring, label = "swipe-${key(item)}")
            val targetY = if (expanded) step * index else (index.coerceAtMost(3) * 12).dp
            val y by animateDpAsState(targetY, spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), label = "stack-y-${key(item)}")
            val targetScale = if (expanded) 1f else 1f - index.coerceAtMost(3) * .018f
            val scale by animateFloatAsState(targetScale, stackSpring, label = "stack-scale-${key(item)}")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .offset(y = y)
                    .zIndex(if (expanded) index.toFloat() else (items.size - index).toFloat())
            ) {
                if (expanded && onDelete != null) {
                    GlassActionButton(
                        text = "删除",
                        onClick = { onDelete(item); revealed = false },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                    )
                }
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = if (expanded && onDelete != null) animatedX else 0f
                        }
                        .draggable(
                            enabled = expanded && onDelete != null,
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                dragging = true
                                dragX = (dragX + delta).coerceIn(-revealPx, 0f)
                            },
                            onDragStopped = {
                                dragging = false
                                revealed = dragX < -revealPx * .38f
                                dragX = if (revealed) -revealPx else 0f
                            }
                        )
                        .pointerInput(item, expanded) {
                        detectTapGestures(
                            onTap = {
                                if (revealed) {
                                    revealed = false
                                    dragX = 0f
                                } else expanded = !expanded
                            },
                            onLongPress = { onLongPress(item) }
                        )
                    }
                ) {
                    Box(Modifier.fillMaxWidth().height(cardHeight).padding(horizontal = 18.dp)) {
                        itemContent(item)
                    }
                }
            }
        }

        if (onAdd != null) {
            val addY = if (items.isEmpty()) 0.dp else if (expanded) step * items.size else cardHeight + collapsedTail.dp
            val animatedAddY by animateDpAsState(addY, spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), label = "stack-add")
            GlassOutlinedButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .offset(y = animatedAddY)
                    .alpha(if (items.isEmpty() || expanded) 1f else .78f)
            ) {
                androidx.compose.material3.Text("＋")
            }
        }
    }
}
