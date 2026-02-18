package org.tomcurran.welfare.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeightRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Weight sync starting")
            repository.sync()
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
