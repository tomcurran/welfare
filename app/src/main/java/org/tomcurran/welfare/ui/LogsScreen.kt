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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tomcurran.welfare.R
import org.tomcurran.welfare.data.AppLogger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

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
                    TextButton(onClick = viewModel::clearLogs) {
                        Text(stringResource(R.string.clear_logs))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(entries.reversed()) { entry ->
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
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
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
