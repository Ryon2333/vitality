package com.jiang.vitality.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jiang.vitality.widget.VitalityWidget

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
        VitalityWidget.refresh(context)
    }
}
