package com.jiang.vitality.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.FossilDiscovery
import com.jiang.vitality.data.PaleontologyCatalog
import com.jiang.vitality.data.PrehistoricCreature
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ExplorationScreen(
    discoveries: List<FossilDiscovery>,
    onDig: () -> FossilDiscovery?,
    onBack: () -> Unit,
    onCollapse: () -> Unit
) {
    val today = LocalDate.now()
    val todayDiscovery = discoveries.firstOrNull { it.localDate() == today }
    var revealed by remember(todayDiscovery) { mutableStateOf(todayDiscovery) }
    var inspecting by remember { mutableStateOf<FossilDiscovery?>(null) }
    var excavating by remember { mutableStateOf(false) }
    val excavation = remember { Animatable(if (todayDiscovery == null) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scroll = rememberAutoCollapseScrollState(onCollapse)
    val uniqueCount = discoveries.distinctBy { it.creatureId }.size

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        PageBackHeader("探索", "每天挖宝一次，慢慢拼出失落的生命世界。", onBack)

        GlassCard(Modifier.fillMaxWidth(), elevation = 14.dp) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("今日地层", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("$uniqueCount / ${PaleontologyCatalog.all.size}", color = Blue, fontWeight = FontWeight.SemiBold)
                }
                ExcavationOrb(excavation.value, excavating, revealed?.let { PaleontologyCatalog.byId[it.creatureId] })
                AnimatedVisibility(
                    visible = revealed != null,
                    enter = fadeIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleIn(spring(dampingRatio = JiangMotion.FluidDamping, stiffness = JiangMotion.FluidStiffness), initialScale = .72f)
                ) {
                    revealed?.let { FossilResult(PaleontologyCatalog.byId[it.creatureId], it.discoveredAt) }
                }
                GlassButton(
                    onClick = {
                        if (!excavating && todayDiscovery == null) {
                            excavating = true
                            scope.launch {
                                excavation.snapTo(0f)
                                excavation.animateTo(1f, tween(1_650, easing = FastOutSlowInEasing))
                                revealed = onDig()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                excavating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = todayDiscovery == null && !excavating
                ) {
                    Text(
                        when {
                            excavating -> "正在清理地层…"
                            todayDiscovery != null -> "今日已经探索过"
                            else -> "挖宝一次"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    if (todayDiscovery == null) "每个自然日只有一次机会，未发现的物种会优先出现。" else "这块地层今天已经安静下来，明天再继续。",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text("我的发现", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        if (discoveries.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("还没有发现。今天的第一块地层正等着你。", color = Muted, modifier = Modifier.padding(22.dp))
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = 3
            ) {
                discoveries.distinctBy { it.creatureId }.forEach { discovery ->
                    val creature = PaleontologyCatalog.byId[discovery.creatureId] ?: return@forEach
                    GlassCard(
                        Modifier.size(104.dp).clickable { inspecting = discovery },
                        shape = CircleShape,
                        elevation = 9.dp
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FossilGlyph(creature.group, Modifier.size(31.dp))
                            Text(creature.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(discovery.localDate().format(DateTimeFormatter.ofPattern("M.d")), color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(140.dp))
    }

    inspecting?.let { discovery ->
        val creature = PaleontologyCatalog.byId[discovery.creatureId]
        if (creature != null) GlassDialog(
            onDismissRequest = { inspecting = null },
            title = { Text(creature.name, color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${creature.era} · ${creature.group}", color = Blue, fontWeight = FontWeight.SemiBold)
                    Text(creature.note, color = Ink)
                    Text("发现于 ${formatDiscoveryTime(discovery.discoveredAt)}", color = Muted, fontSize = 12.sp)
                }
            },
            actions = { GlassActionButton("收好", { inspecting = null }) }
        )
    }
}

@Composable
private fun ExcavationOrb(progress: Float, excavating: Boolean, creature: PrehistoricCreature?) {
    GlassCard(Modifier.size(218.dp), shape = CircleShape, elevation = 18.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = this.center
                drawCircle(Brush.radialGradient(listOf(Color(0xFFECCB93).copy(.78f), Color(0xFF9A735B).copy(.68f), Color(0xFF493735).copy(.72f))))
                repeat(7) { layer ->
                    val y = size.height * (.20f + layer * .105f)
                    val sway = kotlin.math.sin(layer * 1.7f + progress * 3f) * 8.dp.toPx()
                    drawLine(Color.White.copy(.10f + layer * .012f), Offset(size.width * .15f + sway, y), Offset(size.width * .85f + sway, y), 1.2.dp.toPx(), StrokeCap.Round)
                }
                val clearedRadius = size.minDimension * .34f * progress
                drawCircle(Color(0xFFF3E6D4).copy(.34f + progress * .30f), clearedRadius, center)
                if (progress < 1f) {
                    repeat(10) { index ->
                        val angle = index * .93f + progress * 4f
                        val radius = size.minDimension * (.12f + index % 4 * .045f)
                        drawCircle(Color(0xFFFFE2A8).copy(.20f), 2.2.dp.toPx(), Offset(center.x + kotlin.math.cos(angle) * radius, center.y + kotlin.math.sin(angle) * radius))
                    }
                }
            }
            if (creature != null && progress >= .92f) FossilGlyph(creature.group, Modifier.size(82.dp))
            else Text(if (excavating) "轻轻清理" else "⌁", color = Color.White.copy(.88f), fontSize = if (excavating) 15.sp else 48.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun FossilResult(creature: PrehistoricCreature?, discoveredAt: Long) {
    if (creature == null) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("发现了 ${creature.name}", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${creature.era} · ${creature.group}", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(creature.note, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(formatDiscoveryTime(discoveredAt), color = Muted.copy(.78f), fontSize = 10.sp)
    }
}

@Composable
private fun FossilGlyph(group: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = Color(0xFF6B5147).copy(.88f)
        val stroke = size.minDimension * .085f
        when (group) {
            "海洋生命" -> {
                val path = Path().apply {
                    moveTo(size.width * .12f, size.height * .56f)
                    quadraticTo(size.width * .46f, size.height * .18f, size.width * .78f, size.height * .50f)
                    quadraticTo(size.width * .47f, size.height * .82f, size.width * .12f, size.height * .56f)
                }
                drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(ink, Offset(size.width * .78f, size.height * .5f), Offset(size.width * .94f, size.height * .30f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .78f, size.height * .5f), Offset(size.width * .94f, size.height * .70f), stroke, StrokeCap.Round)
            }
            "天空生命" -> {
                drawLine(ink, Offset(size.width * .50f, size.height * .28f), Offset(size.width * .50f, size.height * .78f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .50f, size.height * .43f), Offset(size.width * .08f, size.height * .18f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .50f, size.height * .43f), Offset(size.width * .92f, size.height * .18f), stroke, StrokeCap.Round)
            }
            "早期生命" -> {
                drawOval(ink, topLeft = Offset(size.width * .20f, size.height * .10f), size = Size(size.width * .60f, size.height * .80f), style = Stroke(stroke))
                repeat(5) { index ->
                    val y = size.height * (.25f + index * .12f)
                    drawLine(ink, Offset(size.width * .26f, y), Offset(size.width * .74f, y), stroke * .55f, StrokeCap.Round)
                }
            }
            else -> {
                drawCircle(ink, size.minDimension * .18f, Offset(size.width * .30f, size.height * .35f), style = Stroke(stroke))
                drawLine(ink, Offset(size.width * .42f, size.height * .45f), Offset(size.width * .75f, size.height * .68f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .55f, size.height * .54f), Offset(size.width * .43f, size.height * .80f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .68f, size.height * .63f), Offset(size.width * .82f, size.height * .84f), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun FossilDiscovery.localDate(): LocalDate =
    Instant.ofEpochMilli(discoveredAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDiscoveryTime(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
