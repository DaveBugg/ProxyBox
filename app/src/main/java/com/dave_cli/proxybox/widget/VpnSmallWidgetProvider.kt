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

class VpnSmallWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.dave_cli.proxybox.widget.TOGGLE_VPN_SMALL"
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
        val views = RemoteViews(context.packageName, R.layout.widget_vpn_small)

        if (connected) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_connected)
            views.setInt(R.id.widget_icon, "setColorFilter", 0xFF4ADE80.toInt())
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
            views.setInt(R.id.widget_icon, "setColorFilter", 0xFF888899.toInt())
        }

        val toggleIntent = Intent(context, VpnSmallWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
