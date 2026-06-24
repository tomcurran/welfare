package org.tomcurran.welfare.ui

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tomcurran.welfare.data.AppLogger
import org.tomcurran.welfare.data.WeightRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

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

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repository: WeightRepository,
    private val healthConnectClient: HealthConnectClient?,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    private val _permissionDenied = MutableStateFlow(false)
    private val _healthConnectUnavailable = MutableStateFlow(healthConnectClient == null)
    private val _permissionChecked = MutableStateFlow(false)

    private val dataState = repository.entries()
        .conflate()
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
        .distinctUntilChanged()
        .catch { e -> emit(WeightUiState.Error(e.message ?: "Unknown error")) }

    val uiState: StateFlow<WeightUiState> = combine(
        dataState,
        _permissionDenied,
        _healthConnectUnavailable,
        _permissionChecked,
    ) { data, denied, unavailable, checked ->
        when {
            unavailable -> WeightUiState.HealthConnectUnavailable
            !checked -> WeightUiState.Loading
            denied -> WeightUiState.PermissionNotGranted
            else -> data
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUiState.Loading)

    fun checkPermissionsAndLoad() {
        val client = healthConnectClient
        if (client == null) {
            _permissionChecked.value = true
            return
        }
        viewModelScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (requiredPermissions.all { it in granted }) {
                _permissionDenied.value = false
                try {
                    repository.sync()
                } catch (e: SecurityException) {
                    AppLogger.e(TAG, "Sync failed: permissions revoked", e)
                    _permissionDenied.value = true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Sync failed", e)
                }
                scheduleBackgroundSync()
            } else {
                _permissionDenied.value = true
            }
            _permissionChecked.value = true
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        if (requiredPermissions.all { it in granted }) {
            _permissionDenied.value = false
            viewModelScope.launch {
                try {
                    repository.sync()
                } catch (e: SecurityException) {
                    AppLogger.e(TAG, "Sync failed: permissions revoked", e)
                    _permissionDenied.value = true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Sync failed", e)
                }
            }
            scheduleBackgroundSync()
        } else {
            _permissionDenied.value = true
        }
        _permissionChecked.value = true
    }

    private fun scheduleBackgroundSync() {
        viewModelScope.launch {
            if (!repository.backgroundSyncEnabled.first()) return@launch
            WeightRepository.scheduleBackgroundSync(appContext)
        }
    }

    companion object {
        private val TAG: String = WeightViewModel::class.java.simpleName
    }
}
