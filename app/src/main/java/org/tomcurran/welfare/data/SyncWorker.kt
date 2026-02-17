package org.tomcurran.welfare.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Weight sync starting")
            WeightRepository.getInstance(applicationContext).sync()
            Log.d(TAG, "Weight sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weight sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private val TAG: String = SyncWorker::class.java.simpleName
    }
}
