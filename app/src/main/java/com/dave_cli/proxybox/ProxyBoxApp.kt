package com.dave_cli.proxybox

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dave_cli.proxybox.core.GeoFileManager
import com.dave_cli.proxybox.core.GeoUpdateWorker
import java.util.concurrent.TimeUnit

class ProxyBoxApp : Application() {

    companion object {
        private const val TAG = "ProxyBoxApp"
        private const val STALE_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
    }

    override fun onCreate() {
        super.onCreate()
        scheduleGeoUpdate()
        checkStaleGeoFiles()
    }

    private fun scheduleGeoUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GeoUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "geo_auto_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun checkStaleGeoFiles() {
        val lastUpdate = GeoFileManager.getLastUpdateTime(this)
        val age = System.currentTimeMillis() - lastUpdate

        if (lastUpdate == 0L || age > STALE_THRESHOLD_MS) {
            Log.i(TAG, "Geo files stale (age=${age / 86_400_000}d), scheduling immediate update")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateRequest = OneTimeWorkRequestBuilder<GeoUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueue(immediateRequest)
        }
    }
}
