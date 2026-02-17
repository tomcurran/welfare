package org.tomcurran.welfare.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tomcurran.welfare.BuildConfig
import org.tomcurran.welfare.R
import org.tomcurran.welfare.ui.theme.WelfareTheme

@Composable
fun SettingsScreen(
    viewModel: WeightViewModel,
    onBack: () -> Unit,
) {
    val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()

    SettingsScreenContent(
        onBack = onBack,
        backgroundSyncEnabled = backgroundSyncEnabled,
        onBackgroundSyncChange = { viewModel.setBackgroundSyncEnabled(it) },
        onResetSyncClick = { viewModel.resetSync() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    onBack: () -> Unit,
    backgroundSyncEnabled: Boolean,
    onBackgroundSyncChange: (Boolean) -> Unit,
    onResetSyncClick: () -> Unit
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
            if (BuildConfig.DEBUG) {
                ClickablePreference(
                    title = stringResource(R.string.reset_sync),
                    onClick = onResetSyncClick,
                )
            }
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun ClickablePreference(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenSyncEnabledPreview() {
    WelfareTheme {
        SettingsScreenContent(
            onBack = {},
            backgroundSyncEnabled = true,
            onBackgroundSyncChange = {},
            onResetSyncClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenSyncDisabledPreview() {
    WelfareTheme {
        SettingsScreenContent(
            onBack = {},
            backgroundSyncEnabled = false,
            onBackgroundSyncChange = {},
            onResetSyncClick = {}
        )
    }
}
