package com.dave_cli.proxybox.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dave_cli.proxybox.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed received")
        // Only auto-start if user had previously connected
        val prefs = context.getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_start", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val profile = db.profileDao().getSelectedProfile()
                if (profile != null) {
                    val si = Intent(context, CoreService::class.java)
                    context.startForegroundService(si)
                }
            }
        }
    }
}
