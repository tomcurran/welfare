package org.tomcurran.welfare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import org.tomcurran.welfare.BuildConfig
import org.tomcurran.welfare.R
import org.tomcurran.welfare.data.AppLogger
import org.tomcurran.welfare.data.GoogleSheetsRepository
import org.tomcurran.welfare.ui.theme.WelfareTheme

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit,
) {
    val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
    val googleSheetsState by viewModel.googleSheetsState.collectAsStateWithLifecycle()
    val selectedSpreadsheetName by viewModel.selectedSpreadsheetName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.checkGoogleSheetsAuth()
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (data == null) {
            AppLogger.w(TAG, "Google Sheets authorization returned no data")
            return@rememberLauncherForActivityResult
        }
        try {
            val authorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            viewModel.onGoogleSheetsAuthorized(authorizationResult)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Google Sheets authorization failed", e)
        }
    }

    val spreadsheetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onSpreadsheetPicked(uri)
        }
    }

    fun connectGoogleSheets() {
        scope.launch {
            try {
                val authorizationResult = viewModel.googleSheetsAuthorize()
                val pendingIntent = authorizationResult.pendingIntent
                if (authorizationResult.hasResolution() && pendingIntent != null) {
                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent).build()
                    )
                } else {
                    viewModel.onGoogleSheetsAuthorized(authorizationResult)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Google Sheets authorization failed", e)
            }
        }
    }

    val googleSheetsSubtitle = when (googleSheetsState) {
        is GoogleSheetsState.Checking -> stringResource(R.string.google_sheets_checking)
        is GoogleSheetsState.Connected ->
            stringResource(R.string.google_sheets_connected, (googleSheetsState as GoogleSheetsState.Connected).email)
        is GoogleSheetsState.NotConnected ->
            stringResource(R.string.google_sheets_not_connected)
    }

    SettingsScreenContent(
        onBack = onBack,
        backgroundSyncEnabled = backgroundSyncEnabled,
        onBackgroundSyncChange = { viewModel.setBackgroundSyncEnabled(it) },
        googleSheetsConnected = googleSheetsState is GoogleSheetsState.Connected,
        googleSheetsSubtitle = googleSheetsSubtitle,
        onGoogleSheetsChange = { enabled ->
            if (enabled) {
                connectGoogleSheets()
            } else {
                viewModel.disconnectGoogleSheets()
            }
        },
        selectedSpreadsheetName = selectedSpreadsheetName,
        onSpreadsheetClick = {
            spreadsheetPickerLauncher.launch(
                arrayOf(GoogleSheetsRepository.MIME_TYPE_GOOGLE_SPREADSHEET)
            )
        },
        onResetSyncClick = { viewModel.resetSync() },
        onViewLogsClick = onNavigateToLogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    onBack: () -> Unit,
    backgroundSyncEnabled: Boolean,
    onBackgroundSyncChange: (Boolean) -> Unit,
    googleSheetsConnected: Boolean,
    googleSheetsSubtitle: String?,
    onGoogleSheetsChange: (Boolean) -> Unit,
    selectedSpreadsheetName: String?,
    onSpreadsheetClick: () -> Unit,
    onResetSyncClick: () -> Unit,
    onViewLogsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SwitchPreference(
                title = stringResource(R.string.background_sync),
                checked = backgroundSyncEnabled,
                onCheckedChange = onBackgroundSyncChange,
            )
            SwitchPreference(
                title = stringResource(R.string.google_sheets),
                subtitle = googleSheetsSubtitle,
                checked = googleSheetsConnected,
                onCheckedChange = onGoogleSheetsChange,
            )
            if (googleSheetsConnected) {
                ClickablePreference(
                    title = stringResource(R.string.select_spreadsheet),
                    subtitle = selectedSpreadsheetName ?: stringResource(R.string.no_spreadsheet_selected),
                    onClick = onSpreadsheetClick,
                )
            }
            if (BuildConfig.DEBUG) {
                ClickablePreference(
                    title = stringResource(R.string.reset_sync),
                    onClick = onResetSyncClick,
                )
                ClickablePreference(
                    title = stringResource(R.string.view_logs),
                    onClick = onViewLogsClick,
                )
            }
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun ClickablePreference(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenDisconnectedPreview() {
    WelfareTheme {
        SettingsScreenContent(
            onBack = {},
            backgroundSyncEnabled = true,
            onBackgroundSyncChange = {},
            googleSheetsConnected = false,
            googleSheetsSubtitle = "Not connected",
            onGoogleSheetsChange = {},
            selectedSpreadsheetName = null,
            onSpreadsheetClick = {},
            onResetSyncClick = {},
            onViewLogsClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenConnectedPreview() {
    WelfareTheme {
        SettingsScreenContent(
            onBack = {},
            backgroundSyncEnabled = true,
            onBackgroundSyncChange = {},
            googleSheetsConnected = true,
            googleSheetsSubtitle = "Connected: user@gmail.com",
            onGoogleSheetsChange = {},
            selectedSpreadsheetName = "My Weight Tracker",
            onSpreadsheetClick = {},
            onResetSyncClick = {},
            onViewLogsClick = {},
        )
    }
}
