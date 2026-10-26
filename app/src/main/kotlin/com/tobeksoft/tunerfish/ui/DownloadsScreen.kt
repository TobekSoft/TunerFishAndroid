package com.tobeksoft.tunerfish.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.DownloadRow
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.DataUse
import com.tobeksoft.tunerfish.core.DownloadState

/**
 * Every album on the phone (or on its way): Keep pins one so the clean-up never removes it, the
 * bin removes it now. The line at the top is the space used against the limit set in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TunerFishApp
    val progress by app.downloads.progress.collectAsState()
    val rows = remember(progress) { app.db.downloadList() }
    var removing by remember { mutableStateOf<DownloadRow?>(null) }

    removing?.let { row ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text("Remove ${row.album.title} from the phone?") },
            confirmButton = { TextButton(onClick = { app.downloads.remove(row.album.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                val used = DataUse.sizeText(rows.sumOf { it.bytes })
                val limit = app.settings.downloadLimit
                Text(
                    if (limit.bytes == null) "$used used" else "$used used of ${limit.label}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (rows.isEmpty()) {
                    Text(
                        "Nothing yet. Albums download when you queue them, or with Download.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows, key = { it.album.id }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(
                        app.downloads.coverUri(row.album.id) ?: app.jellyfin.imageUrl(row.album.id, row.album.imageTag, 200),
                        Modifier.size(48.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(row.album.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            statusText(row), style = MaterialTheme.typography.bodySmall,
                            color = if (row.state == DownloadState.Failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.state == DownloadState.Failed) {
                        IconButton(onClick = { app.downloads.retry(row.album.id) }) { Icon(Icons.Default.Refresh, "Try again") }
                    }
                    if (row.state == DownloadState.Conflict || row.state == DownloadState.Skipped) {
                        TextButton(onClick = { app.downloads.replace(row.album.id) }) { Text("Replace") }
                    }
                    IconButton(onClick = { app.downloads.setKept(row.album.id, !row.kept) }) {
                        if (row.kept) Icon(Icons.Filled.PushPin, "Kept: tap to let the clean-up remove it")
                        else Icon(Icons.Outlined.PushPin, "Keep: never removed to save space")
                    }
                    IconButton(onClick = { removing = row }) { Icon(Icons.Default.Delete, "Remove from the phone") }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun statusText(row: DownloadRow): String {
    val size = DataUse.sizeText(row.bytes)
    return when (row.state) {
        DownloadState.Done -> if (row.kept) "$size · kept" else size
        DownloadState.Downloading -> "Downloading · ${row.done} of ${row.total}"
        DownloadState.Queued -> "Waiting · ${row.done} of ${row.total}"
        DownloadState.Failed -> "Failed · ${row.done} of ${row.total}"
        DownloadState.Conflict -> "Another app's copy is in its folder"
        DownloadState.Skipped -> "Not downloaded: another app's copy is there"
    }
}
