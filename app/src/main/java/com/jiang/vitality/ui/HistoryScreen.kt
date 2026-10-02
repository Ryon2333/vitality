package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.Snapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun HistoryScreen(state: Snapshot, onCollapse: () -> Unit = {}) {
    val scrollState = rememberAutoCollapseScrollState(onCollapse)
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        GlassPageHeader(title="日记",subtitle="每一次状态与感受都留在这里。",modifier=Modifier.fillMaxWidth())
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("近七天",color=Ink,fontWeight=FontWeight.Bold,fontSize=18.sp)
            WeekChart(state.week)
            Text("按每日最后一次状态统计",color=Muted,fontSize=12.sp)
        } }
        val days = state.readings.asReversed().take(365).groupBy { entry ->
            Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        if(days.isEmpty()) Text("还没有记录。去首页留下今天的第一段感受。",color=Muted)
        days.forEach { (date, entries) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(dayTitle(date),color=Ink,fontWeight=FontWeight.Bold,fontSize=19.sp)
                        Text("${entries.size} 条",color=Muted,fontSize=12.sp)
                    }
                    entries.forEachIndexed { index, entry ->
                        if(index > 0) HorizontalDivider(color=Color.White.copy(alpha=.34f))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(
                                Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                                color=Muted,
                                fontSize=13.sp
                            )
                            Text("${entry.value}",color=Blue,fontSize=23.sp,fontWeight=FontWeight.Bold)
                        }
                        if(entry.note.isNotBlank()) {
                            Text("此刻的感受",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                            Text(entry.note,color=Ink,fontSize=15.sp,lineHeight=22.sp)
                        }
                        if (entry.photoPaths.isNotEmpty()) MemoryPhotos(entry.photoPaths)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPhotos(paths: List<String>) {
    var viewingPath by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { path ->
            val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "当天随手拍，点击放大",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewingPath = path }
                )
            }
        }
    }
    viewingPath?.let { path ->
        PhotoViewer(imagePath = path, onDismiss = { viewingPath = null })
    }
}

private fun dayTitle(date: LocalDate): String = when (date) {
    LocalDate.now() -> "今天"
    LocalDate.now().minusDays(1) -> "昨天"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE"))
}
