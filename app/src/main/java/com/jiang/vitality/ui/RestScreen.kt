package com.jiang.vitality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RestScreen(rests: List<String>, onSave: (List<String>) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Text("休息方式", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Text("不知道怎么放松时，让气泡帮你挑一个。", color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
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
                        TextButton(onClick = { onSave(rests.toMutableList().apply { remove(name) }) }) { Text("删除") }
                    }
                }
                GlassOutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加休息方式") }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        shape = GlassDialogShape,
        containerColor = GlassDialogColor,
        tonalElevation = 0.dp,
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
        confirmButton = {
            TextButton(onClick = {
                val item = newItem.trim()
                if (item.isNotEmpty()) onSave(rests + item)
                newItem = ""
                showAdd = false
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
    )
}
