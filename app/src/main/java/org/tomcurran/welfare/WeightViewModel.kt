package org.tomcurran.welfare

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tomcurran.welfare.data.SyncWorker
import org.tomcurran.welfare.data.WeightRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class WeightEntry(
    val weight: Double,
    val time: Instant,
)

sealed interface WeightUiState {
    data object Loading : WeightUiState
    data object PermissionNotGranted : WeightUiState
    data class Success(val entries: List<WeightEntry>) : WeightUiState
    data class Error(val message: String) : WeightUiState
}

class WeightViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectClient = HealthConnectClient.getOrCreate(application)
    private val repository = WeightRepository.create(application)

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    private val _permissionDenied = MutableStateFlow(false)

    private val dataState = repository.entries()
        .map<_, WeightUiState> { entities ->
            WeightUiState.Success(
                entities.map { entity ->
                    WeightEntry(
                        weight = entity.weight,
                        time = Instant.ofEpochMilli(entity.time),
                    )
                }.distinctBy { entry ->
                    val day = LocalDate.ofInstant(entry.time, ZoneId.systemDefault())
                    day to entry.weight
                }
            )
        }
        .catch { e -> emit(WeightUiState.Error(e.message ?: "Unknown error")) }

    val uiState: StateFlow<WeightUiState> = combine(dataState, _permissionDenied) { data, denied ->
        if (denied) WeightUiState.PermissionNotGranted else data
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUiState.Loading)

    fun checkPermissionsAndLoad() {
        viewModelScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (requiredPermissions.all { it in granted }) {
                _permissionDenied.value = false
                repository.sync()
                scheduleBackgroundSync()
            } else {
                _permissionDenied.value = true
            }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        if (requiredPermissions.all { it in granted }) {
            _permissionDenied.value = false
            viewModelScope.launch { repository.sync() }
            scheduleBackgroundSync()
        } else {
            _permissionDenied.value = true
        }
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork("weight_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
