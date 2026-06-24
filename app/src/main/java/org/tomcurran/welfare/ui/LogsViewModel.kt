package org.tomcurran.welfare.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.tomcurran.welfare.data.AppLogger
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor() : ViewModel() {

    val entries: StateFlow<List<AppLogger.LogEntry>> = AppLogger.entriesFlow

    fun clearLogs() = AppLogger.clearEntries()
}
