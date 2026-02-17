package org.tomcurran.welfare.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "welfare_prefs")

class WeightRepository(
    private val healthConnectClient: HealthConnectClient,
    private val dao: WeightDao,
    private val dataStore: DataStore<Preferences>,
) {
    fun entries(): Flow<List<WeightEntity>> = dao.getAllByTimeDesc()

    val backgroundSyncEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_SYNC_ENABLED] ?: true
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_SYNC_ENABLED] = enabled }
    }

    suspend fun resetSync() {
        dataStore.edit { it.remove(KEY_HEALTH_CONNECT_CHANGES_TOKEN) }
    }

    suspend fun sync() {
        val token = dataStore.data.first()[KEY_HEALTH_CONNECT_CHANGES_TOKEN]
        if (token == null) {
            fullSync()
        } else {
            try {
                incrementalSync(token)
            } catch (e: Exception) {
                Log.w(TAG, "Incremental sync failed, falling back to full sync", e)
                resetSync()
                fullSync()
            }
        }
    }

    private suspend fun fullSync() {
        val now = Instant.now()
        val start = now.minus(FULL_SYNC_DAYS, ChronoUnit.DAYS)
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now),
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
        dataStore.edit { it[KEY_HEALTH_CONNECT_CHANGES_TOKEN] = newToken }
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
        dataStore.edit { it[KEY_HEALTH_CONNECT_CHANGES_TOKEN] = currentToken }
    }

    companion object {
        private val TAG: String = WeightRepository::class.java.simpleName
        private val KEY_HEALTH_CONNECT_CHANGES_TOKEN = stringPreferencesKey("health_connect_changes_token")
        private val KEY_BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        private const val FULL_SYNC_DAYS: Long = 365 * 5
        private const val WORK_NAME_BACKGROUND_SYNC = "background_sync"

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
                dataStore = appContext.dataStore,
            )
        }

        fun scheduleBackgroundSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME_BACKGROUND_SYNC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelBackgroundSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_BACKGROUND_SYNC)
        }
    }
}
