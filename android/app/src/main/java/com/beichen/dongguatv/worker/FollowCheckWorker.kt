package com.beichen.dongguatv.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.FollowUpdate
import com.beichen.dongguatv.data.Repository

/** 每 6 小时检查追剧更新，有新集数发本地通知 */
class FollowCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        return try {
            val updates = repo.checkFollowUpdates()
            if (updates.isNotEmpty()) showNotification(updates)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(updates: List<FollowUpdate>) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val content = updates.joinToString("\n") {
            "${it.follow.vodName} 更新到第 ${it.latestEpisode} 集（+${it.newCount}）"
        }
        val n = NotificationCompat.Builder(ctx, "follow_update")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("追剧更新 · ${updates.size} 部")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }
}
