package com.jiang.vitality.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.jiang.vitality.MainActivity
import com.jiang.vitality.R
import com.jiang.vitality.data.VitalityStore
import com.jiang.vitality.data.Advice

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store=VitalityStore(context)
        val item=store.reminders().firstOrNull { it.id==intent.getIntExtra("reminder_id",-1) } ?: return
        if (!item.enabled || store.snapshot().locked) return
        AlarmScheduler.schedule(context,item)
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("checkin","状态询问",NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val open=PendingIntent.getActivity(context,item.id,Intent(context,MainActivity::class.java).setAction("checkin"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(item.id,Notification.Builder(context,"checkin").setSmallIcon(R.drawable.ic_app)
            .setContentTitle("${item.title} · 现在感觉怎么样？").setContentText(Advice.suggestion(store.snapshot().value))
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
