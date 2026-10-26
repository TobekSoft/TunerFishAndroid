package com.tobeksoft.tunerfish.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.AlbumDownload
import com.tobeksoft.tunerfish.LibrarySource
import com.tobeksoft.tunerfish.LibraryViewModel
import com.tobeksoft.tunerfish.core.Album
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.AlbumSortMode
import com.tobeksoft.tunerfish.core.Library
import com.tobeksoft.tunerfish.playback.PlayerViewModel

private val sortLabels = listOf(
    AlbumSortMode.Title to "Title",
    AlbumSortMode.YearAscending to "Year (oldest first)",
    AlbumSortMode.YearDescending to "Year (newest first)",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(library: LibraryViewModel, player: PlayerViewModel, onBack: () -> Unit, onOpenAlbum: (Album) -> Unit, onOpenQueue: () -> Unit) {
    val artist = library.artist
    LaunchedEffect(artist) { if (artist == null) onBack() } // e.g. after Android restarted the app
    var sortOpen by remember { mutableStateOf(false) }
    val downloads by library.downloads.collectAsState()
    // Show all / Favourites only starts at the top, not wherever the albums kept before were.
    val gridState = rememberLazyGridState()
    LaunchedEffect(library.showAllAlbums) { gridState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(artist?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Library.albumCountText(library.albums.size), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort albums") }
                    DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                        for ((mode, label) in sortLabels) {
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (mode == library.albumSort) Icon(Icons.Default.Check, null) },
                                onClick = { sortOpen = false; library.setSort(mode) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Show hidden") },
                            leadingIcon = { if (library.showHidden) Icon(Icons.Default.Check, null) },
                            onClick = { sortOpen = false; library.showHiddenItems(!library.showHidden) },
                        )
                    }
                    QueueButton(onOpenQueue)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (library.albumsLimited) ViewAlbumsBar(library)
            PullToRefreshBox(isRefreshing = library.busy, onRefresh = library::refreshAlbums, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(library.albums, key = { it.id }) { album ->
                    AlbumCell(library, player, album, artist, downloads[album.id], onOpen = { onOpenAlbum(album) })
                }
            }
            }
        }
    }
}

/** From a view by marks: how many of the artist's albums it shows, and "Show all" for the rest. */
@Composable
private fun ViewAlbumsBar(library: LibraryViewModel) {
    val label = library.albumsLabel
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (library.showAllAlbums) "All ${library.albumsInAll} albums" else "$label: ${library.albums.size} of ${library.albumsInAll} albums",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { library.showAllAlbums(!library.showAllAlbums) }) {
            Text(if (library.showAllAlbums) "$label only" else "Show all")
        }
    }
}

/**
 * One album in a grid: cover, title, then the year (and in the views, where albums come from across
 * the library, the artist). Long-press: play, queue or download without opening it, and its marks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCell(
    library: LibraryViewModel,
    player: PlayerViewModel,
    album: Album,
    artist: Artist?,
    download: AlbumDownload?,
    onOpen: () -> Unit,
    showArtist: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }).padding(6.dp)) {
            Box {
                Cover(library.coverUrl(album, 400), Modifier.fillMaxWidth())
                DownloadBadge(download, Modifier.align(Alignment.TopEnd))
            }
            Text(
                album.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (showArtist) Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(album.year?.toString() ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                MarkBadges(album.marks, size = 14.dp)
            }
        }
        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Play") }, onClick = {
                menuOpen = false
                library.withTracks(album) { player.play(it, album, artist) }
            })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                menuOpen = false
                library.withTracks(album) { player.enqueue(it, album, artist) }
            })
            if (library.source == LibrarySource.Server) {
                DropdownMenuItem(text = { Text("Download") }, onClick = {
                    menuOpen = false
                    library.download(album, artist)
                })
            }
            HorizontalDivider()
            MarkMenuItems(album.marks, "album", { library.markAlbum(album, it) }, close = { menuOpen = false })
        }
    }
}
