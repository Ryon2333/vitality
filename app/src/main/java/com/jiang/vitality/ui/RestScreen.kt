package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.AiConversation

enum class SoundTherapyDestination { REST, LIBRARY, PLAYER, AI_LIBRARY, AI_EDITOR, AI_DETAIL }

@Composable
fun RestScreen(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    aiTalks: List<AiConversation>,
    onSaveAiTalk: (String, String, List<String>) -> AiConversation?,
    onDeleteAiTalk: (Long) -> Unit,
    musicNames: List<String>,
    musicCoverPath: (String) -> String?,
    currentMusic: String?,
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    playbackMode: MusicPlaybackMode,
    playbackError: String?,
    destination: SoundTherapyDestination,
    onDestination: (SoundTherapyDestination) -> Unit,
    onImportMusic: (Uri) -> Unit,
    onPlayMusic: (String) -> Unit,
    onToggleMusic: () -> Unit,
    onDeleteMusic: (String) -> Unit,
    onPreviousMusic: () -> Unit,
    onNextMusic: () -> Unit,
    onSeekMusic: (Long) -> Unit,
    onPlaybackMode: (MusicPlaybackMode) -> Unit,
    onCollapse: () -> Unit = {}
) {
    var selectedAiTalkId by remember { mutableStateOf<Long?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportMusic(uri)
    }
    BackHandler(enabled = destination != SoundTherapyDestination.REST) {
        onDestination(
            when (destination) {
                SoundTherapyDestination.PLAYER -> SoundTherapyDestination.LIBRARY
                SoundTherapyDestination.AI_EDITOR, SoundTherapyDestination.AI_DETAIL -> SoundTherapyDestination.AI_LIBRARY
                else -> SoundTherapyDestination.REST
            }
        )
    }
    AnimatedContent(
        targetState = destination,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState == SoundTherapyDestination.PLAYER) {
                return@AnimatedContent (
                    fadeIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleIn(spring(dampingRatio = JiangMotion.FluidDamping, stiffness = JiangMotion.FluidStiffness), initialScale = .28f) +
                        slideInHorizontally(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 3 } +
                        slideInVertically(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 3 }
                    ).togetherWith(
                    fadeOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), targetScale = .94f)
                ).using(SizeTransform(clip = false))
            }
            if (initialState == SoundTherapyDestination.PLAYER) {
                return@AnimatedContent (
                    fadeIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), initialScale = .94f)
                    ).togetherWith(
                    fadeOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleOut(spring(dampingRatio = JiangMotion.FluidDamping, stiffness = JiangMotion.FluidStiffness), targetScale = .28f) +
                        slideOutHorizontally(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 3 } +
                        slideOutVertically(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { it / 3 }
                ).using(SizeTransform(clip = false))
            }
            val forward = targetState.ordinal > initialState.ordinal
            val enterDirection = if (forward) 1 else -1
            val exitDirection = -enterDirection
            (fadeIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                scaleIn(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), initialScale = .965f) +
                slideInHorizontally(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { enterDirection * it / 7 })
                .togetherWith(
                    fadeOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) +
                        scaleOut(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness), targetScale = .965f) +
                        slideOutHorizontally(spring(dampingRatio = JiangMotion.SpatialDamping, stiffness = JiangMotion.SpatialStiffness)) { exitDirection * it / 7 }
                ).using(SizeTransform(clip = false))
        },
        label = "sound-therapy-space"
    ) { page ->
    when (page) {
        SoundTherapyDestination.REST -> RestOptionsPage(
            rests = rests,
            onSave = onSave,
            onOpenSoundTherapy = { onDestination(SoundTherapyDestination.LIBRARY) },
            onOpenAiTalk = { onDestination(SoundTherapyDestination.AI_LIBRARY) },
            onCollapse = onCollapse
        )
        SoundTherapyDestination.LIBRARY -> SoundTherapyLibrary(
            musicNames = musicNames,
            coverPath = musicCoverPath,
            currentMusic = currentMusic,
            isPlaying = isPlaying,
            onBack = { onDestination(SoundTherapyDestination.REST) },
            onImport = { importLauncher.launch(arrayOf("audio/*")) },
            onSelect = { name -> onPlayMusic(name); onDestination(SoundTherapyDestination.PLAYER) },
            onDelete = onDeleteMusic,
            onCollapse = onCollapse
        )
        SoundTherapyDestination.PLAYER -> SoundTherapyPlayer(
            title = currentMusic,
            coverPath = currentMusic?.let(musicCoverPath),
            isPlaying = isPlaying,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            mode = playbackMode,
            error = playbackError,
            onBack = { onDestination(SoundTherapyDestination.LIBRARY) },
            onToggle = onToggleMusic,
            onPrevious = onPreviousMusic,
            onNext = onNextMusic,
            onSeek = onSeekMusic,
            onMode = onPlaybackMode
        )
        SoundTherapyDestination.AI_LIBRARY -> AiTalkLibrary(
            talks = aiTalks,
            onBack = { onDestination(SoundTherapyDestination.REST) },
            onAdd = { onDestination(SoundTherapyDestination.AI_EDITOR) },
            onOpen = { talk ->
                selectedAiTalkId = talk.id
                onDestination(SoundTherapyDestination.AI_DETAIL)
            },
            onCollapse = onCollapse
        )
        SoundTherapyDestination.AI_EDITOR -> AiTalkEditor(
            onBack = { onDestination(SoundTherapyDestination.AI_LIBRARY) },
            onSave = { title, answer, tags ->
                onSaveAiTalk(title, answer, tags)?.let { saved ->
                    selectedAiTalkId = saved.id
                    onDestination(SoundTherapyDestination.AI_DETAIL)
                }
            }
        )
        SoundTherapyDestination.AI_DETAIL -> {
            val talk = aiTalks.firstOrNull { it.id == selectedAiTalkId }
            if (talk != null) {
                AiTalkDetail(
                    talk = talk,
                    onBack = { onDestination(SoundTherapyDestination.AI_LIBRARY) },
                    onDelete = {
                        onDeleteAiTalk(talk.id)
                        selectedAiTalkId = null
                        onDestination(SoundTherapyDestination.AI_LIBRARY)
                    }
                )
            } else {
                AiTalkLibrary(
                    talks = aiTalks,
                    onBack = { onDestination(SoundTherapyDestination.REST) },
                    onAdd = { onDestination(SoundTherapyDestination.AI_EDITOR) },
                    onOpen = { selected -> selectedAiTalkId = selected.id; onDestination(SoundTherapyDestination.AI_DETAIL) },
                    onCollapse = onCollapse
                )
            }
        }
    }
    }
}

@Composable
private fun RestOptionsPage(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    onOpenSoundTherapy: () -> Unit,
    onOpenAiTalk: () -> Unit,
    onCollapse: () -> Unit
) {
    var editingRest by remember { mutableStateOf<String?>(null) }
    var restDraft by remember { mutableStateOf("") }
    var addingRest by remember { mutableStateOf(false) }
    val scrollState = rememberAutoCollapseScrollState(onCollapse)
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        GlassPageHeader("休息方式", Modifier.fillMaxWidth(), "轻触展开，长按编辑，展开后左滑可删除。")
        GlassCard(Modifier.fillMaxWidth()) {
            RestBubblePicker(rests = rests, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Text("休息选项", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        ExpandableGlassStack(
            items = rests,
            key = { it },
            onLongPress = { editingRest = it; restDraft = it },
            onDelete = { name -> onSave(rests.filterNot { it == name }) },
            onAdd = { addingRest = true; restDraft = "" }
        ) { name ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(name, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("恢复空间", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RestModuleBubble("♫", "音疗", onOpenSoundTherapy)
            RestModuleBubble("ai", "ai谈", onOpenAiTalk)
        }
        Spacer(Modifier.height(140.dp))
    }

    if (addingRest || editingRest != null) GlassDialog(
        onDismissRequest = { addingRest = false; editingRest = null },
        title = { Text(if (addingRest) "添加休息方式" else "编辑休息方式") },
        text = { OutlinedTextField(restDraft, { restDraft = it.take(30) }, singleLine = true, label = { Text("名称") }, shape = GlassControlShape, colors = glassTextFieldColors()) },
        actions = {
            GlassActionButton("取消", { addingRest = false; editingRest = null })
            GlassActionButton("保存", {
                val value = restDraft.trim()
                if (value.isNotEmpty()) onSave(if (addingRest) rests + value else rests.map { if (it == editingRest) value else it })
                addingRest = false; editingRest = null
            })
        }
    )
}

@Composable
private fun RestModuleBubble(symbol: String, label: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.size(112.dp).clickable(onClick = onClick),
        shape = CircleShape,
        elevation = 12.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(symbol, color = Blue, fontSize = if (symbol == "ai") 21.sp else 28.sp, fontWeight = FontWeight.SemiBold)
                Text(label, color = Ink, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SoundTherapyLibrary(
    musicNames: List<String>,
    coverPath: (String) -> String?,
    currentMusic: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCollapse: () -> Unit
) {
    var manage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberAutoCollapseScrollState(onCollapse)
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageBackHeader("音疗", "选择一段声音，让节奏慢下来。", onBack)
        if (musicNames.isEmpty()) Text("还没有音乐，点击底部加号导入。", color = Muted)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            musicNames.forEach { name ->
                MusicLibraryBubble(
                    name = name,
                    coverPath = coverPath(name),
                    active = name == currentMusic,
                    playing = name == currentMusic && isPlaying,
                    modifier = Modifier
                        .size(156.dp)
                        .pointerInput(name) {
                            detectTapGestures(onTap = { onSelect(name) }, onLongPress = { manage = name })
                        }
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.size(68.dp).clickable(onClick = onImport), shape = CircleShape) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("＋", color = Blue, fontSize = 28.sp) }
            }
        }
        Spacer(Modifier.height(130.dp))
    }
    manage?.let { name ->
        GlassDialog(
            onDismissRequest = { manage = null },
            title = { Text("管理音乐") },
            text = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            actions = {
                GlassActionButton("取消", { manage = null })
                GlassActionButton("删除", { onDelete(name); manage = null })
            }
        )
    }
}

@Composable
private fun MusicLibraryBubble(name: String, coverPath: String?, active: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    val bitmap = remember(coverPath) { coverPath?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
    val accent = Blue
    GlassCard(modifier, shape = CircleShape, elevation = 12.dp) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(.72f), Color(0xFFC9BEEB).copy(.56f), accent.copy(.32f))))
            }
            Column(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap == null) Text("♫", color = Color.White.copy(.94f), fontSize = 28.sp)
                Text(
                    displayMusicTitle(name),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (active) Text(if (playing) "正在播放" else "已暂停", color = Blue, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SoundTherapyPlayer(
    title: String?, coverPath: String?, isPlaying: Boolean,
    positionMillis: Long, durationMillis: Long, mode: MusicPlaybackMode, error: String?,
    onBack: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onMode: (MusicPlaybackMode) -> Unit
) {
    val bitmap = remember(coverPath) { coverPath?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
    val accent = Blue
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 118.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageBackHeader("正在播放", title ?: "选择一首音乐", onBack)
        Spacer(Modifier.weight(.15f))
        GlassCard(Modifier.size(292.dp), shape = CircleShape, elevation = 18.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap, "音乐封面", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                else Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(.8f), Color(0xFFBFD7F6), Color(0xFFC9BEEB), accent.copy(.55f))))
                    drawCircle(Color.White.copy(.65f), radius = size.minDimension * .34f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                if (bitmap == null) Text("♫", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            }
        }
        Text(title ?: "尚未播放", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val seconds = (durationMillis / 1_000L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        GlassVitalitySlider(
            value = (positionMillis / 1_000L).coerceIn(0, seconds.toLong()).toInt(),
            onValueChange = { onSeek(it * 1_000L) },
            valueRange = 0..seconds,
            enabled = durationMillis > 0L,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMillis), color = Muted, fontSize = 12.sp)
            Text("-${formatTime((durationMillis - positionMillis).coerceAtLeast(0L))}", color = Muted, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassActionButton("上一首", onPrevious, Modifier.weight(1f))
            GlassActionButton(if (isPlaying) "暂停" else "播放", onToggle, Modifier.weight(1f), enabled = title != null)
            GlassActionButton("下一首", onNext, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicPlaybackMode.entries.forEach { item ->
                GlassActionButton(if (item == mode) "● ${item.title}" else item.title, { onMode(item) })
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = RecoveryCoral, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(.1f))
    }
}

@Composable
private fun PageBackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.size(48.dp).clickable(onClick = onBack), shape = CircleShape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹", color = Ink, fontSize = 32.sp) }
        }
        GlassPageHeader(title, Modifier.weight(1f), subtitle)
    }
}

private fun formatTime(value: Long): String {
    val seconds = value.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun displayMusicTitle(name: String): String =
    name.substringBeforeLast('.', missingDelimiterValue = name).ifBlank { name }
