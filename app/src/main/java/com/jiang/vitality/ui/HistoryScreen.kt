package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.Reading
import com.jiang.vitality.data.Snapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    state: Snapshot,
    onDeletePhoto: (readingTime: Long, path: String) -> Unit,
    onCollapse: () -> Unit = {}
) {
    val zone = remember { ZoneId.systemDefault() }
    val allDays = remember(state.readings) {
        state.readings.asReversed().take(365).groupBy {
            Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate()
        }
    }
    var selectedDate by remember(allDays.keys) { mutableStateOf<LocalDate?>(null) }
    var showingCollection by remember { mutableStateOf(false) }
    var viewingPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    if (showingCollection) {
        MemoryConstellationScreen(
            readings = state.readings,
            onBack = { showingCollection = false },
            onOpenDate = { date ->
                selectedDate = date
                showingCollection = false
            }
        )
        return
    }
    val visibleDays = selectedDate?.let { date -> allDays.filterKeys { it == date } } ?: allDays
    val listState = rememberAutoCollapseLazyListState(onCollapse)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassPageHeader("日记", Modifier.fillMaxWidth(), "按天翻阅状态、感受和随手拍。")
        }
        item {
            GlassOutlinedButton(
                onClick = { showingCollection = true },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp
            ) {
                Text("✦  我的集合", color = Blue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("近七天", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    WeekChart(state.week)
                    Text("按每日最后一次状态统计", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (allDays.isNotEmpty()) {
            item { DayFinder(allDays, selectedDate) { selectedDate = it } }
        }
        if (visibleDays.isEmpty()) {
            item { Text("还没有记录。去首页留下今天的第一段感受。", color = Muted) }
        }
        items(visibleDays.entries.toList(), key = { it.key.toEpochDay() }) { (date, entries) ->
            HistoryDayDeck(
                date = date,
                entries = entries,
                onViewPhoto = { viewingPath = it },
                onDeletePhoto = { readingTime, path -> pendingPhotoDelete = readingTime to path }
            )
        }
    }

    viewingPath?.let { path -> PhotoViewer(path) { viewingPath = null } }
    pendingPhotoDelete?.let { (readingTime, path) ->
        GlassDialog(
            onDismissRequest = { pendingPhotoDelete = null },
            title = { Text("删除这张照片？") },
            text = { Text("照片会从这条记录中永久移除，状态和心得仍会保留。") },
            actions = {
                GlassActionButton("取消", { pendingPhotoDelete = null })
                GlassActionButton("确认删除", {
                    onDeletePhoto(readingTime, path)
                    if (viewingPath == path) viewingPath = null
                    pendingPhotoDelete = null
                })
            }
        )
    }
}

@Composable
private fun DayFinder(
    days: Map<LocalDate, List<Reading>>,
    selectedDate: LocalDate?,
    onSelect: (LocalDate?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("按天查找", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (selectedDate != null) {
                Text("查看全部", color = Blue, fontSize = 13.sp, modifier = Modifier.clickable { onSelect(null) })
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            days.forEach { (date, entries) ->
                val preview = entries.asSequence().flatMap { it.photoPaths.asSequence() }.firstOrNull()
                DayPreviewBubble(
                    date = date,
                    photoPath = preview,
                    selected = selectedDate == date,
                    count = entries.size,
                    onClick = { onSelect(if (selectedDate == date) null else date) }
                )
            }
        }
    }
}

@Composable
private fun DayPreviewBubble(
    date: LocalDate,
    photoPath: String?,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val bitmap = rememberThumbnail(photoPath, 220)
    GlassCard(
        Modifier.width(92.dp).height(108.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        elevation = if (selected) 14.dp else 8.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    "${dayTitle(date)}的照片预览",
                    Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color(0xFF10213D).copy(alpha = .24f)))
            }
            Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (bitmap != null) Color.White else if (selected) Blue else Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("M月 · E")),
                    color = if (bitmap != null) Color.White.copy(.9f) else Muted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text("$count 条", color = if (bitmap != null) Color.White.copy(.82f) else Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HistoryDayDeck(
    date: LocalDate,
    entries: List<Reading>,
    onViewPhoto: (String) -> Unit,
    onDeletePhoto: (Long, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayTitle(date), color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("${entries.size} 条 · 点击展开", color = Muted, fontSize = 11.sp)
        }
        ExpandableGlassStack(
            items = entries,
            key = { it.time },
            onLongPress = {},
            cardHeight = 154.dp
        ) { entry ->
            ReadingCardContent(entry, onViewPhoto, onDeletePhoto)
        }
    }
}

@Composable
private fun ReadingCardContent(
    entry: Reading,
    onViewPhoto: (String) -> Unit,
    onDeletePhoto: (Long, String) -> Unit
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Muted,
                    fontSize = 12.sp
                )
                Text(entry.value.toString(), color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                entry.note.ifBlank { "只记录了此刻的状态" },
                color = if (entry.note.isBlank()) Muted else Ink,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.photoPaths.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                entry.photoPaths.take(2).forEach { path ->
                    PhotoThumbnail(
                        path,
                        onView = { onViewPhoto(path) },
                        onDelete = { onDeletePhoto(entry.time, path) }
                    )
                }
                if (entry.photoPaths.size > 2) {
                    Text("+${entry.photoPaths.size - 2}", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(path: String, onView: () -> Unit, onDelete: () -> Unit) {
    val bitmap = rememberThumbnail(path, 180) ?: return
    Box(Modifier.size(58.dp)) {
        Image(
            bitmap,
            "记录照片，点击放大",
            Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)).clickable(onClick = onView),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.align(Alignment.TopEnd).size(21.dp).clip(CircleShape)
                .background(Color(0xCCFFFFFF)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = RecoveryCoral, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun rememberThumbnail(path: String?, target: Int): ImageBitmap? = remember(path, target) {
    path?.let {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(it, bounds)
        var sample = 1
        while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
        BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

private fun dayTitle(date: LocalDate): String = when (date) {
    LocalDate.now() -> "今天"
    LocalDate.now().minusDays(1) -> "昨天"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE"))
}
