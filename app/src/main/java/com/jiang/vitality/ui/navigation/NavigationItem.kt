package com.jiang.vitality.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.ui.LocalVitalityColors
import kotlin.math.cos
import kotlin.math.sin

internal enum class NavigationGlyph { Today, Rest, Records, Settings }

internal data class NavigationDestination(
    val label: String,
    val glyph: NavigationGlyph
)

internal val navigationDestinations = listOf(
    NavigationDestination("今日", NavigationGlyph.Today),
    NavigationDestination("休息", NavigationGlyph.Rest),
    NavigationDestination("记录", NavigationGlyph.Records),
    NavigationDestination("设置", NavigationGlyph.Settings)
)

/**
 * 单个导航项。选中高亮不再用生硬的变色，而是交给背后的液态光源层；
 * 这里只保留平滑的按压反馈与柔和的颜色过渡。
 */
@Composable
internal fun NavigationItem(
    destination: NavigationDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVitalityColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .92f else 1f,
        animationSpec = itemPressSpec,
        label = "navigation item response"
    )
    val contentColor by animateColorAsState(
        targetValue = palette.ink.copy(alpha = if (selected) .94f else .46f),
        animationSpec = tween(260),
        label = "navigation item contrast"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (selected) -1.5.dp.toPx() else 0f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            NavigationGlyphView(
                glyph = destination.glyph,
                color = contentColor,
                modifier = Modifier.size(23.dp)
            )
        }
        Text(
            text = destination.label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp, bottom = 5.dp)
        )
    }
}

@Composable
internal fun NavigationGlyphView(
    glyph: NavigationGlyph,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val stroke = 1.75.dp.toPx()
        val center = this.center
        when (glyph) {
            NavigationGlyph.Today -> {
                drawCircle(color, radius = size.minDimension * .31f, center = center, style = Stroke(stroke))
                drawCircle(color, radius = size.minDimension * .09f, center = center)
                drawArc(
                    color.copy(alpha = .52f),
                    startAngle = -58f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(size.width * .08f, size.height * .08f),
                    size = Size(size.width * .84f, size.height * .84f),
                    style = Stroke(stroke * .65f)
                )
            }
            NavigationGlyph.Rest -> {
                val path = Path().apply {
                    moveTo(size.width * .70f, size.height * .17f)
                    cubicTo(size.width * .35f, size.height * .18f, size.width * .23f, size.height * .68f, size.width * .61f, size.height * .82f)
                    cubicTo(size.width * .30f, size.height * .92f, size.width * .10f, size.height * .66f, size.width * .18f, size.height * .39f)
                    cubicTo(size.width * .25f, size.height * .16f, size.width * .49f, size.height * .07f, size.width * .70f, size.height * .17f)
                    close()
                }
                drawPath(path, color)
            }
            NavigationGlyph.Records -> {
                drawRoundRect(color.copy(alpha = .52f), Offset(size.width * .12f, size.height * .51f), Size(size.width * .16f, size.height * .35f), CornerRadius(stroke))
                drawRoundRect(color.copy(alpha = .72f), Offset(size.width * .42f, size.height * .30f), Size(size.width * .16f, size.height * .56f), CornerRadius(stroke))
                drawRoundRect(color, Offset(size.width * .72f, size.height * .13f), Size(size.width * .16f, size.height * .73f), CornerRadius(stroke))
            }
            NavigationGlyph.Settings -> {
                drawCircle(color, size.minDimension * .25f, center, style = Stroke(stroke))
                drawCircle(color, size.minDimension * .08f, center)
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45.0))
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(
                        color,
                        center + direction * size.minDimension * .34f,
                        center + direction * size.minDimension * .45f,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
