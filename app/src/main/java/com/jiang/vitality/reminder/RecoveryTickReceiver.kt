package com.jiang.vitality.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jiang.vitality.data.VitalityStore
import com.jiang.vitality.widget.VitalityWidget

class RecoveryTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state=VitalityStore(context).snapshot()
        VitalityWidget.refresh(context)
        if (state.locked) AlarmScheduler.scheduleRecovery(context) else AlarmScheduler.scheduleAll(context)
    }
}
