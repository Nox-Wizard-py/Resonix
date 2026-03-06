package com.noxwizard.resonix.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noxwizard.resonix.BuildConfig

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Updater.getLatestVersionName().onSuccess { latestVersion ->
                if (latestVersion != BuildConfig.VERSION_NAME) {
                    UpdateNotificationManager.notifyIfNewVersion(
                        applicationContext,
                        latestVersion,
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
