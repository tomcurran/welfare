package org.tomcurran.welfare.data

import android.content.Context
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
        val attempt = runAttemptCount
        return try {
            AppLogger.d(TAG, "Weight sync starting (attempt ${attempt + 1})")
            repository.sync()
            AppLogger.d(TAG, "Weight sync completed")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Weight sync failed", e)
            AppLogger.e(TAG, "Weight sync failed (attempt ${attempt + 1})", e)
            Result.retry()
        }
    }

    companion object {
        private val TAG: String = SyncWorker::class.java.simpleName
    }
}
