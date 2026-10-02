package com.jiang.vitality.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jiang.vitality.data.Reminder
import com.jiang.vitality.data.VitalityStore
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    private fun reminderIntent(context: Context, id: Int) = PendingIntent.getBroadcast(context,id,
        Intent(context,ReminderReceiver::class.java).putExtra("reminder_id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(context: Context, id: Int) = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderIntent(context,id))

    fun scheduleAll(context: Context, previous: List<Reminder> = emptyList()) {
        previous.forEach { cancel(context,it.id) }
        val store=VitalityStore(context)
        store.reminders().forEach { schedule(context,it) }
        scheduleRecovery(context)
    }

    fun schedule(context: Context, item: Reminder) {
        cancel(context,item.id)
        if (!item.enabled || VitalityStore(context).snapshot().locked) return
        var next=LocalDateTime.now().withHour(item.hour).withMinute(item.minute).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next=next.plusDays(1)
        set(context,next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),reminderIntent(context,item.id))
    }

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(context,100000,
        Intent(context,RecoveryTickReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun scheduleRecovery(context: Context) {
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(tickIntent(context))
        val state=VitalityStore(context).snapshot()
        if (!state.locked) return
        val now=System.currentTimeMillis()
        val start=context.getSharedPreferences("vitality_v2",Context.MODE_PRIVATE).getLong("recovery_start",now)
        val elapsed=((now-start)/3_600_000L).coerceAtLeast(0)
        val nextHour=start+(elapsed+1)*3_600_000L
        set(context,minOf(nextHour,state.unlockAt),tickIntent(context))
    }

    private fun set(context: Context, time: Long, intent: PendingIntent) {
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent)
    }
}
