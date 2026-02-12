package org.tomcurran.welfare.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            WeightRepository.create(applicationContext).sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
