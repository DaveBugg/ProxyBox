package com.dave_cli.proxybox.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GeoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "GeoUpdateWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val updated = GeoFileManager.updateAll(applicationContext)

            if (updated && CoreService.isActive) {
                Log.i(TAG, "Geo files updated while VPN active — restarting core")
                val stopIntent = Intent(applicationContext, CoreService::class.java).apply {
                    action = CoreService.ACTION_STOP
                }
                applicationContext.startService(stopIntent)

                kotlinx.coroutines.delay(500)

                val startIntent = Intent(applicationContext, CoreService::class.java).apply {
                    action = CoreService.ACTION_START
                }
                applicationContext.startService(startIntent)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Geo update failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
