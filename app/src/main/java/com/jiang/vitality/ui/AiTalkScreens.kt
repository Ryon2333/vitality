package com.jiang.vitality.ui

import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jiang.vitality.data.AiConversation
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AiTalkLibrary(
    talks: List<AiConversation>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (AiConversation) -> Unit,
    onCollapse: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(talks) { talks.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.distinct() }
    val tags = remember(talks) { talks.flatMap { it.tags }.distinct().sorted() }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val filtered = talks.filter { talk ->
        (selectedDay == null || Instant.ofEpochMilli(talk.createdAt).atZone(zone).toLocalDate() == selectedDay) &&
            (selectedTag == null || selectedTag in talk.tags)
    }
    val grouped = filtered.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
    val scrollState = rememberAutoCollapseScrollState(onCollapse)

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        AiPageBackHeader("ai谈", "收藏值得反复阅读的对话。", onBack)
        if (days.isNotEmpty()) {
            Text("按日期", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            FilterRail(
                items = days.map { it.format(DateTimeFormatter.ofPattern("M月d日")) },
                selected = selectedDay?.format(DateTimeFormatter.ofPattern("M月d日")),
                onSelect = { label -> selectedDay = days.firstOrNull { it.format(DateTimeFormatter.ofPattern("M月d日")) == label } }
            )
        }
        if (tags.isNotEmpty()) {
            Text("按标签", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            FilterRail(tags, selectedTag) { selectedTag = it }
        }
        if (talks.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "还没有保存对话。点击加号，把 ChatGPT 中复制的回答原样粘贴进来。",
                    color = Muted,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else if (filtered.isEmpty()) {
            Text("这个筛选条件下还没有对话。", color = Muted)
        }
        grouped.forEach { (date, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(aiDayTitle(date), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${entries.size} 条 · 点击展开", color = Muted, fontSize = 11.sp)
                }
                ExpandableGlassStack(
                    items = entries,
                    key = { it.id },
                    onLongPress = onOpen,
                    cardHeight = 118.dp
                ) { talk ->
                    AiTalkPreview(talk, onOpen)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.size(68.dp).clickable(onClick = onAdd), shape = CircleShape) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("＋", color = Blue, fontSize = 28.sp)
                }
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun FilterRail(items: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassActionButton(if (selected == null) "● 全部" else "全部", { onSelect(null) })
        items.forEach { item ->
            GlassActionButton(if (selected == item) "● $item" else item, { onSelect(item) })
        }
    }
}

@Composable
private fun AiTalkPreview(talk: AiConversation, onOpen: (AiConversation) -> Unit) {
    Column(
        Modifier.fillMaxSize().clickable { onOpen(talk) },
        verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                talk.title,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                Instant.ofEpochMilli(talk.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        if (talk.tags.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(talk.tags.joinToString("  ") { "#$it" }, color = Blue, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun AiTalkEditor(
    onBack: () -> Unit,
    onSave: (title: String, answer: String, tags: List<String>) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        AiPageBackHeader("新增对话", "粘贴后会保留 Markdown 结构。", onBack)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(500) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("发送给 AI 的问题") },
            placeholder = { Text("顶部标题") },
            shape = GlassControlShape,
            colors = glassTextFieldColors()
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标签") },
            placeholder = { Text("用空格或逗号分隔，例如：Compose 学习") },
            shape = GlassControlShape,
            colors = glassTextFieldColors()
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(500_000) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
            label = { Text("AI 的回答") },
            placeholder = { Text("在 ChatGPT 点击“一键复制”后粘贴到这里") },
            shape = RoundedCornerShape(28.dp),
            colors = glassTextFieldColors()
        )
        RecordStateButton(
            text = "保存到 ai谈",
            onClick = {
                onSave(
                    title,
                    answer,
                    tags.split(Regex("[，,\\s]+"))
                )
            },
            enabled = title.isNotBlank() && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
fun AiTalkDetail(
    talk: AiConversation,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        AiPageBackHeader("ai谈", aiDateTime(talk.createdAt), onBack)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(talk.title, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                if (talk.tags.isNotEmpty()) Text(talk.tags.joinToString("  ") { "#$it" }, color = Blue, fontSize = 12.sp)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            MarkdownAnswer(talk.answer, Modifier.fillMaxWidth().padding(20.dp))
        }
        GlassOutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text("删除这段对话", color = RecoveryCoral) }
        Spacer(Modifier.height(130.dp))
    }
    if (confirmDelete) {
        GlassDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这段对话？") },
            text = { Text("删除后无法恢复。") },
            actions = {
                GlassActionButton("取消", { confirmDelete = false })
                GlassActionButton("删除", onDelete)
            }
        )
    }
}

@Composable
private fun MarkdownAnswer(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textSizePx = with(LocalDensity.current) { 16.sp.toPx() }
    val markwon = remember(context, textSizePx) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(JLatexMathPlugin.create(textSizePx) { it.inlinesEnabled(true) })
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val textColor = Ink.toArgb()
    val linkColor = Blue.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.35f)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = AndroidColor.TRANSPARENT
                setTextIsSelectable(true)
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            markwon.setMarkdown(view, normalizeChatGptMarkdown(markdown))
        }
    )
}

private fun normalizeChatGptMarkdown(value: String): String = value
    .replace("\\[", "\$\$")
    .replace("\\]", "\$\$")
    .replace("\\(", "\$")
    .replace("\\)", "\$")

@Composable
private fun AiPageBackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.size(48.dp).clickable(onClick = onBack), shape = CircleShape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹", color = Ink, fontSize = 32.sp) }
        }
        GlassPageHeader(title, Modifier.weight(1f), subtitle)
    }
}

private fun aiDateTime(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))

private fun aiDayTitle(date: LocalDate): String = when (date) {
    LocalDate.now() -> "今天"
    LocalDate.now().minusDays(1) -> "昨天"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE"))
}
