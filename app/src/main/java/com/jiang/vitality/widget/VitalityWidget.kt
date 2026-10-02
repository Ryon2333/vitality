package com.jiang.vitality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jiang.vitality.MainActivity
import com.jiang.vitality.R
import com.jiang.vitality.data.Advice
import com.jiang.vitality.data.VitalityStore

class VitalityWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context,manager,it) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        val delta=when(intent.action) { ACTION_PLUS -> 5; ACTION_MINUS -> -5; else -> return }
        VitalityStore(context).adjust(delta)
        refresh(context)
    }
    companion object {
        private const val ACTION_PLUS="com.jiang.vitality.PLUS"
        private const val ACTION_MINUS="com.jiang.vitality.MINUS"
        fun refresh(context: Context) {
            val manager=AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context,VitalityWidget::class.java)).forEach { update(context,manager,it) }
        }
        private fun update(context: Context,manager: AppWidgetManager,id: Int) {
            val state=VitalityStore(context).snapshot()
            val views=RemoteViews(context.packageName,R.layout.widget)
            views.setTextViewText(R.id.widget_value,"${state.value}")
            views.setTextViewText(R.id.widget_message,if(state.locked) "Recovery mode · 明早 06:00 解锁" else Advice.message(state.value))
            views.setTextViewText(R.id.widget_minus,if(state.locked) "已锁定" else "−5")
            views.setTextViewText(R.id.widget_plus,if(state.locked) "休息中" else "＋5")
            fun action(name: String, request: Int) = PendingIntent.getBroadcast(context,request,Intent(context,VitalityWidget::class.java).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_minus,action(ACTION_MINUS,1001))
            views.setOnClickPendingIntent(R.id.widget_plus,action(ACTION_PLUS,1002))
            val open=PendingIntent.getActivity(context,id,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title,open)
            views.setOnClickPendingIntent(R.id.widget_value,open)
            views.setOnClickPendingIntent(R.id.widget_message,open)
            manager.updateAppWidget(id,views)
        }
    }
}
