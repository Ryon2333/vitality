package com.jiang.vitality

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jiang.vitality.data.Snapshot
import com.jiang.vitality.data.VitalityStore
import com.jiang.vitality.reminder.AlarmScheduler
import com.jiang.vitality.ui.*
import com.jiang.vitality.ui.backdrop.LocalBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.jiang.vitality.ui.navigation.JiangLiquidNavigationBar
import com.jiang.vitality.widget.VitalityWidget
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private val store by lazy { VitalityStore(this) }
    private var state by mutableStateOf<Snapshot?>(null)
    private var checkin by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = store.snapshot()
        checkin = intent?.action == "checkin" && !state!!.locked

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        setContent { AppScreen() }
    }

    override fun onResume() {
        super.onResume()
        state = store.snapshot()
        AlarmScheduler.scheduleAll(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkin = intent.action == "checkin" && !store.snapshot().locked
    }

    private fun refresh() {
        state = store.snapshot()
        VitalityWidget.refresh(this)
    }

    @Composable
    private fun AppScreen() {
        val snapshot = state ?: return
        var tab by remember { mutableIntStateOf(0) }
        var navigationCollapsed by remember { mutableStateOf(false) }
        var soundTherapyDestination by remember { mutableStateOf(SoundTherapyDestination.REST) }
        var showRecoveryCelebration by remember { mutableStateOf(false) }
        var workUnlockStep by remember { mutableIntStateOf(0) }
        var workUnlockPhrase by remember { mutableStateOf("") }
        val hazeState = rememberHazeState()
        val wallpaperBackdrop = rememberLayerBackdrop()
        val bubbleSounds = rememberBubbleSoundPlayer()
        val context = LocalContext.current
        val meditationPlayer = remember { MeditationPlayer(context) }
        var musicNames by remember { mutableStateOf(store.musicNames()) }
        var recoveryPhotoPath by remember { mutableStateOf<String?>(null) }
        val recoveryCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val captured = recoveryPhotoPath
            recoveryPhotoPath = null
            if (saved && captured != null) {
                store.finalizePhoto(captured)?.let { finalized ->
                    if (!store.recordRecoveryPhoto(finalized)) store.deletePhoto(finalized)
                    refresh()
                }
            } else if (captured != null) {
                store.deletePhoto(captured)
            }
        }
        val musicTracks = musicNames.map { it to store.musicFile(it) }
        DisposableEffect(Unit) {
            onDispose { meditationPlayer.release() }
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                state = store.snapshot()
                VitalityWidget.refresh(this@MainActivity)
            }
        }

        VitalityTheme(recoveryMode = snapshot.locked) {
            CompositionLocalProvider(
                LocalGlassHazeState provides hazeState,
                LocalBackdrop provides wallpaperBackdrop
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .layerBackdrop(wallpaperBackdrop)
                            .hazeSource(hazeState)
                            .background(LocalVitalityColors.current.background)
                    ) {
                        DynamicGeometryBackground(
                            vitality = snapshot.value,
                            recoveryMode = snapshot.locked,
                            modifier = Modifier.matchParentSize()
                        )
                        GlassBackdropSource(
                            recoveryMode = snapshot.locked,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    Scaffold(
                        containerColor = Color.Transparent
                    ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (tab) {
                            0 -> HomeScreen(
                                snapshot,
                                onRecord = { checkin = true },
                                onRecovery = {
                                    if (snapshot.locked) {
                                        workUnlockPhrase = ""
                                        workUnlockStep = 1
                                    } else {
                                        if (store.beginRecovery()) {
                                            showRecoveryCelebration = true
                                            AlarmScheduler.scheduleAll(this@MainActivity)
                                            refresh()
                                        }
                                    }
                                },
                                onRecoveryPhoto = {
                                    val file = store.createPhotoFile()
                                    recoveryPhotoPath = file.absolutePath
                                    val uri = FileProvider.getUriForFile(
                                        this@MainActivity,
                                        "${packageName}.fileprovider",
                                        file
                                    )
                                    recoveryCameraLauncher.launch(uri)
                                },
                                onPlayBubbleSound = { callItADay ->
                                    if (callItADay) bubbleSounds.playCallItADay()
                                    else bubbleSounds.playRandomRestBubble()
                                },
                                onCollapse = { navigationCollapsed = true }
                            )
                            1 -> RestScreen(
                                rests = snapshot.rests,
                                onSave = {
                                    store.saveRests(it)
                                    refresh()
                                },
                                musicNames = musicNames,
                                musicCoverPath = { store.musicCoverFile(it)?.absolutePath },
                                currentMusic = meditationPlayer.currentName,
                                isPlaying = meditationPlayer.isPlaying,
                                positionMillis = meditationPlayer.positionMillis,
                                durationMillis = meditationPlayer.durationMillis,
                                playbackMode = meditationPlayer.mode,
                                playbackError = meditationPlayer.playbackError,
                                destination = soundTherapyDestination,
                                onDestination = { soundTherapyDestination = it },
                                onImportMusic = { uri ->
                                    store.importMusic(uri)?.let { imported ->
                                        musicNames = store.musicNames()
                                        refresh()
                                        meditationPlayer.play(
                                            imported,
                                            musicNames.map { it to store.musicFile(it) }
                                        )
                                    }
                                },
                                onPlayMusic = { name ->
                                    if (meditationPlayer.currentName == name) {
                                        if (!meditationPlayer.isPlaying) meditationPlayer.toggle()
                                    } else {
                                        meditationPlayer.play(name, musicTracks)
                                    }
                                },
                                onToggleMusic = meditationPlayer::toggle,
                                onDeleteMusic = { name ->
                                    if (meditationPlayer.currentName == name) meditationPlayer.stop()
                                    store.deleteMusic(name)
                                    musicNames = store.musicNames()
                                    refresh()
                                },
                                onPreviousMusic = meditationPlayer::previous,
                                onNextMusic = meditationPlayer::next,
                                onSeekMusic = meditationPlayer::seekTo,
                                onPlaybackMode = { meditationPlayer.setMode(it, musicTracks) },
                                onCollapse = { navigationCollapsed = true }
                            )
                            2 -> HistoryScreen(
                                state = snapshot,
                                onDeletePhoto = { readingTime, path ->
                                    store.deleteReadingPhoto(readingTime, path)
                                    refresh()
                                },
                                onCollapse = { navigationCollapsed = true }
                            )
                            else -> SettingsScreen(
                                snapshot,
                                onReminders = { updated ->
                                    val previous = snapshot.reminders
                                    store.saveReminders(updated)
                                    AlarmScheduler.scheduleAll(
                                        this@MainActivity,
                                        previous
                                    )
                                    refresh()
                                },
                                onBaseline = {
                                    store.setBaseline(it)
                                    refresh()
                                },
                                onExportData = { uri ->
                                    runCatching {
                                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                            writer -> writer.write(store.exportData())
                                        } ?: error("无法创建备份文件")
                                    }.isSuccess
                                },
                                onImportData = { uri ->
                                    runCatching {
                                        val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                            it.readText()
                                        } ?: error("无法读取备份文件")
                                        val summary = store.importData(raw)
                                        refresh()
                                        AlarmScheduler.scheduleAll(this@MainActivity)
                                        "已导入 ${summary.readings} 条记录和 ${summary.photos} 张照片"
                                    }.getOrElse { "导入失败：${it.message ?: "文件内容无效"}" }
                                },
                                onCollapse = { navigationCollapsed = true }
                            )
                        }
                    }
                }
                NowPlayingBubble(
                    title = meditationPlayer.currentName.takeUnless {
                        tab == 1 && soundTherapyDestination == SoundTherapyDestination.PLAYER
                    },
                    playing = meditationPlayer.isPlaying,
                    positionMillis = meditationPlayer.positionMillis,
                    durationMillis = meditationPlayer.durationMillis,
                    onOpen = {
                        tab = 1
                        soundTherapyDestination = SoundTherapyDestination.PLAYER
                        navigationCollapsed = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 92.dp, end = 22.dp)
                )
                JiangLiquidNavigationBar(
                    selectedIndex = tab,
                    onSelected = {
                        tab = it
                        navigationCollapsed = false
                    },
                    recoveryMode = snapshot.locked,
                    collapsed = navigationCollapsed,
                    onExpand = { navigationCollapsed = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
                if (showRecoveryCelebration) {
                    RecoveryCelebrationOverlay(
                        onFinished = { showRecoveryCelebration = false },
                        onPlaySound = bubbleSounds::playCallItADay,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            if (checkin && !snapshot.locked) {
                CheckinDialog(
                    snapshot.value,
                    onDismiss = { checkin = false },
                    onSave = { value, note, photoPaths ->
                        store.record(value, note, photoPaths)
                        checkin = false
                        refresh()
                    },
                    createPhotoFile = store::createPhotoFile,
                    finalizePhoto = store::finalizePhoto,
                    importPhoto = store::importPhoto,
                    deletePhoto = store::deletePhoto
                )
            }

            WorkModeUnlockDialogs(
                step = workUnlockStep,
                phrase = workUnlockPhrase,
                onPhraseChange = { workUnlockPhrase = it.take(30) },
                onStepChange = { workUnlockStep = it },
                onDismiss = {
                    workUnlockStep = 0
                    workUnlockPhrase = ""
                },
                onUnlock = {
                    if (
                        workUnlockPhrase == WorkModeUnlockPhrase &&
                        store.endRecoveryEarly()
                    ) {
                        workUnlockStep = 0
                        workUnlockPhrase = ""
                        AlarmScheduler.scheduleAll(this@MainActivity)
                        refresh()
                    }
                }
            )
            }
        }
    }
}

private const val WorkModeUnlockPhrase = "状态是第一优先级"

@Composable
private fun WorkModeUnlockDialogs(
    step: Int,
    phrase: String,
    onPhraseChange: (String) -> Unit,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    when (step) {
        1 -> GlassDialog(
            onDismissRequest = onDismiss,
            title = { Text("休息是前进的一部分") },
            text = { Text("已经进入休息状态。只有确实无法推迟的事情，才值得现在重新开始工作。") },
            actions = {
                GlassActionButton(text = "继续休息", onClick = onDismiss)
                GlassActionButton(text = "我现在有急事", onClick = { onStepChange(2) })
            }
        )
        2 -> GlassDialog(
            onDismissRequest = onDismiss,
            title = { Text("确定要继续？") },
            text = { Text("切回工作状态后，将停止本次自动恢复并重新开放状态记录。") },
            actions = {
                GlassActionButton(text = "返回休息", onClick = onDismiss)
                GlassActionButton(text = "确定", onClick = { onStepChange(3) })
            }
        )
        3 -> GlassDialog(
            onDismissRequest = onDismiss,
            title = { Text("务必维持好自己的状态") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("输入“$WorkModeUnlockPhrase”后，才能解除 Call it a day 模式。")
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = onPhraseChange,
                        singleLine = true,
                        label = { Text("验证短语") },
                        isError = phrase.isNotEmpty() && phrase != WorkModeUnlockPhrase,
                        shape = GlassControlShape,
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            actions = {
                GlassActionButton(text = "继续休息", onClick = onDismiss)
                GlassActionButton(
                    text = "解除工作锁定",
                    onClick = onUnlock,
                    enabled = phrase == WorkModeUnlockPhrase
                )
            }
        )
    }
}

@Composable
private fun CheckinDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onSave: (Int, String, List<String>) -> Unit,
    createPhotoFile: () -> File,
    finalizePhoto: (String) -> String?,
    importPhoto: (android.net.Uri) -> String?,
    deletePhoto: (String) -> Unit
) {
    val context = LocalContext.current
    var value by remember(initial) { mutableIntStateOf(initial) }
    var note by remember { mutableStateOf("") }
    var photoPaths by remember { mutableStateOf(listOf<String>()) }
    var pendingCapturePath by remember { mutableStateOf("") }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val captured = pendingCapturePath
        pendingCapturePath = ""
        if (saved) {
            finalizePhoto(captured)?.let { finalized -> photoPaths = photoPaths + finalized }
        } else {
            deletePhoto(captured)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            photoPaths = photoPaths + uris.mapNotNull(importPhoto)
        }
    }
    val dismissWithCleanup = {
        photoPaths.forEach { deletePhoto(it) }
        if (pendingCapturePath.isNotBlank()) deletePhoto(pendingCapturePath)
        onDismiss()
    }

    GlassDialog(
        onDismissRequest = dismissWithCleanup,
        title = { Text("现在感觉怎么样？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$value / 100",
                    color = Blue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                GlassVitalitySlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0..100,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("这一刻，你有什么感受？") },
                    shape = GlassControlShape,
                    colors = glassTextFieldColors(),
                    minLines = 3,
                    maxLines = 6
                )
                if (photoPaths.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        photoPaths.forEach { path ->
                            val bitmap = remember(path) {
                                BitmapFactory.decodeFile(path)?.asImageBitmap()
                            }
                            bitmap?.let {
                                Box {
                                    Image(
                                        bitmap = it,
                                        contentDescription = "随手拍预览",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                    )
                                    GlassActionButton(
                                        text = "移除",
                                        onClick = { deletePhoto(path); photoPaths = photoPaths - path },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    GlassOutlinedButton(
                        onClick = {
                            runCatching {
                                val target = createPhotoFile()
                                pendingCapturePath = target.absolutePath
                                cameraLauncher.launch(
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        target
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("拍一张") }
                    GlassOutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) { Text("从相册多选") }
                }
            }
        },
        actions = {
            GlassActionButton(text = "稍后", onClick = dismissWithCleanup)
            RecordStateButton(
                text = "保存此刻状态",
                onClick = { onSave(value, note, photoPaths) },
                modifier = Modifier.widthIn(min = 132.dp)
            )
        }
    )
}
