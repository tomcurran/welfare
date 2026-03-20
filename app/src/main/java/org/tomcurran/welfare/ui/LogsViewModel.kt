package org.tomcurran.welfare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.tomcurran.welfare.data.AppLogger
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor() : ViewModel() {

    val entries: StateFlow<List<AppLogger.LogEntry>> = AppLogger.entriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLogger.entriesFlow.value)

    fun clearLogs() = AppLogger.clearEntries()
}
