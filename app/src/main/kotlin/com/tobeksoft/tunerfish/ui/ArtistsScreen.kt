package com.tobeksoft.tunerfish.ui

import com.tobeksoft.tunerfish.playback.PlayerViewModel
import com.tobeksoft.tunerfish.core.LibraryView
import com.tobeksoft.tunerfish.core.Album
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.tobeksoft.tunerfish.core.SearchScope
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tobeksoft.tunerfish.LibrarySource
import com.tobeksoft.tunerfish.LibraryViewModel
import com.tobeksoft.tunerfish.R
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.SortOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(
    library: LibraryViewModel,
    player: PlayerViewModel,
    onOpenArtist: (Artist) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenQueue: () -> Unit,
    onExit: () -> Unit,
) {
    // Each view keeps its own place in the list (saved, so it outlives opening an artist), and Back
    // to the Library returns where you were.
    val listStates = LibraryView.entries.associateWith { rememberLazyListState() }
    val listState = listStates.getValue(library.view)
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val onPhone = library.source == LibrarySource.Phone

    // Open the list at the artist chosen last time (once, when the artists first arrive).
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(library.artists) {
        if (!restored && library.artists.isNotEmpty()) {
            restored = true
            val i = library.lastArtistIndex()
            if (i > 0) listState.scrollToItem(i)
        }
    }

    var aboutOpen by remember { mutableStateOf(false) }
    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
    BackHandler(enabled = library.view != LibraryView.Library) { library.showView(LibraryView.Library) }

    // The search box is hidden, for more of the list, until 🔍 in the top bar is tapped.
    // It stays while it has text; empty, it goes again when it loses focus. Back (after the keyboard)
    // clears it and closes it, before leaving a view.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val showSearch = searchOpen || library.artistFilter.isNotEmpty()
    BackHandler(enabled = showSearch) { library.setFilter(""); searchOpen = false }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchOpen) {
        if (searchOpen && library.artistFilter.isEmpty()) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // The fish stands for the app (like VLC's cone) and opens About; the title chooses the view.
                navigationIcon = {
                    IconButton(onClick = { aboutOpen = true }) {
                        Image(painterResource(R.mipmap.ic_launcher), "About ${stringResource(R.string.app_name)}", Modifier.size(36.dp))
                    }
                },
                title = { ViewChooser(library) },
                actions = {
                    // Opens and closes the search box (closing clears it). Search works on the artist
                    // lists; from a played view it goes to the Library first.
                    IconButton(onClick = {
                        if (showSearch) {
                            library.setFilter("")
                            searchOpen = false
                        } else {
                            if (!(library.view == LibraryView.Library || library.view.byMarks)) library.showView(LibraryView.Library)
                            searchOpen = true
                        }
                    }) { Icon(Icons.Default.Search, if (showSearch) "Close search" else "Search") }
                    // Server ⇄ phone. Shows where you'd go, like a link.
                    IconButton(onClick = { library.showPhone(!onPhone) }) {
                        if (onPhone) Icon(painterResource(R.drawable.ic_jellyfin), "Show the Jellyfin server's library")
                        else Icon(Icons.Default.PhoneAndroid, "Show the albums on this phone")
                    }
                    QueueButton(onOpenQueue)
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; library.loadArtists(connectFirst = true) })
                        DropdownMenuItem(
                            text = { Text("Show hidden") },
                            trailingIcon = { if (library.showHidden) Icon(Icons.Default.Check, "On") },
                            onClick = { menuOpen = false; library.showHiddenItems(!library.showHidden) },
                        )
                        DropdownMenuItem(text = { Text("Downloads") }, onClick = { menuOpen = false; onOpenDownloads() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                        DropdownMenuItem(text = { Text("About") }, onClick = { menuOpen = false; aboutOpen = true })
                        DropdownMenuItem(text = { Text("Exit") }, onClick = { menuOpen = false; onExit() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val artistList = library.view == LibraryView.Library || library.view.byMarks
            if (artistList && showSearch) {
                var focused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    library.artistFilter, library::setFilter,
                    placeholder = { Text(searchPlaceholder(library.searchScope)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = { library.setFilter(""); searchOpen = false }) { Icon(Icons.Default.Clear, "Close search") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(searchFocus)
                        .onFocusChanged {
                            if (focused && !it.isFocused && library.artistFilter.isEmpty()) searchOpen = false
                            focused = it.isFocused
                        },
                )
                if (focused || library.artistFilter.isNotEmpty()) ScopeChips(library)
            }
            library.offlineReason?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Pull down to refresh; when offline, that also tries the server again.
            PullToRefreshBox(
                isRefreshing = library.busy,
                onRefresh = { library.loadArtists(connectFirst = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
            if (!artistList) PlayedView(library, player, onOpenAlbum)
            else Row(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val empty = when {
                        library.artists.isNotEmpty() -> null
                        library.artistFilter.isNotBlank() -> if (library.searching) "Searching…" else "No matches."
                        library.view.byMarks && library.viewLoaded -> emptyText(library.view)
                        else -> null
                    }
                    if (empty != null) item {
                        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    items(library.artists, key = { it.id }) { artist -> ArtistRow(library, artist) { onOpenArtist(artist) } }
                }
                JumpBar(
                    present = library.groupsPresent,
                    onJump = { group ->
                        val i = library.firstIndexInGroup(group)
                        if (i >= 0) scope.launch { listState.scrollToItem(i) }
                    },
                )
            }
            }
        }
    }
}

/** One artist in a list: the name and its marks, and what matched the filter box; long-press for the marks menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(library: LibraryViewModel, artist: Artist, onOpen: () -> Unit) {
    var marksOpen by remember { mutableStateOf(false) }
    val note = library.noteOf(artist)
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { marksOpen = true })
                .padding(horizontal = 16.dp, vertical = if (note == null) 12.dp else 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(artist.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                MarkBadges(artist.marks, Modifier.padding(start = 8.dp))
            }
            // The count stays in view however long the title, which gives way instead.
            if (note != null) Row {
                val style = MaterialTheme.typography.bodySmall
                val colour = MaterialTheme.colorScheme.onSurfaceVariant
                Text(note.text, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = style, color = colour)
                if (note.more > 0) Text(" +${note.more}", maxLines = 1, style = style, color = colour)
            }
        }
        DropdownMenu(marksOpen, onDismissRequest = { marksOpen = false }) {
            MarkMenuItems(artist.marks, "artist", { library.markArtist(artist, it) }, close = { marksOpen = false })
        }
    }
    HorizontalDivider()
}

/** What the filter box looks in: any of artist names, album titles and track titles (at least one). */
@Composable
private fun ScopeChips(library: LibraryViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in SearchScope.entries) {
            val on = s in library.searchScope
            FilterChip(
                selected = on,
                onClick = { library.changeSearchScope(if (on) library.searchScope - s else library.searchScope + s) },
                label = { Text(s.name) },
            )
        }
    }
}

/** "Search artists and albums", from the scope. */
private fun searchPlaceholder(scope: Set<SearchScope>): String {
    val words = SearchScope.entries.filter { it in scope }.map { it.name.lowercase() }
    return "Search " + if (words.size == 1) words[0] else words.dropLast(1).joinToString(", ") + " and " + words.last()
}

/**
 * The title: which view is showing (Library, Favourites, ...), tap to choose another. Which source
 * (server or phone) shows in the toggle's icon, not here.
 */
@Composable
private fun ViewChooser(library: LibraryViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clickable { open = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(library.view.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, "Choose what to show")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            for (v in LibraryView.entries) {
                DropdownMenuItem(
                    text = { Text(v.label) },
                    leadingIcon = { if (v == library.view) Icon(Icons.Default.Check, null) },
                    onClick = { open = false; library.showView(v) },
                )
            }
        }
    }
}

/** A played view (Recents, Most): its albums, ranked, from across the library. */
@Composable
private fun PlayedView(library: LibraryViewModel, player: PlayerViewModel, onOpenAlbum: (Album) -> Unit) {
    val downloads by library.downloads.collectAsState()
    val albums = library.viewAlbums
    val note = library.viewNote ?: if (library.viewLoaded && albums.isEmpty()) emptyText(library.view) else null
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
        if (note != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        items(albums, key = { it.id }) { album ->
            AlbumCell(library, player, album, library.artistOf(album), downloads[album.id], onOpen = { onOpenAlbum(album) }, showArtist = true)
        }
    }
}

private fun emptyText(view: LibraryView) = when (view) {
    LibraryView.Favourites -> "No favourites yet. Long-press an artist or album and choose Favourite."
    LibraryView.Liked -> "Nothing liked yet. Long-press an artist or album and choose Like."
    LibraryView.Hidden -> "Nothing is hidden."
    else -> "Nothing played yet."
}

/** The A–Z bar: tap or slide a finger down it to scroll the list. Never selects or filters. */
@Composable
private fun JumpBar(present: Set<String>, onJump: (String) -> Unit) {
    val groups = SortOrder.groups
    Column(
        Modifier.width(28.dp).fillMaxHeight().padding(vertical = 4.dp)
            .pointerInput(present) {
                var last: String? = null
                fun pick(y: Float) {
                    val i = (y / size.height * groups.size).toInt().coerceIn(0, groups.lastIndex)
                    val g = groups[i]
                    if (g != last && g in present) { last = g; onJump(g) }
                }
                awaitEachGesture {
                    last = null
                    val down = awaitFirstDown()
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        if (!change.pressed) break
                        pick(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        for (g in groups) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // A tight line height (the default body style's is 24 sp, taller than a letter's slot on
                // a short screen), and never clipped by the slot even if the font is enlarged.
                Text(
                    g, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.wrapContentHeight(unbounded = true),
                    color = if (g in present) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
    }
}
