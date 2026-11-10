package com.beichen.dongguatv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.beichen.dongguatv.worker.FollowCheckWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        createFollowChannel()
        scheduleFollowCheck()
    }

    private fun createFollowChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_FOLLOW,
                getString(R.string.channel_follow),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.channel_follow_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun scheduleFollowCheck() {
        val req = PeriodicWorkRequestBuilder<FollowCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "follow_check", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    companion object {
        const val CHANNEL_FOLLOW = "follow_update"
        @JvmStatic
        lateinit var instance: App
            private set
    }
}
