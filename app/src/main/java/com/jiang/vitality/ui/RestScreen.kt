package com.jiang.vitality.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun RestScreen(
    rests: List<String>,
    onSave: (List<String>) -> Unit,
    musicNames: List<String>,
    currentMusic: String?,
    isPlaying: Boolean,
    onImportMusic: (Uri) -> Unit,
    onToggleMusic: (String) -> Unit,
    onDeleteMusic: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportMusic(uri) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        GlassPageHeader(
            title = "休息方式",
            subtitle = "不知道怎么放松时，让气泡帮你挑一个。",
            modifier = Modifier.fillMaxWidth()
        )
        GlassCard(Modifier.fillMaxWidth()) {
            RestBubblePicker(
                rests = rests,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("休息选项", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                rests.forEach { name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), color = Ink)
                        GlassActionButton(
                            text = "删除",
                            onClick = { onSave(rests.toMutableList().apply { remove(name) }) }
                        )
                    }
                }
                GlassOutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加休息方式") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("冥想音乐", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("导入喜欢的音乐，休息时循环播放。", color = Muted, fontSize = 12.sp)
                GlassOutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 导入音乐") }
                if (musicNames.isEmpty()) {
                    Text("还没有音乐，点上面导入。", color = Muted, fontSize = 12.sp)
                } else {
                    musicNames.forEach { name ->
                        val active = currentMusic == name
                        val playing = active && isPlaying
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    color = if (active) Blue else Ink,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (active) {
                                    Text(if (playing) "播放中" else "已暂停", color = Muted, fontSize = 11.sp)
                                }
                            }
                            GlassActionButton(
                                text = if (playing) "暂停" else "播放",
                                onClick = { onToggleMusic(name) }
                            )
                            GlassActionButton(
                                text = "删除",
                                onClick = { onDeleteMusic(name) }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showAdd) GlassDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("添加休息方式") },
        text = {
            OutlinedTextField(
                newItem, { newItem = it.take(30) },
                singleLine = true,
                label = { Text("例如：散步 20 分钟") },
                shape = GlassControlShape,
                colors = glassTextFieldColors()
            )
        },
        actions = {
            GlassActionButton(text = "取消", onClick = { showAdd = false })
            GlassActionButton(text = "保存", onClick = {
                val item = newItem.trim()
                if (item.isNotEmpty()) onSave(rests + item)
                newItem = ""
                showAdd = false
            })
        }
    )
}
