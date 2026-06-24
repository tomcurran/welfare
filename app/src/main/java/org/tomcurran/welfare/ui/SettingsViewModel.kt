package org.tomcurran.welfare.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tomcurran.welfare.data.AppLogger
import org.tomcurran.welfare.data.GoogleSheetsRepository
import org.tomcurran.welfare.data.WeightRepository
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed interface GoogleSheetsState {
    data object Checking : GoogleSheetsState
    data class Connected(val email: String) : GoogleSheetsState
    data object NotConnected : GoogleSheetsState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WeightRepository,
    private val googleSheetsRepository: GoogleSheetsRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _googleSheetsState = MutableStateFlow<GoogleSheetsState>(GoogleSheetsState.Checking)
    val googleSheetsState: StateFlow<GoogleSheetsState> = _googleSheetsState.asStateFlow()

    val selectedSpreadsheetName: StateFlow<String?> = googleSheetsRepository.selectedSpreadsheetName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _spreadsheetPickFailed = MutableStateFlow(false)
    val spreadsheetPickFailed: StateFlow<Boolean> = _spreadsheetPickFailed.asStateFlow()

    suspend fun googleSheetsAuthorize(): AuthorizationResult {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(GOOGLE_SHEETS_SCOPES)
            .build()
        val result = Identity.getAuthorizationClient(appContext)
            .authorize(authorizationRequest)
            .asDeferred()
            .await()
        return result
    }

    fun checkGoogleSheetsAuth() {
        viewModelScope.launch {
            _googleSheetsState.value = GoogleSheetsState.Checking
            try {
                val authorizationResult = googleSheetsAuthorize()
                processAuthorizationResult(authorizationResult)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Google Sheets auth check failed", e)
                _googleSheetsState.value = GoogleSheetsState.NotConnected
            }
        }
    }

    private suspend fun processAuthorizationResult(authorizationResult: AuthorizationResult) {
        val accessToken = authorizationResult.accessToken
        if (accessToken == null) {
            _googleSheetsState.value = GoogleSheetsState.NotConnected
            return
        }
        val email = fetchGoogleEmail(accessToken)
        if (email != null) {
            googleSheetsRepository.setAccountEmail(email)
            _googleSheetsState.value = GoogleSheetsState.Connected(email)
        } else {
            AppLogger.w(TAG, "Authorization succeeded but could not retrieve email")
            _googleSheetsState.value = GoogleSheetsState.NotConnected
        }
    }

    private suspend fun fetchGoogleEmail(accessToken: String): String? =
        withContext(Dispatchers.IO) {
            val connection = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    AppLogger.w(TAG, "Userinfo request failed: HTTP ${connection.responseCode}")
                    return@withContext null
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("email").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to fetch Google email", e)
                null
            } finally {
                connection.disconnect()
            }
        }

    fun onGoogleSheetsAuthorized(authorizationResult: AuthorizationResult) {
        viewModelScope.launch {
            processAuthorizationResult(authorizationResult)
        }
    }

    fun disconnectGoogleSheets() {
        viewModelScope.launch {
            googleSheetsRepository.clearSpreadsheet()
            googleSheetsRepository.clearAccountEmail()
            _googleSheetsState.value = GoogleSheetsState.NotConnected
        }
    }

    fun onSpreadsheetPicked(uri: Uri) {
        viewModelScope.launch {
            _spreadsheetPickFailed.value = false
            val displayName = googleSheetsRepository.getDisplayName(uri)
            if (displayName == null) {
                AppLogger.w(TAG, "Could not get display name from URI: $uri")
                _spreadsheetPickFailed.value = true
                return@launch
            }
            val result = googleSheetsRepository.resolveSpreadsheetByName(displayName)
            if (result != null) {
                val (id, name) = result
                googleSheetsRepository.selectSpreadsheet(id, name)
            } else {
                AppLogger.w(TAG, "Could not resolve spreadsheet ID for: $displayName")
                _spreadsheetPickFailed.value = true
            }
        }
    }

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

    companion object {
        private val TAG: String = SettingsViewModel::class.java.simpleName

        val GOOGLE_SHEETS_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/spreadsheets"),
            Scope(GoogleSheetsRepository.SCOPE_DRIVE_METADATA_READONLY),
            Scope("openid"),
            Scope("email"),
        )
    }
}
