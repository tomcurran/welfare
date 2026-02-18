package org.tomcurran.welfare.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tomcurran.welfare.data.WeightRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WeightRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundSyncEnabled(enabled)
            if (enabled) {
                WeightRepository.scheduleBackgroundSync(appContext)
            } else {
                WeightRepository.cancelBackgroundSync(appContext)
            }
        }
    }

    fun resetSync() {
        viewModelScope.launch {
            repository.resetSync()
        }
    }
}
