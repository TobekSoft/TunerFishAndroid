package com.tobeksoft.tunerfish.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.core.Library
import com.tobeksoft.tunerfish.playback.PlayerViewModel

/** The bar under every browsing screen: what's playing, play/pause, next. Tap it for Now Playing. */
@Composable
fun MiniPlayer(player: PlayerViewModel, onOpen: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(player.artworkUri, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(player.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = player::togglePlay) {
                Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause")
            }
            IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(player: PlayerViewModel, onBack: () -> Unit, onOpenQueue: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now playing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    ToneButton()
                    QueueButton(onOpenQueue)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The cover takes whatever height is left, staying square, so on a short screen it shrinks
            // rather than squashing the controls below it.
            Cover(player.artworkUri, Modifier.weight(1f, fill = false))
            Text(player.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(player.subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            SeekBar(player)
            TransportControls(player)
        }
    }
}

/**
 * A slim progress bar: thin track, small round thumb that grows while you drag. While dragging it
 * shows the finger's position instead of the player's, and seeks only when you let go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(player: PlayerViewModel) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val duration = player.durationMs.coerceAtLeast(1)
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.onSurface,
        activeTrackColor = MaterialTheme.colorScheme.onSurface,
        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    )

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragging ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { player.seekTo((it * duration).toLong()) }; dragging = null },
            enabled = player.hasMedia,
            interactionSource = interaction,
            colors = colors,
            thumb = {
                val size = if (dragging != null) 18.dp else 12.dp
                Box(Modifier.size(size).background(MaterialTheme.colorScheme.onSurface, CircleShape))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state, colors = colors, modifier = Modifier.height(3.dp),
                    thumbTrackGapSize = 0.dp, drawStopIndicator = null,
                )
            },
            modifier = Modifier.height(32.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = dragging?.let { (it * duration).toLong() } ?: player.positionMs
            Text(Library.formatDuration(shown), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Library.formatDuration(player.durationMs), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** The queue icon, top right on Now Playing and on every browse screen. */
@Composable
fun QueueButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
}

/** Previous / play-pause / next: big flat skip buttons either side of a solid round play button. */
@Composable
private fun TransportControls(player: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = player::previous, enabled = player.hasMedia, modifier = Modifier.size(72.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(52.dp))
        }
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)
                .clickable(enabled = player.hasMedia, onClick = player::togglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(52.dp),
            )
        }
        IconButton(onClick = player::next, enabled = player.hasMedia, modifier = Modifier.size(72.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(52.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(player: PlayerViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = player::clear, enabled = player.queue.isNotEmpty()) { Text("Clear") } },
            )
        },
    ) { padding ->
        if (player.queue.isEmpty()) {
            Text(
                "Nothing queued. Open an album and tap Play album, or long-press an album to add it here.",
                modifier = Modifier.padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            itemsIndexed(player.queue) { i, entry ->
                val current = i == player.currentIndex
                var menuOpen by remember { mutableStateOf(false) }
                // Long-press: remove the track's whole album from the queue.
                // Bordered, with an icon and bold text, so it stands out from the list behind it.
                DropdownMenu(
                    menuOpen, onDismissRequest = { menuOpen = false },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                ) {
                    val tracks = player.albumRunAt(i).count()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        text = {
                            Text(
                                if (tracks == 1) "Remove album (1 track)" else "Remove album ($tracks tracks)",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        onClick = { menuOpen = false; player.removeAlbumAt(i) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .combinedClickable(onClick = { player.playAt(i) }, onLongClick = { menuOpen = true })
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(20.dp)) { if (current) PlayingMark() }
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { player.remove(i) }) { Icon(Icons.Default.Close, "Remove from queue") }
                }
                HorizontalDivider()
            }
        }
    }
}
