package org.tomcurran.welfare.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tomcurran.welfare.R
import org.tomcurran.welfare.data.AppLogger
import org.tomcurran.welfare.ui.theme.WelfareTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    LogsScreenContent(
        entries = entries,
        onBack = onBack,
        onClearLogs = viewModel::clearLogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreenContent(
    entries: List<AppLogger.LogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onClearLogs) {
                        Text(stringResource(R.string.clear_logs))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(entries) { entry ->
                LogEntryRow(entry)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: AppLogger.LogEntry) {
    val levelColor = when (entry.level) {
        "E" -> MaterialTheme.colorScheme.error
        "W" -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    val time = Instant.ofEpochMilli(entry.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "[$time] ${entry.level}/${entry.tag}: ${entry.message}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = levelColor,
        )
        if (entry.throwable != null) {
            Text(
                text = entry.throwable.lines().take(4).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = levelColor.copy(alpha = 0.7f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogsScreenPreview() {
    WelfareTheme {
        LogsScreenContent(
            entries = listOf(
                AppLogger.LogEntry(
                    timestamp = 1731580800000L, // 2024-11-14 10:40:00
                    level = "I",
                    tag = "LogsScreen",
                    message = "This is an info log",
                    throwable = null
                ),
                AppLogger.LogEntry(
                    timestamp = 1731580860000L, // 2024-11-14 10:41:00
                    level = "W",
                    tag = "LogsScreen",
                    message = "This is a warning log",
                    throwable = null
                ),
                AppLogger.LogEntry(
                    timestamp = 1731580920000L, // 2024-11-14 10:42:00
                    level = "E",
                    tag = "LogsScreen",
                    message = "This is an error log",
                    throwable = "java.lang.RuntimeException: Something went wrong\n\tat org.tomcurran.welfare.ui.LogsScreenKt.LogsScreenPreview(LogsScreen.kt:100)"
                )
            ),
            onBack = {},
            onClearLogs = {}
        )
    }
}
