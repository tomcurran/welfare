package org.tomcurran.welfare.ui

import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tomcurran.welfare.data.WeightRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeightEntry(
    val id: String,
    val weight: Double,
    val time: Instant,
)

sealed interface WeightUiState {
    data object Loading : WeightUiState
    data object PermissionNotGranted : WeightUiState
    data object HealthConnectUnavailable : WeightUiState
    data class Success(val entries: List<WeightEntry>) : WeightUiState
    data class Error(val message: String) : WeightUiState
}

class WeightViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectClient = try {
        HealthConnectClient.getOrCreate(application)
    } catch (e: Exception) {
        Log.e(TAG, "Health Connect not available", e)
        null
    }
    private val repository = WeightRepository.getInstance(application)

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    private val _permissionDenied = MutableStateFlow(false)
    private val _healthConnectUnavailable = MutableStateFlow(healthConnectClient == null)

    private val dataState = repository.entries()
        .map<_, WeightUiState> { entities ->
            WeightUiState.Success(
                entities.map { entity ->
                    WeightEntry(
                        id = entity.healthConnectId,
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

    val uiState: StateFlow<WeightUiState> = combine(
        dataState,
        _permissionDenied,
        _healthConnectUnavailable,
    ) { data, denied, unavailable ->
        when {
            unavailable -> WeightUiState.HealthConnectUnavailable
            denied -> WeightUiState.PermissionNotGranted
            else -> data
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUiState.Loading)

    fun checkPermissionsAndLoad() {
        val client = healthConnectClient ?: return
        viewModelScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (requiredPermissions.all { it in granted }) {
                _permissionDenied.value = false
                try {
                    repository.sync()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                }
                scheduleBackgroundSync()
            } else {
                _permissionDenied.value = true
            }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        if (requiredPermissions.all { it in granted }) {
            _permissionDenied.value = false
            viewModelScope.launch {
                try {
                    repository.sync()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                }
            }
            scheduleBackgroundSync()
        } else {
            _permissionDenied.value = true
        }
    }

    private fun scheduleBackgroundSync() {
        viewModelScope.launch {
            if (!repository.backgroundSyncEnabled.first()) return@launch
            WeightRepository.scheduleBackgroundSync(getApplication())
        }
    }

    companion object {
        private val TAG: String = WeightViewModel::class.java.simpleName
    }
}
