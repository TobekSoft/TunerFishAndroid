package com.tobeksoft.tunerfish.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tobeksoft.tunerfish.AlbumDownload
import com.tobeksoft.tunerfish.LibrarySource
import com.tobeksoft.tunerfish.LibraryViewModel
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.DownloadState
import com.tobeksoft.tunerfish.core.Library
import com.tobeksoft.tunerfish.playback.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(library: LibraryViewModel, player: PlayerViewModel, onBack: () -> Unit, onOpenQueue: () -> Unit) {
    val album = library.album
    LaunchedEffect(album) { if (album == null) onBack() }
    if (album == null) return
    val tracks = library.tracks
    val download = library.downloads.collectAsState().value[album.id]
    val onPhone = remember(album.id, download) { library.downloadedTrackIds(album.id) }
    val app = LocalContext.current.applicationContext as TunerFishApp
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // Queueing downloads an album anyway; this downloads it without queueing and keeps it.
                    val canDownload = library.source == LibrarySource.Server &&
                        (download == null || download.state in setOf(DownloadState.Failed, DownloadState.Skipped, DownloadState.Conflict))
                    if (canDownload) {
                        IconButton(onClick = { library.download(album) }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Default.Download, "Download and keep")
                        }
                    }
                    QueueButton(onOpenQueue)
                    if (download != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (download.state == DownloadState.Conflict || download.state == DownloadState.Skipped) {
                                DropdownMenuItem(text = { Text("Replace the other copy") }, onClick = {
                                    menuOpen = false
                                    app.downloads.replace(album.id)
                                })
                            }
                            DropdownMenuItem(
                                text = { Text("Keep on the phone") },
                                leadingIcon = { if (download.kept) Icon(Icons.Default.Check, null) },
                                onClick = { menuOpen = false; app.downloads.setKept(album.id, !download.kept) },
                            )
                            DropdownMenuItem(text = { Text("Remove download") }, onClick = {
                                menuOpen = false
                                app.downloads.remove(album.id)
                                if (library.source == LibrarySource.Phone) onBack() // it's no longer on the phone
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(library.coverUrl(album, 400), Modifier.size(128.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(album.artist, style = MaterialTheme.typography.bodyMedium)
                        Text(album.year?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.offset(x = (-12).dp)) { MarkButtons(album.marks) { library.markAlbum(album, it) } }
                        if (download != null) {
                            Text(
                                downloadText(download), style = MaterialTheme.typography.bodySmall,
                                color = if (download.state == DownloadState.Failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { player.play(tracks, album, library.artist) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Play album")
                    }
                    FilledTonalButton(onClick = { player.enqueue(tracks, album, library.artist) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Add to queue")
                    }
                }
                if (library.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                Spacer(Modifier.size(8.dp))
                HorizontalDivider()
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                val current = track.id == player.currentId
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { player.play(tracks, album, library.artist, i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
                        if (current) PlayingMark()
                        else Text(track.number?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        if (track.artist.isNotBlank() && track.artist != album.artist)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (track.id in onPhone) {
                        Icon(
                            Icons.Default.DownloadDone, "On the phone",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp).size(14.dp),
                        )
                    }
                    Text(Library.formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun downloadText(d: AlbumDownload): String = when (d.state) {
    DownloadState.Done -> if (d.kept) "Downloaded · kept" else "Downloaded"
    DownloadState.Downloading -> "Downloading · ${d.done} of ${d.total}"
    DownloadState.Queued -> if (d.done > 0) "Waiting to download · ${d.done} of ${d.total}" else "Waiting to download"
    DownloadState.Failed -> "Download failed · ${d.done} of ${d.total}"
    DownloadState.Conflict -> "Another app's copy is in its folder"
    DownloadState.Skipped -> "Not downloaded: another app's copy is there"
}
