package org.tomcurran.welfare.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class WeightRepository(
    private val healthConnectClient: HealthConnectClient,
    private val dao: WeightDao,
    private val prefs: SharedPreferences,
) {
    fun entries(): Flow<List<WeightEntity>> = dao.getAllByTimeDesc()

    fun resetSync() {
        prefs.edit { remove(KEY_CHANGES_TOKEN) }
    }

    suspend fun sync() {
        val token = prefs.getString(KEY_CHANGES_TOKEN, null)
        if (token == null) {
            initialLoad()
        } else {
            try {
                incrementalSync(token)
            } catch (e: Exception) {
                Log.w(TAG, "Incremental sync failed, falling back to initial load", e)
                resetSync()
                initialLoad()
            }
        }
    }

    private suspend fun initialLoad() {
        val now = Instant.now()
        val oneYearAgo = now.minus(INITIAL_LOAD_DAYS, ChronoUnit.DAYS)
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneYearAgo, now),
                    pageToken = pageToken,
                )
            )
            dao.upsertAll(response.records.map { record ->
                WeightEntity(
                    healthConnectId = record.metadata.id,
                    weight = record.weight.inKilograms,
                    time = record.time.toEpochMilli(),
                )
            })
            pageToken = response.pageToken
        } while (pageToken != null)
        val newToken = healthConnectClient.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(WeightRecord::class))
        )
        prefs.edit { putString(KEY_CHANGES_TOKEN, newToken) }
    }

    private suspend fun incrementalSync(token: String) {
        var currentToken = token
        do {
            val changesResponse = healthConnectClient.getChanges(currentToken)
            val upserts = mutableListOf<WeightEntity>()
            for (change in changesResponse.changes) {
                when (change) {
                    is UpsertionChange -> {
                        val record = change.record
                        if (record is WeightRecord) {
                            upserts.add(
                                WeightEntity(
                                    healthConnectId = record.metadata.id,
                                    weight = record.weight.inKilograms,
                                    time = record.time.toEpochMilli(),
                                )
                            )
                        }
                    }
                    is DeletionChange -> {
                        dao.deleteByHealthConnectId(change.recordId)
                    }
                }
            }
            if (upserts.isNotEmpty()) {
                dao.upsertAll(upserts)
            }
            currentToken = changesResponse.nextChangesToken
        } while (changesResponse.hasMore)
        prefs.edit { putString(KEY_CHANGES_TOKEN, currentToken) }
    }

    companion object {
        private val TAG: String = WeightRepository::class.java.simpleName
        internal const val PREFS_NAME = "welfare_prefs"
        internal const val KEY_CHANGES_TOKEN = "health_connect_changes_token"
        private const val INITIAL_LOAD_DAYS = 365L
        private const val WORK_NAME = "weight_sync"

        @Volatile
        private var INSTANCE: WeightRepository? = null

        fun getInstance(context: Context): WeightRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }

        private fun create(context: Context): WeightRepository {
            val appContext = context.applicationContext
            return WeightRepository(
                healthConnectClient = HealthConnectClient.getOrCreate(appContext),
                dao = WeightDatabase.getInstance(appContext).weightDao(),
                prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
        }

        fun scheduleBackgroundSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelBackgroundSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
