package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.AiConversation
import com.jiang.vitality.data.FossilDiscovery
import com.jiang.vitality.data.MediaReview
import com.jiang.vitality.data.MediaReviewDraft
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class SoundTherapyDestination {
    REST, LIBRARY, PLAYER, AI_LIBRARY, AI_EDITOR, AI_DETAIL,
    REVIEW_LIBRARY, REVIEW_EDITOR, REVIEW_DETAIL, EXPLORE
}

@Composable
fun RestScreen(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    aiTalks: List<AiConversation>,
    onSaveAiTalk: (String, String, String, List<String>) -> AiConversation?,
    onUpdateAiTalk: (Long, String, List<String>, Boolean) -> Unit,
    onDeleteAiTalk: (Long) -> Unit,
    reviews: List<MediaReview>,
    onSaveReview: (MediaReviewDraft) -> MediaReview?,
    onDeleteReview: (Long) -> Unit,
    discoveries: List<FossilDiscovery>,
    onDig: () -> FossilDiscovery?,
    createPhotoFile: () -> File,
    finalizePhoto: (String) -> String?,
    importPhoto: (Uri) -> String?,
    deletePhoto: (String) -> Unit,
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
    var selectedReviewId by remember { mutableStateOf<Long?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportMusic(uri)
    }
    BackHandler(enabled = destination != SoundTherapyDestination.REST) {
        onDestination(
            when (destination) {
                SoundTherapyDestination.PLAYER -> SoundTherapyDestination.LIBRARY
                SoundTherapyDestination.AI_EDITOR, SoundTherapyDestination.AI_DETAIL -> SoundTherapyDestination.AI_LIBRARY
                SoundTherapyDestination.REVIEW_EDITOR, SoundTherapyDestination.REVIEW_DETAIL -> SoundTherapyDestination.REVIEW_LIBRARY
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
            onOpenReviews = { onDestination(SoundTherapyDestination.REVIEW_LIBRARY) },
            onOpenExplore = { onDestination(SoundTherapyDestination.EXPLORE) },
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
            queue = musicNames,
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
            categories = aiTalks.map { it.category }.distinct().sorted(),
            onBack = { onDestination(SoundTherapyDestination.AI_LIBRARY) },
            onSave = { title, answer, category, tags ->
                onSaveAiTalk(title, answer, category, tags)?.let { saved ->
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
                    categories = aiTalks.map { it.category }.distinct().sorted(),
                    onBack = { onDestination(SoundTherapyDestination.AI_LIBRARY) },
                    onUpdateMetadata = { category, tags, favorite ->
                        onUpdateAiTalk(talk.id, category, tags, favorite)
                    },
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
        SoundTherapyDestination.REVIEW_LIBRARY -> ReviewLibrary(
            reviews = reviews,
            onBack = { onDestination(SoundTherapyDestination.REST) },
            onAdd = { selectedReviewId = null; onDestination(SoundTherapyDestination.REVIEW_EDITOR) },
            onOpen = { review -> selectedReviewId = review.id; onDestination(SoundTherapyDestination.REVIEW_DETAIL) },
            onCollapse = onCollapse
        )
        SoundTherapyDestination.REVIEW_EDITOR -> ReviewEditor(
            review = selectedReviewId?.let { id -> reviews.firstOrNull { it.id == id } },
            onBack = { onDestination(if (selectedReviewId == null) SoundTherapyDestination.REVIEW_LIBRARY else SoundTherapyDestination.REVIEW_DETAIL) },
            onSave = { draft ->
                onSaveReview(draft)?.let { saved ->
                    selectedReviewId = saved.id
                    onDestination(SoundTherapyDestination.REVIEW_DETAIL)
                }
            },
            createPhotoFile = createPhotoFile,
            finalizePhoto = finalizePhoto,
            importPhoto = importPhoto,
            deletePhoto = deletePhoto
        )
        SoundTherapyDestination.REVIEW_DETAIL -> {
            val review = selectedReviewId?.let { id -> reviews.firstOrNull { it.id == id } }
            if (review == null) {
                ReviewLibrary(
                    reviews = reviews,
                    onBack = { onDestination(SoundTherapyDestination.REST) },
                    onAdd = { selectedReviewId = null; onDestination(SoundTherapyDestination.REVIEW_EDITOR) },
                    onOpen = { selected -> selectedReviewId = selected.id; onDestination(SoundTherapyDestination.REVIEW_DETAIL) },
                    onCollapse = onCollapse
                )
            } else {
                ReviewDetail(
                    review = review,
                    onBack = { onDestination(SoundTherapyDestination.REVIEW_LIBRARY) },
                    onEdit = { onDestination(SoundTherapyDestination.REVIEW_EDITOR) },
                    onDelete = {
                        onDeleteReview(review.id)
                        selectedReviewId = null
                        onDestination(SoundTherapyDestination.REVIEW_LIBRARY)
                    }
                )
            }
        }
        SoundTherapyDestination.EXPLORE -> ExplorationScreen(
            discoveries = discoveries,
            onDig = onDig,
            onBack = { onDestination(SoundTherapyDestination.REST) },
            onCollapse = onCollapse
        )
    }
    }
}

@Composable
private fun RestOptionsPage(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    onOpenSoundTherapy: () -> Unit,
    onOpenAiTalk: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenExplore: () -> Unit,
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
        FloatingRestModules(onOpenSoundTherapy, onOpenAiTalk, onOpenReviews, onOpenExplore)
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
private fun FloatingRestModules(onSound: () -> Unit, onAi: () -> Unit, onReview: () -> Unit, onExplore: () -> Unit) {
    val modules = remember(onSound, onAi, onReview, onExplore) {
        listOf(
            Triple("♫", "音疗", onSound),
            Triple("ai", "ai谈", onAi),
            Triple("评", "我评", onReview),
            Triple("⌁", "探索", onExplore)
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bubblePx = with(density) { 102.dp.toPx() }
        val fieldWidth = constraints.maxWidth.toFloat()
        val fieldHeight = constraints.maxHeight.toFloat()
        modules.forEachIndexed { index, (symbol, label, action) ->
            val driftX = remember(index) { androidx.compose.animation.core.Animatable(0f) }
            val driftY = remember(index) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(index) {
                val random = Random(9301L + index * 71L)
                while (isActive) {
                    coroutineScope {
                        val duration = 5_800 + random.nextInt(2_400)
                        launch { driftX.animateTo(random.nextFloat() * 14f - 7f, tween(duration)) }
                        launch { driftY.animateTo(random.nextFloat() * 12f - 6f, tween(duration + 500)) }
                    }
                }
            }
            val column = index % 2
            val row = index / 2
            val baseX = fieldWidth * (.25f + column * .50f) - bubblePx / 2f
            val baseY = fieldHeight * (.28f + row * .48f) - bubblePx / 2f
            RestModuleBubble(
                symbol,
                label,
                action,
                Modifier.graphicsLayer {
                    translationX = baseX + with(density) { driftX.value.dp.toPx() }
                    translationY = baseY + with(density) { driftY.value.dp.toPx() }
                }
            )
        }
    }
}

@Composable
private fun RestModuleBubble(symbol: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.size(102.dp).clickable(onClick = onClick),
        shape = CircleShape,
        elevation = 12.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(symbol, color = Blue, fontSize = if (symbol == "ai") 21.sp else 27.sp, fontWeight = FontWeight.SemiBold)
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
    title: String?, queue: List<String>, coverPath: String?, isPlaying: Boolean,
    positionMillis: Long, durationMillis: Long, mode: MusicPlaybackMode, error: String?,
    onBack: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onMode: (MusicPlaybackMode) -> Unit
) {
    val bitmap = remember(coverPath) { coverPath?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
    val accent = Blue
    var showModes by remember { mutableStateOf(false) }
    val queueIndex = queue.indexOf(title).takeIf { it >= 0 }
    val playerScroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(playerScroll)
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 148.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageBackHeader("正在播放", title ?: "选择一首音乐", onBack)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(224.dp)) {
            GlassCard(Modifier.fillMaxSize(), shape = CircleShape, elevation = 18.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(bitmap, "音乐封面", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    else Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(.8f), Color(0xFFBFD7F6), Color(0xFFC9BEEB), accent.copy(.55f))))
                        drawCircle(Color.White.copy(.65f), radius = size.minDimension * .34f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                    if (bitmap == null) Text("♫", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                }
            }
            GlassCard(
                Modifier.align(Alignment.BottomEnd).size(72.dp).clickable { showModes = true },
                shape = CircleShape,
                elevation = 12.dp
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(playbackModeIcon(mode), color = Blue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("模式", color = Muted, fontSize = 9.sp)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title?.let(::displayMusicTitle) ?: "尚未播放", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                queueIndex?.let { "第 ${it + 1} 首 · 共 ${queue.size} 首" } ?: "从音疗曲库选择音乐",
                color = Muted,
                fontSize = 12.sp
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerControlButton(PlayerGlyph.PREVIOUS, "上一首", 58.dp, title != null, onPrevious)
            PlayerControlButton(if (isPlaying) PlayerGlyph.PAUSE else PlayerGlyph.PLAY, if (isPlaying) "暂停" else "播放", 78.dp, title != null, onToggle)
            PlayerControlButton(PlayerGlyph.NEXT, "下一首", 58.dp, title != null, onNext)
        }
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
        if (!error.isNullOrBlank()) Text(error, color = RecoveryCoral, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
    if (showModes) {
        GlassDialog(
            onDismissRequest = { showModes = false },
            title = { Text("选择播放方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicPlaybackMode.entries.forEach { item ->
                        GlassOutlinedButton(
                            onClick = { onMode(item); showModes = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (item == mode) "●  ${playbackModeIcon(item)}  ${item.title}" else "${playbackModeIcon(item)}  ${item.title}",
                                color = if (item == mode) Blue else Ink
                            )
                        }
                    }
                }
            },
            actions = { GlassActionButton("取消", { showModes = false }) }
        )
    }
}

private enum class PlayerGlyph { PREVIOUS, PLAY, PAUSE, NEXT }

@Composable
private fun PlayerControlButton(
    glyph: PlayerGlyph,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .90f else 1f,
        animationSpec = spring(dampingRatio = JiangMotion.PressDamping, stiffness = JiangMotion.PressStiffness),
        label = "player-$label-press"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassCard(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    if (enabled) onClick { onClick(); true } else disabled()
                }
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            shape = CircleShape,
            elevation = if (size > 70.dp) 14.dp else 8.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val iconColor = if (enabled) Ink else Muted.copy(alpha = .45f)
                Canvas(Modifier.size(if (size > 70.dp) 31.dp else 24.dp)) {
                    val unit = this.size.minDimension
                    val stroke = unit * .105f
                    val cap = androidx.compose.ui.graphics.StrokeCap.Round
                    when (glyph) {
                        PlayerGlyph.PLAY -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(unit * .33f, unit * .22f)
                                quadraticTo(unit * .31f, unit * .18f, unit * .40f, unit * .23f)
                                lineTo(unit * .78f, unit * .46f)
                                quadraticTo(unit * .85f, unit * .50f, unit * .78f, unit * .54f)
                                lineTo(unit * .40f, unit * .77f)
                                quadraticTo(unit * .31f, unit * .82f, unit * .33f, unit * .72f)
                                close()
                            }
                            drawPath(path, iconColor)
                        }
                        PlayerGlyph.PAUSE -> {
                            drawRoundRect(iconColor, topLeft = Offset(unit * .27f, unit * .20f), size = androidx.compose.ui.geometry.Size(unit * .16f, unit * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * .08f))
                            drawRoundRect(iconColor, topLeft = Offset(unit * .57f, unit * .20f), size = androidx.compose.ui.geometry.Size(unit * .16f, unit * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * .08f))
                        }
                        PlayerGlyph.PREVIOUS, PlayerGlyph.NEXT -> {
                            val previous = glyph == PlayerGlyph.PREVIOUS
                            val barX = if (previous) unit * .23f else unit * .77f
                            drawLine(iconColor, Offset(barX, unit * .27f), Offset(barX, unit * .73f), stroke, cap)
                            val path = androidx.compose.ui.graphics.Path().apply {
                                if (previous) {
                                    moveTo(unit * .72f, unit * .24f); lineTo(unit * .35f, unit * .50f); lineTo(unit * .72f, unit * .76f)
                                } else {
                                    moveTo(unit * .28f, unit * .24f); lineTo(unit * .65f, unit * .50f); lineTo(unit * .28f, unit * .76f)
                                }
                                close()
                            }
                            drawPath(path, iconColor)
                        }
                    }
                }
            }
        }
        Text(label, color = if (enabled) Muted else Muted.copy(alpha = .4f), fontSize = 10.sp)
    }
}

private fun playbackModeIcon(mode: MusicPlaybackMode): String = when (mode) {
    MusicPlaybackMode.PLAY_ONCE -> "1×"
    MusicPlaybackMode.CONTINUOUS -> "→"
    MusicPlaybackMode.REPEAT_ALL -> "↻"
    MusicPlaybackMode.REPEAT_ONE -> "↻1"
    MusicPlaybackMode.SHUFFLE -> "⇄"
}

@Composable
fun PageBackHeader(title: String, subtitle: String, onBack: () -> Unit) {
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
