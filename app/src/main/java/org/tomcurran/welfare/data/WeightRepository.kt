package org.tomcurran.welfare.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.ChronoUnit

class WeightRepository(
    private val healthConnectClient: HealthConnectClient,
    private val dao: WeightDao,
    private val prefs: SharedPreferences,
) {
    fun entries(): Flow<List<WeightEntity>> = dao.getAllByTimeDesc()

    suspend fun sync() {
        val token = prefs.getString(KEY_CHANGES_TOKEN, null)
        if (token == null) {
            initialLoad()
        } else {
            try {
                incrementalSync(token)
            } catch (_: Exception) {
                prefs.edit { remove(KEY_CHANGES_TOKEN) }
                initialLoad()
            }
        }
    }

    private suspend fun initialLoad() {
        val now = Instant.now()
        val oneYearAgo = now.minus(365, ChronoUnit.DAYS)
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneYearAgo, now),
                    pageToken = pageToken,
                )
            )
            for (record in response.records) {
                dao.upsert(
                    WeightEntity(
                        healthConnectId = record.metadata.id,
                        weight = record.weight.inKilograms,
                        time = record.time.toEpochMilli(),
                    )
                )
            }
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
            for (change in changesResponse.changes) {
                when (change) {
                    is UpsertionChange -> {
                        val record = change.record
                        if (record is WeightRecord) {
                            dao.upsert(
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
            currentToken = changesResponse.nextChangesToken
        } while (changesResponse.hasMore)
        prefs.edit { putString(KEY_CHANGES_TOKEN, currentToken) }
    }

    companion object {
        private const val KEY_CHANGES_TOKEN = "health_connect_changes_token"

        fun create(context: Context): WeightRepository {
            val appContext = context.applicationContext
            return WeightRepository(
                healthConnectClient = HealthConnectClient.getOrCreate(appContext),
                dao = WeightDatabase.getInstance(appContext).weightDao(),
                prefs = appContext.getSharedPreferences("weight_sync", Context.MODE_PRIVATE),
            )
        }
    }
}
