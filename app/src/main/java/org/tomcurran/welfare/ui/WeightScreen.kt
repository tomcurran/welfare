package org.tomcurran.welfare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tomcurran.welfare.R
import org.tomcurran.welfare.ui.theme.WelfareTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun WeightScreen(
    viewModel: WeightViewModel,
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionsAndLoad()
    }

    WeightScreenContent(
        uiState = uiState,
        onRefresh = { viewModel.checkPermissionsAndLoad() },
        onNavigateToSettings = onNavigateToSettings,
        onRequestPermissions = { permissionLauncher.launch(viewModel.requiredPermissions) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreenContent(
    uiState: WeightUiState,
    onRefresh: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onRequestPermissions: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (uiState is WeightUiState.Success) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is WeightUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is WeightUiState.HealthConnectUnavailable -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.error_format, "Health Connect is not available"))
                }
            }
            is WeightUiState.PermissionNotGranted -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.weight_permission_required))
                        Button(onClick = onRequestPermissions) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }
            is WeightUiState.Success -> {
                if (state.entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.no_weight_records))
                    }
                } else {
                    WeightList(entries = state.entries, modifier = Modifier.padding(innerPadding))
                }
            }
            is WeightUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.error_format, state.message))
                }
            }
        }
    }
}

@Composable
fun WeightList(entries: List<WeightEntry>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = entries,
            key = { entry -> entry.id },
        ) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.time
                        .atZone(ZoneId.systemDefault())
                        .format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "%.1f kg".format(entry.weight),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            HorizontalDivider()
        }
    }
}

private val previewEntries = listOf(
    WeightEntry(id = "1", weight = 82.5, time = Instant.parse("2025-02-17T08:30:00Z")),
    WeightEntry(id = "2", weight = 82.1, time = Instant.parse("2025-02-16T07:45:00Z")),
    WeightEntry(id = "3", weight = 83.0, time = Instant.parse("2025-02-15T09:00:00Z")),
)

@Preview(showBackground = true)
@Composable
fun WeightScreenLoadingPreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.Loading)
    }
}

@Preview(showBackground = true)
@Composable
fun WeightScreenSuccessPreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.Success(previewEntries))
    }
}

@Preview(showBackground = true)
@Composable
fun WeightScreenEmptyPreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.Success(emptyList()))
    }
}

@Preview(showBackground = true)
@Composable
fun WeightScreenPermissionPreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.PermissionNotGranted)
    }
}

@Preview(showBackground = true)
@Composable
fun WeightScreenErrorPreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.Error("Something went wrong"))
    }
}

@Preview(showBackground = true)
@Composable
fun WeightScreenHealthConnectUnavailablePreview() {
    WelfareTheme {
        WeightScreenContent(uiState = WeightUiState.HealthConnectUnavailable)
    }
}
