package org.tomcurran.welfare

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    private val _uiState = MutableStateFlow<WeightUiState>(WeightUiState.Loading)
    val uiState: StateFlow<WeightUiState> = _uiState

    fun checkPermissionsAndLoad() {
        viewModelScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (requiredPermissions.all { it in granted }) {
                loadWeightRecords()
            } else {
                _uiState.value = WeightUiState.PermissionNotGranted
            }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        if (requiredPermissions.all { it in granted }) {
            viewModelScope.launch { loadWeightRecords() }
        } else {
            _uiState.value = WeightUiState.PermissionNotGranted
        }
    }

    private suspend fun loadWeightRecords() {
        _uiState.value = WeightUiState.Loading
        try {
            val now = Instant.now()
            val oneYearAgo = now.minus(365, ChronoUnit.DAYS)
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneYearAgo, now),
                )
            )
            val entries = response.records.map { record ->
                WeightEntry(
                    weight = record.weight.inKilograms,
                    time = record.time,
                )
            }.sortedByDescending { it.time }
            _uiState.value = WeightUiState.Success(entries)
        } catch (e: Exception) {
            _uiState.value = WeightUiState.Error(e.message ?: "Unknown error")
        }
    }
}
