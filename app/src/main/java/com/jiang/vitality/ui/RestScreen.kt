package com.jiang.vitality.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.ui.backdrop.LiquidSlider
import com.jiang.vitality.ui.backdrop.LocalBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop

@Composable
fun RestScreen(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    musicNames: List<String>,
    currentMusic: String?,
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    playbackMode: MusicPlaybackMode,
    playbackError: String?,
    onImportMusic: (Uri) -> Unit,
    onToggleMusic: (String) -> Unit,
    onDeleteMusic: (String) -> Unit,
    onPreviousMusic: () -> Unit,
    onNextMusic: () -> Unit,
    onSeekMusic: (Long) -> Unit,
    onPlaybackMode: (MusicPlaybackMode) -> Unit
) {
    var editingRest by remember { mutableStateOf<String?>(null) }
    var restDraft by remember { mutableStateOf("") }
    var addingRest by remember { mutableStateOf(false) }
    var deletingMusic by remember { mutableStateOf<String?>(null) }
    val backdrop = LocalBackdrop.current ?: emptyBackdrop()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportMusic(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        GlassPageHeader(
            title = "休息方式",
            subtitle = "轻触展开，长按编辑。",
            modifier = Modifier.fillMaxWidth()
        )
        GlassCard(Modifier.fillMaxWidth()) {
            RestBubblePicker(rests = rests, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("休息选项", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            ExpandableGlassStack(
                items = rests,
                key = { it },
                onLongPress = { editingRest = it; restDraft = it },
                onAdd = { addingRest = true; restDraft = "" }
            ) { name ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium)
                    Text("长按编辑", color = Muted, fontSize = 11.sp)
                }
            }
        }

        Text("冥想音乐", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        if (currentMusic != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentMusic, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LiquidSlider(
                        value = { if (durationMillis > 0L) positionMillis.toFloat().coerceAtMost(durationMillis.toFloat()) else 0f },
                        onValueChange = { onSeekMusic(it.toLong()) },
                        valueRange = 0f..durationMillis.coerceAtLeast(1L).toFloat(),
                        visibilityThreshold = 250f,
                        backdrop = backdrop,
                        accentColor = Blue,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(positionMillis), color = Muted, fontSize = 11.sp)
                        Text(formatTime(durationMillis), color = Muted, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassActionButton("上一首", onPreviousMusic, Modifier.weight(1f))
                        GlassActionButton(if (isPlaying) "暂停" else "播放", { onToggleMusic(currentMusic) }, Modifier.weight(1f))
                        GlassActionButton("下一首", onNextMusic, Modifier.weight(1f))
                    }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("播放方式", color = Ink, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MusicPlaybackMode.entries.forEach { mode ->
                        GlassActionButton(
                            text = if (mode == playbackMode) "● ${mode.title}" else mode.title,
                            onClick = { onPlaybackMode(mode) }
                        )
                    }
                }
                if (!playbackError.isNullOrBlank()) Text(playbackError, color = RecoveryCoral, fontSize = 12.sp)
            }
        }

        if (musicNames.isEmpty()) {
            GlassOutlinedButton(
                onClick = { importLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 导入音乐") }
        } else {
            ExpandableGlassStack(
                items = musicNames,
                key = { it },
                onLongPress = { deletingMusic = it },
                onAdd = { importLauncher.launch(arrayOf("audio/*")) }
            ) { name ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = if (name == currentMusic) Blue else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (name == currentMusic) Text(if (isPlaying) "正在播放" else "已暂停", color = Muted, fontSize = 11.sp)
                    }
                    GlassActionButton(if (name == currentMusic && isPlaying) "暂停" else "播放", { onToggleMusic(name) })
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }

    if (addingRest || editingRest != null) GlassDialog(
        onDismissRequest = { addingRest = false; editingRest = null },
        title = { Text(if (addingRest) "添加休息方式" else "编辑休息方式") },
        text = {
            OutlinedTextField(restDraft, { restDraft = it.take(30) }, singleLine = true, label = { Text("名称") }, shape = GlassControlShape, colors = glassTextFieldColors())
        },
        actions = {
            editingRest?.let { old ->
                GlassActionButton("删除", { onSave(rests.filterNot { it == old }); editingRest = null })
            }
            GlassActionButton("取消", { addingRest = false; editingRest = null })
            GlassActionButton("保存", {
                val value = restDraft.trim()
                if (value.isNotEmpty()) {
                    onSave(if (addingRest) rests + value else rests.map { if (it == editingRest) value else it })
                }
                addingRest = false; editingRest = null
            })
        }
    )

    deletingMusic?.let { name ->
        GlassDialog(
            onDismissRequest = { deletingMusic = null },
            title = { Text("管理音乐") },
            text = { Text("长按选中了“$name”。删除后需要重新导入才能恢复。") },
            actions = {
                GlassActionButton("取消", { deletingMusic = null })
                GlassActionButton("删除", { onDeleteMusic(name); deletingMusic = null })
            }
        )
    }
}

private fun formatTime(value: Long): String {
    val seconds = (value.coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
