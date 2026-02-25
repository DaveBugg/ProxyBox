package com.dave_cli.proxybox

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dave_cli.proxybox.core.GeoUpdateWorker
import java.util.concurrent.TimeUnit

class ProxyBoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleGeoUpdate()
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
}
