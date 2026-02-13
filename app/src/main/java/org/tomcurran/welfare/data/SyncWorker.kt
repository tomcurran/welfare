package org.tomcurran.welfare.data

import android.content.Context
import android.util.Log.d
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private val TAG: String = SyncWorker::class.java.simpleName
    }

    override suspend fun doWork(): Result {
        return try {
            d(TAG, "${SyncWorker::doWork} syncing weight data")
            WeightRepository.create(applicationContext).sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
