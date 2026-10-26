package com.tobeksoft.tunerfish

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tobeksoft.tunerfish.core.Album
import com.tobeksoft.tunerfish.core.AlbumSortMode
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.BaseItem
import com.tobeksoft.tunerfish.core.FilteredArtists
import com.tobeksoft.tunerfish.core.JellyfinException
import com.tobeksoft.tunerfish.core.Library
import com.tobeksoft.tunerfish.core.LibraryView
import com.tobeksoft.tunerfish.core.LogArea
import com.tobeksoft.tunerfish.core.Marks
import com.tobeksoft.tunerfish.core.MatchNote
import com.tobeksoft.tunerfish.core.PlayHistory
import com.tobeksoft.tunerfish.core.PlayedTrack
import com.tobeksoft.tunerfish.core.Search
import com.tobeksoft.tunerfish.core.SearchHit
import com.tobeksoft.tunerfish.core.SearchScope
import com.tobeksoft.tunerfish.core.SortOrder
import com.tobeksoft.tunerfish.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where the artists, albums and tracks come from: the Jellyfin server, or the downloads on the phone. */
enum class LibrarySource { Server, Phone }

/**
 * Sign-in, then the drill-down: artists → albums of one artist → tracks of one album.
 * One instance for the whole activity, so each screen reads what the previous one chose.
 *
 * The same screens browse either the server or the albums on the phone. The phone is shown when
 * chosen by hand, and automatically when the server can't be reached or "Wi-Fi only" is on without
 * Wi-Fi; an automatic switch goes back to the server when the network changes and it answers again.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TunerFishApp
    private val jellyfin get() = app.jellyfin
    private val settings get() = app.settings

    var isSignedIn by mutableStateOf(app.isSignedIn); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    // Sign in (the password is only ever held here, never stored)
    var serverUrl by mutableStateOf(settings.serverUrls.firstOrNull() ?: "")
    var userName by mutableStateOf(settings.userName ?: "")
    var password by mutableStateOf("")

    var source by mutableStateOf(LibrarySource.Server); private set
    /** Why the phone's albums are showing without being chosen ("Can't reach the server"), or null. */
    var offlineReason by mutableStateOf<String?>(null); private set

    // Artists
    private var allArtists: List<Artist> = emptyList()
    var artists by mutableStateOf<List<Artist>>(emptyList()); private set
    var groupsPresent by mutableStateOf<Set<String>>(emptySet()); private set
    var artistFilter by mutableStateOf(""); private set
    /** What the filter box looks in (artist names, album and track titles). */
    var searchScope by mutableStateOf(settings.searchScope); private set
    /** Album and track titles matching the filter text (or a shorter one, while a search is on its way). */
    private var hits: List<SearchHit> = emptyList()
    /** True while albums and tracks are being looked up. */
    var searching by mutableStateOf(false); private set
    private var searchJob: Job? = null
    /** The artists listed and the albums each shows (the view's, narrowed by the filter box). */
    private var shortlist by mutableStateOf(FilteredArtists.all(emptyList()))
    /** Show artists and albums marked hidden. */
    var showHidden by mutableStateOf(settings.showHidden); private set

    // A view instead of the whole library. The views by marks (Favourites, ...) shorten the artist
    // list and each artist's albums; the played views (Recently played, ...) list albums instead.
    var view by mutableStateOf(LibraryView.Library); private set
    private var viewAll: List<Album> = emptyList()
    private var viewFilter = FilteredArtists(emptyList(), emptyMap())
    var viewAlbums by mutableStateOf<List<Album>>(emptyList()); private set
    /** Why a view is empty when it isn't for lack of marks or plays ("Needs the server"), or null. */
    var viewNote by mutableStateOf<String?>(null); private set
    /** True once the view's albums have arrived, so an empty view can say so. */
    var viewLoaded by mutableStateOf(false); private set
    private var viewJob: Job? = null

    // Albums
    var artist by mutableStateOf<Artist?>(null); private set
    private var allAlbums: List<Album> = emptyList()
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var albumSort by mutableStateOf(settings.albumSort); private set
    /** The artist was opened from a view by marks that shows only some of its albums; [albumsInAll] is how many it has. */
    var albumsLimited by mutableStateOf(false); private set
    var albumsInAll by mutableStateOf(0); private set
    /** "Show all": the artist's other albums too, not only the view's. */
    var showAllAlbums by mutableStateOf(false); private set

    // Tracks
    var album by mutableStateOf<Album?>(null); private set
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set

    private var albumsJob: Job? = null
    private var tracksJob: Job? = null
    private var albumsFrom: LibrarySource? = null
    private var tracksFrom: LibrarySource? = null

    // Server addresses (Settings): reachable or not, null while checking
    var addresses by mutableStateOf(jellyfin.addresses); private set
    var addressStatus by mutableStateOf<Map<String, Boolean?>>(emptyMap()); private set
    val currentAddress: String get() = jellyfin.serverUrl

    // Network changes come in bursts (Wi-Fi drops, mobile data takes over): act once they settle.
    // Capability updates (signal strength and so on) only re-check Wi-Fi; a network coming or going
    // also tries an unreachable server again.
    private var networkJob: Job? = null
    private var retryServer = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged(retry = true)
        override fun onLost(network: Network) = networkChanged(retry = true)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = networkChanged(retry = false)
    }
    private var lastServerAllowed = app.serverAllowed
    private val sendingMarks = Mutex() // before init, which may send marks at once

    init {
        if (isSignedIn) loadArtists(connectFirst = true)
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onCleared() {
        app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
    }

    private fun networkChanged(retry: Boolean) {
        viewModelScope.launch { // callbacks arrive on another thread
            retryServer = retryServer || retry
            networkJob?.cancel()
            networkJob = launch {
                delay(1500)
                checkNetwork(retryServer)
                retryServer = false
            }
        }
    }

    /**
     * After a network change, or "Wi-Fi only" being switched: go to the phone's albums if the server
     * may no longer be used or no longer answers, or back to the server if the phone was only showing
     * because it had to.
     */
    fun checkNetwork(retryServer: Boolean = false) {
        if (!isSignedIn) return
        val allowed = app.serverAllowed
        val changed = allowed != lastServerAllowed
        lastServerAllowed = allowed
        when {
            source == LibrarySource.Server && !allowed -> showOffline(NO_WIFI)
            // A new network: find the address that answers now (home or NetBird), or fall back to the phone.
            source == LibrarySource.Server && retryServer -> viewModelScope.launch {
                if (!jellyfin.connect() && source == LibrarySource.Server) showOffline(NO_SERVER)
            }
            offlineReason != null && allowed && (changed || retryServer) -> loadArtists(connectFirst = true)
        }
    }

    /** The toggle on the artists screen. Choosing the phone by hand stays put until it's switched back. */
    fun showPhone(phone: Boolean) {
        app.log(LogArea.App, if (phone) "Chose the phone's albums" else "Chose the server's library")
        offlineReason = null
        if (phone) {
            source = LibrarySource.Phone
            loadPhoneArtists()
        } else {
            source = LibrarySource.Server
            loadArtists(connectFirst = true)
        }
    }

    private fun showOffline(reason: String) {
        if (offlineReason != reason) app.log(LogArea.Server, reason)
        source = LibrarySource.Phone
        offlineReason = reason
        error = null
        loadPhoneArtists()
    }

    private fun loadPhoneArtists() {
        allArtists = withMarks(Library.fileArtists(app.db.offlineArtists()))
        applyFilter()
        loadView()
        search()
    }

    fun signIn() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val previous = settings.serverUrls
                val auth = jellyfin.login(serverUrl, userName.trim(), password)
                // Signing in again to the same server keeps its other addresses; a new server starts afresh.
                val keep = if (auth.serverId != null && auth.serverId == settings.serverId) previous else emptyList()
                val list = (listOf(jellyfin.serverUrl) + keep).distinct()
                jellyfin.configure(list, auth.accessToken, auth.user.id, auth.serverId)
                settings.serverUrls = jellyfin.addresses
                settings.serverId = auth.serverId
                addresses = jellyfin.addresses
                settings.userName = userName.trim()
                settings.userId = auth.user.id
                settings.accessToken = auth.accessToken
                serverUrl = jellyfin.serverUrl
                password = ""
                isSignedIn = true
                app.log(LogArea.Server, "Signed in as ${settings.userName} at ${jellyfin.serverUrl}")
                loadArtists()
            } catch (e: JellyfinException) {
                error = if (e.status == 401) "Wrong user name or password." else e.message
                app.log(LogArea.Server, "Sign-in failed: $error")
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}"
                app.log(LogArea.Server, "Sign-in failed: $error")
            } finally {
                busy = false
            }
        }
    }

    fun signOut() {
        app.log(LogArea.Server, "Signed out")
        viewModelScope.launch { jellyfin.logout() }
        settings.accessToken = null
        settings.userId = null
        jellyfin.configure(jellyfin.addresses, null, null, jellyfin.serverId)
        isSignedIn = false
        allArtists = emptyList(); artists = emptyList(); groupsPresent = emptySet()
        artist = null; allAlbums = emptyList(); albums = emptyList(); album = null; tracks = emptyList()
        view = LibraryView.Library; viewAll = emptyList(); viewFilter = FilteredArtists(emptyList(), emptyMap()); viewAlbums = emptyList()
        source = LibrarySource.Server; offlineReason = null
    }

    /** Refreshes the artists from wherever they come from now; the server falls back to the phone if it can't be reached. */
    fun loadArtists(connectFirst: Boolean = false) {
        if (source == LibrarySource.Phone && offlineReason == null) return loadPhoneArtists() // chosen by hand
        if (!app.serverAllowed) return showOffline(NO_WIFI)
        guarded(onUnreachable = { showOffline(NO_SERVER) }) {
            if (connectFirst) {
                // Sign-ins saved before the server id was recorded: learn it from the first address.
                if (jellyfin.serverId == null) {
                    jellyfin.serverIdAt(jellyfin.serverUrl)?.let { jellyfin.rememberServerId(it); settings.serverId = it }
                }
                // Find which address answers (home or away) before the first real request.
                if (jellyfin.addresses.size > 1 && !jellyfin.connect()) throw IOException("No address answers")
            }
            sendMarks() // first, so the list below already has them
            allArtists = fromServer(Library.sortArtists(jellyfin.albumArtists().items))
            if (offlineReason != null) app.log(LogArea.Server, "Server answers again: back to its library")
            source = LibrarySource.Server
            offlineReason = null
            applyFilter()
            loadView()
            search()
        }
    }

    fun setFilter(text: String) {
        artistFilter = text
        applyFilter()
        search()
    }

    fun changeSearchScope(scope: Set<SearchScope>) {
        if (scope.isEmpty()) return // something must be looked in
        searchScope = scope
        settings.searchScope = scope
        applyFilter()
        search()
    }

    /** The filter text narrows the artist list (the library or a view by marks), not the played views. */
    private val filtering get() = artistFilter.isNotBlank() && (view == LibraryView.Library || view.byMarks)

    /** What the albums screen's line calls the albums it shows: "Matching", or the view ("Favourites"). */
    val albumsLabel: String get() = if (filtering) "Matching" else view.label

    /** What matched, for an artist listed for some of its albums or tracks. */
    fun noteOf(a: Artist): MatchNote? = shortlist.noteOf(a.id)

    /**
     * Looks up album and track titles for the filter text, once typing pauses: Jellyfin's search, or
     * the albums on the phone. Until the answer comes, the hits for the earlier text narrow the list.
     */
    private fun search() {
        searchJob?.cancel()
        val term = artistFilter.trim()
        val scope = searchScope
        if (!Search.looksBeyondArtists(term, scope)) {
            hits = emptyList()
            searching = false
            return
        }
        searching = true
        val phone = source == LibrarySource.Phone
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                hits = withContext(Dispatchers.IO) {
                    if (phone) Search.hits(withMarks(app.db.offlineAlbums()), app.db.offlineTracks(), term, scope)
                    else {
                        val albums = async { if (SearchScope.Albums in scope) jellyfin.search(term, "MusicAlbum").items else emptyList() }
                        val tracks = async { if (SearchScope.Tracks in scope) jellyfin.search(term, "Audio").items else emptyList() }
                        Search.hits(albums.await(), tracks.await())
                    }
                }
                applyFilter()
            } catch (e: IOException) {
                // No answer: the list stays as narrowed so far (artist names still filter).
                app.log(LogArea.Server, "Search for \"$term\" failed: ${e.message}")
            }
            searching = false
        }
    }

    private fun applyFilter() {
        viewFilter = view.filter(allArtists, viewAll, showHidden)
        val base = if (view.byMarks) viewFilter else FilteredArtists.all(Library.visibleArtists(allArtists, showHidden))
        shortlist = Search.narrow(base, artistFilter, searchScope, hits, showHidden || view == LibraryView.Hidden)
        artists = shortlist.artists
        groupsPresent = Library.groupsPresent(artists)
        viewAlbums = if (view.byMarks) emptyList() else view.albums(viewAll, showHidden)
        applyAlbumFilter()
    }

    /**
     * The artist's albums as the list shows them: in a view by marks or with filter text, only the
     * marked or matching ones, unless "Show all".
     */
    private fun applyAlbumFilter() {
        val a = artist
        val only = when {
            a == null || !(view.byMarks || filtering) -> null
            shortlist.artists.none { it.id == a.id } -> emptySet() // its last mark just went
            else -> shortlist.albumsOf(a.id)
        }
        albumsLimited = only != null
        albumsInAll = view.albumsOf(allAlbums, null, showHidden).size
        albums = view.albumsOf(allAlbums, if (showAllAlbums) null else only, showHidden)
    }

    fun showAllAlbums(on: Boolean) {
        showAllAlbums = on
        applyAlbumFilter()
    }

    fun showHiddenItems(on: Boolean) {
        showHidden = on
        settings.showHidden = on
        applyFilter()
    }

    // ---------------------------------------------------------------- views

    /** The chooser on the artists screen; Back from a view goes to the Library. */
    fun showView(v: LibraryView) {
        if (v == view) return
        view = v
        viewAll = emptyList()
        applyFilter()
        loadView()
    }

    /** Fetches the view's albums from wherever the library comes from now (again after a refresh or a switch). */
    private fun loadView() {
        viewJob?.cancel()
        viewNote = null
        viewLoaded = false
        val v = view
        if (v == LibraryView.Library) return
        if (source == LibrarySource.Phone) {
            viewAll = when {
                v.byMarks -> withMarks(app.db.offlineAlbums())
                v == LibraryView.RecentlyPlayed -> withMarks(app.db.offlineRecentlyPlayed(PlayHistory.ALBUMS))
                else -> { viewNote = "Most needs the server."; emptyList() }
            }
            viewLoaded = true
            applyFilter()
            return
        }
        viewJob = guarded {
            // Off the main thread: Hidden reads every album in the library (megabytes of it).
            viewAll = withContext(Dispatchers.IO) { when (v) {
                LibraryView.Favourites -> fromServer(serverAlbums(jellyfin.allAlbums("IsFavorite").items))
                LibraryView.Liked -> fromServer(serverAlbums(jellyfin.allAlbums("Likes").items))
                // Jellyfin's Dislikes filter matches everything not liked: pick the hidden ones out here.
                LibraryView.Hidden -> fromServer(serverAlbums(jellyfin.allAlbums().items)).filter { it.marks.hidden }
                LibraryView.RecentlyPlayed -> {
                    val ids = PlayHistory.recent(playedTracks("DatePlayed"))
                    inOrder(ids, jellyfin.albumsById(ids))
                }
                else -> {
                    val tracks = playedTracks("PlayCount")
                    val found = jellyfin.albumsById(PlayHistory.candidates(tracks))
                    val ids = PlayHistory.most(tracks, found.associate { it.id to (it.childCount ?: 0) })
                    inOrder(ids, found)
                }
            } }
            viewLoaded = true
            applyFilter()
        }
    }

    private suspend fun playedTracks(sortBy: String) = jellyfin.playedTracks(sortBy).items.mapNotNull(PlayedTrack::from)

    private fun serverAlbums(items: List<BaseItem>) = items.map { Album.from(it, "") }

    /** The albums of [ids] in that order, with the marks the phone knows. */
    private fun inOrder(ids: List<String>, items: List<BaseItem>): List<Album> {
        val byId = fromServer(serverAlbums(items)).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** The artist an album from a played view is filed under (for opening, playing and downloading it). */
    fun artistOf(a: Album): Artist? {
        val id = a.artistId ?: return null
        return allArtists.firstOrNull { it.id == id }
            ?: Artist(id, a.artist, a.artist, SortOrder.groupOf(a.artist)) // not in the list: filed by its name
    }

    /** Opens an album from a played view: its artist becomes the one being browsed. */
    fun openViewAlbum(a: Album) {
        val owner = artistOf(a)
        if (owner != null && owner.id != artist?.id) {
            artist = owner
            allAlbums = emptyList(); albums = emptyList(); albumsLimited = false
            albumsFrom = null
        }
        openAlbum(a)
    }

    fun firstIndexInGroup(group: String) = Library.firstIndexInGroup(artists, group)

    /** Index of the artist chosen last time, so the list can open there. */
    fun lastArtistIndex(): Int = artists.indexOfFirst { it.id == settings.lastArtistId }

    fun openArtist(a: Artist) {
        showAllAlbums = false
        if (artist?.id == a.id && allAlbums.isNotEmpty() && albumsFrom == source) return applyAlbumFilter()
        artist = a
        allAlbums = emptyList(); albums = emptyList(); albumsLimited = false
        albumsFrom = source
        settings.lastArtistId = a.id
        albumsJob?.cancel()
        if (source == LibrarySource.Phone) {
            showAlbums(withMarks(app.db.offlineAlbums(a.id)))
            return
        }
        albumsJob = guarded {
            showAlbums(fromServer(jellyfin.albums(a.id).items.map { Album.from(it, a.name) }))
        }
    }

    /** Pull to refresh on the albums: load them again from wherever they come from now. */
    fun refreshAlbums() {
        val a = artist ?: return
        albumsFrom = null
        openArtist(a)
    }

    private fun showAlbums(list: List<Album>) {
        allAlbums = Library.sortAlbums(list, albumSort)
        applyAlbumFilter()
    }

    fun setSort(mode: AlbumSortMode) {
        albumSort = mode
        settings.albumSort = mode
        showAlbums(allAlbums)
    }

    // ---------------------------------------------------------------- marks (heart, like, hidden)

    fun markArtist(a: Artist, marks: Marks) = mark(a.id, a.marks, marks)
    fun markAlbum(a: Album, marks: Marks) = mark(a.id, a.marks, marks)

    /** Shown at once and kept on the phone; sent to Jellyfin now, or when it can next be reached. */
    private fun mark(id: String, old: Marks, new: Marks) {
        if (old == new) return
        app.db.changeMarks(id, new)
        allArtists = allArtists.map { if (it.id == id) it.copy(marks = new) else it }
        allAlbums = allAlbums.map { if (it.id == id) it.copy(marks = new) else it }
        viewAll = viewAll.map { if (it.id == id) it.copy(marks = new) else it }
        artist = artist?.let { if (it.id == id) it.copy(marks = new) else it }
        album = album?.let { if (it.id == id) it.copy(marks = new) else it }
        applyFilter()
        viewModelScope.launch { sendMarks() }
    }


    /** Sends the changes made on the phone; stops at the first that can't get through, to try later. */
    private suspend fun sendMarks() = sendingMarks.withLock {
        if (!isSignedIn || !app.serverAllowed) return@withLock
        for ((id, m) in app.db.pendingMarks()) {
            try {
                jellyfin.setFavourite(id, m.favourite)
                jellyfin.setLikes(id, m.likes)
            } catch (e: JellyfinException) {
                if (e.status == 401) return@withLock // signed out; the next request says so
                // Otherwise the server won't take it (the item's gone): don't keep trying.
                app.log(LogArea.Marks, "Server refused marks for item $id: ${e.message}")
            } catch (_: IOException) {
                return@withLock
            }
            app.db.marksSent(id, m)
        }
    }

    /** Records the marks the server sent, then shows the ones the phone has (its own unsent changes win). */
    private fun fromServer(artists: List<Artist>): List<Artist> {
        app.db.saveServerMarks(artists.associate { it.id to it.marks })
        return withMarks(artists)
    }

    @JvmName("albumsFromServer")
    private fun fromServer(albums: List<Album>): List<Album> {
        app.db.saveServerMarks(albums.associate { it.id to it.marks })
        return withMarks(albums)
    }

    private fun withMarks(artists: List<Artist>): List<Artist> {
        val marks = app.db.marks(artists.map { it.id })
        return artists.map { a -> marks[a.id]?.let { a.copy(marks = it) } ?: a }
    }

    @JvmName("albumsWithMarks")
    private fun withMarks(albums: List<Album>): List<Album> {
        val marks = app.db.marks(albums.map { it.id })
        return albums.map { a -> marks[a.id]?.let { a.copy(marks = it) } ?: a }
    }

    fun openAlbum(a: Album) {
        if (album?.id == a.id && tracks.isNotEmpty() && tracksFrom == source) return
        album = a
        tracks = emptyList()
        tracksFrom = source
        tracksJob?.cancel()
        tracksJob = guarded { tracks = loadTracks(a) }
    }

    /** For "Play" / "Add to queue" straight from the album grid, without opening the album. */
    fun withTracks(a: Album, action: (List<Track>) -> Unit) {
        guarded { action(if (album?.id == a.id && tracks.isNotEmpty() && tracksFrom == source) tracks else loadTracks(a)) }
    }

    /** Downloads the album without queueing it, and keeps it (never removed automatically). */
    fun download(a: Album, artist: Artist? = this.artist) {
        artist ?: return
        withTracks(a) { app.downloads.request(a, artist, it, keep = true) }
    }

    fun downloadedTrackIds(albumId: String): Set<String> = app.db.downloadedTrackIds(albumId)

    /** Every album with a download, by id, for the download marks. */
    val downloads get() = app.downloads.progress

    /** The album's tracks; on the phone, only the ones that are downloaded. */
    private suspend fun loadTracks(a: Album): List<Track> =
        if (source == LibrarySource.Phone) app.db.downloadedTracks(a.id).filter { it.file != null }.map { it.track }
        else jellyfin.tracks(a.id).items.map(Track::from)

    /** On the phone, the downloaded cover.jpg; from the server, its image at [size] pixels. */
    fun coverUrl(a: Album, size: Int): Any =
        (if (source == LibrarySource.Phone) app.downloads.coverUri(a.id) else null)
            ?: jellyfin.imageUrl(a.id, a.imageTag, size)

    fun clearError() { error = null }

    // ---------------------------------------------------------------- server addresses

    /** Adds another address for the same server (tried after the existing ones). */
    fun addAddress(text: String) {
        val a = com.tobeksoft.tunerfish.core.JellyfinClient.normaliseServerUrl(text)
        if (a.isEmpty() || a in addresses) return
        saveAddresses(addresses + a)
    }

    fun removeAddress(a: String) {
        if (addresses.size > 1) saveAddresses(addresses - a)
    }

    fun moveAddressUp(a: String) {
        val i = addresses.indexOf(a)
        if (i <= 0) return
        saveAddresses(addresses.toMutableList().apply { removeAt(i); add(i - 1, a) })
    }

    private fun saveAddresses(list: List<String>) {
        jellyfin.configure(list, jellyfin.token, jellyfin.userId, jellyfin.serverId)
        settings.serverUrls = jellyfin.addresses
        addresses = jellyfin.addresses
        checkAddresses()
    }

    /** Checks every address (for the marks in Settings) and switches to the best one that answers. */
    fun checkAddresses() {
        addressStatus = addresses.associateWith { null }
        viewModelScope.launch {
            for (a in addresses) launch {
                val ok = jellyfin.isReachable(a) // wait first, then merge into the map as it is now
                addressStatus = addressStatus + (a to ok)
            }
        }
        viewModelScope.launch { jellyfin.connect(); addresses = jellyfin.addresses }
    }

    /**
     * Runs a server call with the busy flag and a readable error; a rejected token signs you out.
     * [onUnreachable], if given, replaces the error when the server can't be reached at all.
     */
    private fun guarded(onUnreachable: (() -> Unit)? = null, block: suspend () -> Unit): Job = viewModelScope.launch {
        busy = true
        error = null
        try {
            block()
        } catch (e: JellyfinException) {
            if (e.status == 401) {
                app.log(LogArea.Server, "The server no longer accepts this sign-in (HTTP 401): signed out")
                signOut()
            } else {
                error = e.message
                app.log(LogArea.Server, "Error: ${e.message}")
            }
        } catch (e: IOException) {
            if (onUnreachable != null) onUnreachable()
            else {
                error = "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}"
                app.log(LogArea.Server, error!!)
            }
        } finally {
            busy = false
        }
    }

    private companion object {
        const val NO_SERVER = "Can't reach the server: albums on this phone"
        const val NO_WIFI = "No Wi-Fi: albums on this phone"
    }
}
