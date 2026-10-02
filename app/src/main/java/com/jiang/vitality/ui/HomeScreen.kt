package com.jiang.vitality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jiang.vitality.data.Advice
import com.jiang.vitality.data.Snapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    state: Snapshot,
    onRecord: () -> Unit,
    onRecovery: () -> Unit,
    onPlayBubbleSound: (callItADay: Boolean) -> Unit = {},
    onCollapse: () -> Unit = {}
) {
    val scrollState = remember(state.locked) { ScrollState(0) }
    // 无论向上还是向下滑动，都收起成气泡；不会自动展开，只能点击气泡重新展开。
    LaunchedEffect(scrollState) {
        var previous = scrollState.value
        snapshotFlow { scrollState.value }.collect { current ->
            if (current == previous) return@collect
            previous = current
            onCollapse()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(if (state.locked) 166.dp else 128.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 21.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        (if (state.locked) RecoveryCoral else Blue).copy(alpha = 0.18f),
                                        (if (state.locked) RecoveryCoral else Blue).copy(alpha = 0.08f)
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.88f),
                                        (if (state.locked) RecoveryCoral else Blue).copy(alpha = 0.20f)
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (state.locked) "RECOVERY MODE" else "LIVE VITALITY",
                            color = if (state.locked) RecoveryCoral else Blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    Spacer(Modifier.height(13.dp))
                    Box(contentAlignment = Alignment.Center) {
                        VitalityRing(state.value, recoveryMode = state.locked)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.value}",
                                fontSize = 64.sp,
                                lineHeight = 68.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text("/ 100", color = Muted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(
                        Advice.label(state.value),
                        fontWeight = FontWeight.Bold,
                        color = Blue,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (state.locked) "正在休息 · 每满一小时 +2" else Advice.message(state.value),
                        color = Muted,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassButton(
                        onClick = onRecord,
                        enabled = !state.locked,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = RecoveryCoral.copy(alpha = .10f),
                            disabledContentColor = RecoveryCoral.copy(alpha = .76f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(if (state.locked) "状态已锁定" else "记录此刻状态  ＋")
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(9.dp)
                            .background(Blue.copy(alpha = .72f), CircleShape)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("此刻建议", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.locked) "今天已经结束。先休息，明早 06:00 再决定下一步。"
                            else Advice.suggestion(state.value),
                            color = Muted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 19.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("近七天", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val days = state.week.filterNotNull()
                        Text(
                            if (days.isEmpty()) "尚无记录" else "日均 ${days.average().toInt()} / 100",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    WeekChart(state.week)
                    Text("每天最后一次状态", color = Muted, fontSize = 12.sp)
                }
            }

            if (state.locked) {
                Text(
                    "如不切换，状态将锁定至 ${Instant.ofEpochMilli(state.unlockAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.locked) {
                GlassButton(
                    onClick = onRecovery,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(7.dp).background(RecoveryOrange, CircleShape))
                        Text("切回工作状态", fontSize = 16.sp, letterSpacing = .4.sp)
                    }
                }
            } else {
                HoldToCallItADayButton(
                    onConfirmed = onRecovery,
                    recoveryMode = false,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        CollapsingHomeHeader(
            recoveryMode = state.locked,
            scrollState = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .zIndex(3f)
        )

        if (state.locked) {
            LivingRestBubbleField(
                vitality = state.value,
                onPlayBubbleSound = onPlayBubbleSound,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun CollapsingHomeHeader(
    recoveryMode: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val palette = LocalVitalityColors.current
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val settleDistance = with(density) { 96.dp.toPx() }
            val progress = (scrollState.value / settleDistance).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to palette.background.copy(alpha = progress * .46f),
                    .72f to palette.background.copy(alpha = progress * .28f),
                    1f to palette.background.copy(alpha = 0f),
                    endY = size.height
                )
            )
            if (progress > .16f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = progress * .72f), Color.Transparent)
                    ),
                    start = Offset(size.width * .05f, size.height * .58f),
                    end = Offset(size.width * .95f, size.height * .58f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Column(
            Modifier
                .padding(start = 18.dp, top = 18.dp)
                .graphicsLayer {
                    val brandDistance = 42.dp.toPx()
                    val progress = (scrollState.value / brandDistance).coerceIn(0f, 1f)
                    translationY = -progress * 8.dp.toPx()
                    scaleX = 1f - progress * .08f
                    scaleY = 1f - progress * .08f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            Text(
                "江  ·  J I A N G",
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Text(
                if (recoveryMode) "恢复空间" else "今日状态",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        alpha = 1f - (scrollState.value / 40.dp.toPx()).coerceIn(0f, 1f)
                    }
            )
        }

        Text(
            "状态是第一优先级",
            color = Ink,
            fontSize = 30.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.6).sp,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 18.dp, top = 94.dp)
                .graphicsLayer {
                    val delay = 24.dp.toPx()
                    val travel = 72.dp.toPx()
                    val progress = ((scrollState.value - delay) / travel).coerceIn(0f, 1f)
                    translationY = -progress * 48.dp.toPx()
                    scaleX = 1f - progress * .37f
                    scaleY = 1f - progress * .37f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        )
    }
}

private val demo = Snapshot(78, 100, false, 0L, emptyList(), emptyList(), emptyList(), listOf(80, 76, 84, null, 70, 74, 78))

@Preview(name = "活力首页", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
fun HomeScreenPreview() {
    VitalityTheme(recoveryMode = false) {
        Surface(color = LocalVitalityColors.current.background) {
            HomeScreen(demo, onRecord = {}, onRecovery = {})
        }
    }
}

@Preview(name = "休息锁定", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
fun RecoveryPreview() {
    VitalityTheme(recoveryMode = true) {
        Surface(color = LocalVitalityColors.current.background) {
            HomeScreen(
                demo.copy(value = 42, locked = true, unlockAt = System.currentTimeMillis() + 36_000_000L),
                onRecord = {},
                onRecovery = {}
            )
        }
    }
}
