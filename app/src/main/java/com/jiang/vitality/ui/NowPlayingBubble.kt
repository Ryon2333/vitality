package com.jiang.vitality.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NowPlayingBubble(
    title: String?,
    playing: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mutedColor = Muted
    val progressColor = Blue
    AnimatedVisibility(
        visible = title != null,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = 90f)) + expandVertically(spring(dampingRatio = .75f, stiffness = 90f)),
        exit = fadeOut(spring(stiffness = 110f)) + shrinkVertically(spring(dampingRatio = .8f, stiffness = 100f))
    ) {
        GlassCard(
            Modifier
                .widthIn(min = 220.dp, max = 340.dp)
                .height(64.dp)
                .clickable(onClick = onOpen)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("正在聆听", color = Muted, fontSize = 10.sp, letterSpacing = .7.sp)
                        Text(title.orEmpty(), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlassActionButton(if (playing) "暂停" else "播放", onToggle)
                }
                val progress = if (durationMillis > 0L) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawLine(mutedColor.copy(alpha = .16f), Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Round)
                    drawLine(progressColor, Offset(0f, center.y), Offset(size.width * progress, center.y), size.height, StrokeCap.Round)
                }
            }
        }
    }
}
