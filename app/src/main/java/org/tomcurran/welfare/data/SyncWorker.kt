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
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Weight sync failed permanently due to security exception (permissions revoked?)", e)
            Result.failure()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                AppLogger.d(TAG, "Weight sync cancelled")
                throw e
            }
            AppLogger.e(TAG, "Weight sync failed (attempt ${attempt + 1})", e)
            Result.retry()
        }
    }

    companion object {
        private val TAG: String = SyncWorker::class.java.simpleName
    }
}
