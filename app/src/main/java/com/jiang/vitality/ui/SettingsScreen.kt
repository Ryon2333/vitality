package com.jiang.vitality.ui

import android.app.TimePickerDialog
import android.os.Build
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiang.vitality.data.Reminder
import com.jiang.vitality.data.Snapshot
import com.jiang.vitality.reminder.AlarmScheduler
import java.time.LocalDate

@Composable fun SettingsScreen(
    state: Snapshot,
    onReminders: (List<Reminder>) -> Unit,
    onBaseline: (Int) -> Unit,
    onExportData: (Uri) -> Boolean,
    onImportData: (Uri) -> String
) {
    val context=LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf("") }
    var baseline by remember(state.baseline) { mutableFloatStateOf(state.baseline.toFloat()) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) transferMessage = if (onExportData(uri)) "备份已导出" else "导出失败，请重试"
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transferMessage = onImportData(uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("设置",color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold)
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("状态询问",fontSize=18.sp,color=Ink,fontWeight=FontWeight.Bold)
            Text("固定时间发通知；时间可逐项修改。",fontSize=12.sp,color=Muted)
            state.reminders.forEach { item ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title,color=Ink,fontWeight=FontWeight.Medium)
                        Text(item.clock,color=Muted,fontSize=12.sp)
                    }
                    TextButton(onClick={ editing=item; newTitle=item.title }) { Text("修改") }
                    Switch(checked=item.enabled,onCheckedChange={ checked -> onReminders(state.reminders.map { if(it.id==item.id)it.copy(enabled=checked) else it }) })
                }
            }
            GlassOutlinedButton(onClick={adding=true;newTitle=""},modifier=Modifier.fillMaxWidth()) { Text("＋ 新增询问") }
        } }
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("初始值  ${baseline.toInt()}",color=Ink,fontWeight=FontWeight.Bold)
            Text("仅在没有记录时作为当前状态。",fontSize=12.sp,color=Muted)
            Slider(value=baseline,onValueChange={baseline=it},valueRange=0f..100f,onValueChangeFinished={onBaseline(baseline.toInt())})
        } }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("数据与迁移",fontSize=18.sp,color=Ink,fontWeight=FontWeight.Bold)
                Text("备份包含状态、心得、照片、提醒和休息选项。导入会替换当前数据。",fontSize=12.sp,color=Muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    GlassOutlinedButton(
                        onClick={
                            exportLauncher.launch("vitality-${LocalDate.now()}.json")
                        },
                        modifier=Modifier.weight(1f)
                    ) { Text("导出备份") }
                    GlassOutlinedButton(
                        onClick={importLauncher.launch(arrayOf("application/json","text/plain"))},
                        modifier=Modifier.weight(1f)
                    ) { Text("导入备份") }
                }
                if(transferMessage.isNotBlank()) Text(transferMessage,color=Blue,fontSize=12.sp)
            }
        }
        if(!AlarmScheduler.exactAllowed(context)) GlassCard(Modifier.fillMaxWidth()) {
            TextButton(onClick={if(Build.VERSION.SDK_INT>=31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))}) {
                Text("开启精确提醒权限，减少系统延迟")
            }
        }
        Text("通知权限需在首次启动时授予。ColorOS 若限制后台活动，也需要允许此应用后台运行。",color=Muted,fontSize=12.sp)
    }
    if(adding || editing!=null) AlertDialog(onDismissRequest={adding=false;editing=null},shape=GlassDialogShape,containerColor=GlassDialogColor,tonalElevation=0.dp,title={Text(if(adding) "新增询问" else "修改询问")},
        text={OutlinedTextField(newTitle,{newTitle=it.take(30)},singleLine=true,label={Text("名称")},shape=GlassControlShape,colors=glassTextFieldColors())},
        confirmButton={TextButton(onClick={
            val old=editing
            val title=newTitle.trim().ifEmpty { "状态询问" }
            adding=false;editing=null
            TimePickerDialog(context,{_,hour,minute->
                val updated=if(old==null) state.reminders+Reminder((state.reminders.maxOfOrNull { it.id } ?: 0)+1,title,hour,minute)
                    else state.reminders.map { if(it.id==old.id)it.copy(title=title,hour=hour,minute=minute) else it }
                onReminders(updated)
            },old?.hour ?: 16,old?.minute ?: 0,true).show()
        }) {Text("设置时间")}},
        dismissButton={ Row {
            if(editing!=null) TextButton(onClick={ onReminders(state.reminders.filterNot { it.id==editing?.id }); editing=null }){Text("删除")}
            TextButton(onClick={adding=false;editing=null}){Text("取消")}
        } })
}
