package com.jiang.vitality.ui

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
import com.jiang.vitality.ui.backdrop.LiquidSlider
import com.jiang.vitality.ui.backdrop.LiquidToggle
import com.jiang.vitality.ui.backdrop.LocalBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import java.time.LocalDate

@Composable fun SettingsScreen(
    state: Snapshot,
    onReminders: (List<Reminder>) -> Unit,
    onBaseline: (Int) -> Unit,
    onExportData: (Uri) -> Boolean,
    onImportData: (Uri) -> String
) {
    val context = LocalContext.current
    val backdrop = LocalBackdrop.current ?: emptyBackdrop()
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var pendingTimeTitle by remember { mutableStateOf<String?>(null) }
    var pendingTimeReminder by remember { mutableStateOf<Reminder?>(null) }
    var timeHour by remember { mutableIntStateOf(16) }
    var timeMinute by remember { mutableIntStateOf(0) }
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
        GlassPageHeader(title="设置",subtitle="提醒、基线与数据管理。",modifier=Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("状态询问",fontSize=18.sp,color=Ink,fontWeight=FontWeight.Bold)
            Text("轻触展开时间卡片，长按编辑。",fontSize=12.sp,color=Muted)
            ExpandableGlassStack(
                items = state.reminders,
                key = { it.id },
                onLongPress = { editing=it; newTitle=it.title },
                onAdd = { adding=true; newTitle="" }
            ) { item ->
                Row(Modifier.fillMaxSize(), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title,color=Ink,fontWeight=FontWeight.Medium)
                        Text(item.clock,color=Muted,fontSize=12.sp)
                    }
                    LiquidToggle(
                        selected = { item.enabled },
                        onSelect = { checked ->
                            onReminders(state.reminders.map { if(it.id==item.id)it.copy(enabled=checked) else it })
                        },
                        backdrop = backdrop,
                        accentColor = Blue
                    )
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("初始值  ${baseline.toInt()}",color=Ink,fontWeight=FontWeight.Bold)
            Text("仅在没有记录时作为当前状态。",fontSize=12.sp,color=Muted)
            Spacer(Modifier.height(10.dp))
            LiquidSlider(
                value = { baseline },
                onValueChange = {
                    baseline = it
                    onBaseline(it.toInt())
                },
                valueRange = 0f..100f,
                visibilityThreshold = 1f,
                backdrop = backdrop,
                accentColor = Blue,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
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
            GlassOutlinedButton(
                onClick={if(Build.VERSION.SDK_INT>=31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))},
                modifier=Modifier.fillMaxWidth()
            ) { Text("开启精确提醒权限，减少系统延迟") }
        }
        Text("通知权限需在首次启动时授予。ColorOS 若限制后台活动，也需要允许此应用后台运行。",color=Muted,fontSize=12.sp)
    }
    if(adding || editing!=null) GlassDialog(onDismissRequest={adding=false;editing=null},title={Text(if(adding) "新增询问" else "修改询问")},
        text={OutlinedTextField(newTitle,{newTitle=it.take(30)},singleLine=true,label={Text("名称")},shape=GlassControlShape,colors=glassTextFieldColors())},
        actions={
            if(editing!=null) GlassActionButton(text="删除", onClick={ onReminders(state.reminders.filterNot { it.id==editing?.id }); editing=null })
            GlassActionButton(text="取消", onClick={adding=false;editing=null})
            GlassActionButton(text="设置时间", onClick={
            val old=editing
            val title=newTitle.trim().ifEmpty { "状态询问" }
            pendingTimeReminder=old
            pendingTimeTitle=title
            timeHour=old?.hour ?: 16
            timeMinute=old?.minute ?: 0
            adding=false;editing=null
        }) })

    pendingTimeTitle?.let { title ->
        GlassDialog(
            onDismissRequest={ pendingTimeTitle=null; pendingTimeReminder=null },
            title={ Text("设置提醒时间") },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(String.format("%02d:%02d", timeHour, timeMinute), color=Blue, fontSize=30.sp, fontWeight=FontWeight.Bold)
                    Text("小时  $timeHour", color=Ink, fontWeight=FontWeight.Medium)
                    GlassVitalitySlider(value=timeHour,onValueChange={timeHour=it},valueRange=0..23,modifier=Modifier.fillMaxWidth())
                    Text("分钟  $timeMinute", color=Ink, fontWeight=FontWeight.Medium)
                    GlassVitalitySlider(value=timeMinute,onValueChange={timeMinute=it},valueRange=0..59,modifier=Modifier.fillMaxWidth())
                }
            },
            actions={
                GlassActionButton(text="取消",onClick={ pendingTimeTitle=null;pendingTimeReminder=null })
                GlassActionButton(text="保存",onClick={
                    val old=pendingTimeReminder
                    val updated=if(old==null) state.reminders+Reminder((state.reminders.maxOfOrNull { it.id } ?: 0)+1,title,timeHour,timeMinute)
                        else state.reminders.map { if(it.id==old.id)it.copy(title=title,hour=timeHour,minute=timeMinute) else it }
                    onReminders(updated)
                    pendingTimeTitle=null
                    pendingTimeReminder=null
                })
            }
        )
    }
}
