package org.tomcurran.welfare.ui

import android.content.Context
import android.util.Log
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
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _googleSheetsState = MutableStateFlow<GoogleSheetsState>(GoogleSheetsState.Checking)
    val googleSheetsState: StateFlow<GoogleSheetsState> = _googleSheetsState.asStateFlow()

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
                Log.d(TAG, "Google Sheets auth check failed", e)
            }
        }
    }

    private suspend fun processAuthorizationResult(authorizationResult: AuthorizationResult) {
        val accessToken = authorizationResult.accessToken
        if (accessToken == null) {
            _googleSheetsState.value = GoogleSheetsState.NotConnected
        } else {
            val email = fetchGoogleEmail(accessToken)
            if (email != null) {
                _googleSheetsState.value = GoogleSheetsState.Connected(email)
            } else {
                _googleSheetsState.value = GoogleSheetsState.NotConnected
            }
        }
    }

    fun onGoogleSheetsAuthorized(authorizationResult: AuthorizationResult) {
        viewModelScope.launch {
            processAuthorizationResult(authorizationResult)
        }
    }

    fun disconnectGoogleSheets() {
        viewModelScope.launch {
            try {
                Identity.getSignInClient(appContext)
                    .signOut()
                    .asDeferred()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disconnect Google Sheets", e)
            }
            _googleSheetsState.value = GoogleSheetsState.NotConnected
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

    private suspend fun fetchGoogleEmail(accessToken: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://www.googleapis.com/oauth2/v3/userinfo")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("email").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Google email", e)
                null
            }
        }

    companion object {
        private val TAG: String = SettingsViewModel::class.java.simpleName

        val GOOGLE_SHEETS_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/spreadsheets"),
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("openid"),
            Scope("email"),
        )
    }
}
