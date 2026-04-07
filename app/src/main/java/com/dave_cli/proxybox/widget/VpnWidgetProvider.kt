package com.dave_cli.proxybox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.CoreService

class VpnWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.dave_cli.proxybox.widget.TOGGLE_VPN"

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, VpnWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val serviceIntent = Intent(context, CoreService::class.java)
            if (CoreService.isActive) {
                serviceIntent.action = CoreService.ACTION_STOP
                context.startService(serviceIntent)
            } else {
                serviceIntent.action = CoreService.ACTION_START
                context.startForegroundService(serviceIntent)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val connected = CoreService.isActive
        val views = RemoteViews(context.packageName, R.layout.widget_vpn)

        if (connected) {
            val name = CoreService.activeProfileName ?: "Connected"
            views.setTextViewText(R.id.widget_status, name)
            views.setTextColor(R.id.widget_status, 0xFF4ADE80.toInt())
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_connected)
        } else {
            views.setTextViewText(R.id.widget_status, "Disconnected")
            views.setTextColor(R.id.widget_status, 0xFFAAAACC.toInt())
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
        }

        val toggleIntent = Intent(context, VpnWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
