package com.tobeksoft.tunerfish.ui

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tobeksoft.tunerfish.AlbumDownload
import com.tobeksoft.tunerfish.core.DownloadState

/** A square album cover with a ♪ placeholder underneath while it loads (or if there is none). */
@Composable
fun Cover(url: Any?, modifier: Modifier = Modifier) {
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(4)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/**
 * Marks the playing track. An icon rather than the "▶" character, which Samsung's font draws as a
 * blue emoji; sized to sit beside the small track numbers.
 */
@Composable
fun PlayingMark(modifier: Modifier = Modifier) =
    Icon(Icons.Default.PlayArrow, "Playing", tint = MaterialTheme.colorScheme.onSurface, modifier = modifier.size(14.dp))

/** A cover's corner mark: ✓ when the album is on the phone, ⬇ while it's downloading or waiting to. */
@Composable
fun DownloadBadge(download: AlbumDownload?, modifier: Modifier = Modifier) {
    if (download == null || download.state == DownloadState.Skipped) return
    val done = download.state == DownloadState.Done
    Box(
        modifier.padding(4.dp).size(22.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) Icons.Default.DownloadDone else Icons.Default.Download,
            if (done) "Downloaded" else "Downloading",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}
