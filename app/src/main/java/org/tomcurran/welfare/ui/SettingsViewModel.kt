package org.tomcurran.welfare.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tomcurran.welfare.data.WeightRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeightRepository.getInstance(application)

    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundSyncEnabled(enabled)
            if (enabled) {
                WeightRepository.scheduleBackgroundSync(getApplication())
            } else {
                WeightRepository.cancelBackgroundSync(getApplication())
            }
        }
    }

    fun resetSync() {
        viewModelScope.launch {
            repository.resetSync()
        }
    }
}