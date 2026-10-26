package com.tobeksoft.tunerfish.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.Album
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.DataUse
import com.tobeksoft.tunerfish.core.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** "Peace & Love is 320 MB (FLAC). Play it on mobile data?", asked before [go] runs. */
data class DataQuestion(val text: String, val confirm: String, val go: () -> Unit)

/** One entry in the play queue, as the queue screen shows it. */
data class QueueEntry(val id: String, val title: String, val subtitle: String, val albumId: String?)

/** The UI's view of the player in [PlaybackService], through a MediaController. */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TunerFishApp
    private var controller: MediaController? = null

    var hasMedia by mutableStateOf(false); private set
    /** The queue has played to its end (now, or before the app last closed): nothing playing or paused. */
    var finished by mutableStateOf(false); private set
    /** Set when an album goes into an empty queue; the UI opens the queue and calls [queueShown]. */
    var showQueue by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var subtitle by mutableStateOf(""); private set
    var artworkUri by mutableStateOf<Uri?>(null); private set
    var currentId by mutableStateOf<String?>(null); private set
    var currentIndex by mutableIntStateOf(-1); private set
    var queue by mutableStateOf<List<QueueEntry>>(emptyList()); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    /** Waiting for a yes before queueing a big album on mobile data, or null. */
    var dataQuestion by mutableStateOf<DataQuestion?>(null); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            refresh()
            // The player has no position events; poll while the UI is around.
            while (isActive) {
                positionMs = c.currentPosition
                durationMs = c.duration.coerceAtLeast(0)
                delay(500)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        hasMedia = item != null
        // A finished queue comes back unprepared (idle) after a restart; the service remembers it ended.
        finished = c.playbackState == Player.STATE_ENDED || (c.playbackState == Player.STATE_IDLE && app.settings.queueFinished)
        isPlaying = c.isPlaying
        title = item?.mediaMetadata?.title?.toString() ?: ""
        subtitle = item?.mediaMetadata?.let(::subtitleOf) ?: ""
        artworkUri = item?.mediaMetadata?.artworkUri
        currentId = item?.mediaId
        currentIndex = c.currentMediaItemIndex
        queue = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            QueueEntry(
                m.mediaId, m.mediaMetadata.title?.toString() ?: "", subtitleOf(m.mediaMetadata),
                m.mediaMetadata.extras?.getString(PlaybackService.EXTRA_ALBUM_ID),
            )
        }
        positionMs = c.currentPosition
        durationMs = c.duration.coerceAtLeast(0)
    }

    private fun subtitleOf(m: MediaMetadata) =
        listOfNotNull(m.artist, m.albumTitle).filter { it.isNotBlank() }.joinToString(" — ")

    private fun itemsOf(tracks: List<Track>, album: Album?): List<MediaItem> = tracks.map { t ->
        val albumId = album?.id ?: t.albumId
        val imageTag = album?.imageTag
        val art = albumId?.let { app.downloads.coverUri(it)?.toString() ?: app.jellyfin.imageUrl(it, imageTag, 600) }
        MediaItem.Builder()
            .setMediaId(t.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(album?.title ?: t.album)
                    .setArtworkUri(art?.let(Uri::parse))
                    .setExtras(bundleOf(PlaybackService.EXTRA_ALBUM_ID to albumId, PlaybackService.EXTRA_IMAGE_TAG to imageTag))
                    .build(),
            )
            .build()
    }

    /**
     * Replaces the queue with the album and plays from [startIndex]. Queueing an album downloads it,
     * filed under [artist] for browsing offline.
     */
    fun play(tracks: List<Track>, album: Album, artist: Artist?, startIndex: Int = 0) =
        askAboutData(tracks, album, "Play") { playNow(tracks, album, artist, startIndex) }

    private fun playNow(tracks: List<Track>, album: Album, artist: Artist?, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        download(tracks, album, artist)
        c.setMediaItems(itemsOf(tracks, album), startIndex, 0)
        c.prepare()
        c.play()
    }

    /** Adds the album to the end of the queue (and downloads it). Never starts playing. */
    fun enqueue(tracks: List<Track>, album: Album, artist: Artist?) =
        askAboutData(tracks, album, "Add") { enqueueNow(tracks, album, artist) }

    private fun enqueueNow(tracks: List<Track>, album: Album, artist: Artist?) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        download(tracks, album, artist)
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(itemsOf(tracks, album))
        // Adding never starts the music ("Play album" does): into an empty queue, the first track
        // loads paused and the queue opens, showing what's lined up.
        if (wasEmpty) {
            c.pause()
            c.prepare()
            showQueue = true
        }
    }

    fun queueShown() { showQueue = false }

    /**
     * Off Wi-Fi, queueing an album fetches whatever of it isn't on the phone yet over mobile data.
     * Above DataUse's threshold, ask first; otherwise (or on Wi-Fi) just [go].
     */
    private fun askAboutData(tracks: List<Track>, album: Album, verb: String, go: () -> Unit) {
        if (app.onWifi) return go()
        val onPhone = app.db.downloadedTrackIds(album.id)
        val bytes = tracks.filter { it.id !in onPhone }.sumOf { it.bytes ?: 0 }
        if (!DataUse.shouldAsk(bytes)) return go()
        val format = tracks.firstNotNullOfOrNull { it.container }?.uppercase()?.let { " ($it)" } ?: ""
        val action = if (verb == "Play") "Play it" else "Add it to the queue"
        dataQuestion = DataQuestion("${album.title} is ${DataUse.sizeText(bytes)}$format. $action on mobile data?", verb, go)
    }

    fun answerDataQuestion(yes: Boolean) {
        val q = dataQuestion ?: return
        dataQuestion = null
        if (yes) q.go()
    }

    private fun download(tracks: List<Track>, album: Album, artist: Artist?) {
        if (artist != null) app.downloads.request(album, artist, tracks, keep = false)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    /** Like most players: more than a few seconds in, "previous" restarts the track. */
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun next() { controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs = ms }
    fun playAt(index: Int) { controller?.run { seekTo(index, 0); prepare(); play() } }
    fun remove(index: Int) { controller?.removeMediaItem(index) }

    /**
     * The queue positions of the album the track at [index] belongs to: the unbroken run of its
     * album's tracks around it (the same album queued twice is two runs). Just [index] if unknown.
     */
    fun albumRunAt(index: Int): IntRange {
        val albumId = queue.getOrNull(index)?.albumId ?: return index..index
        var first = index
        while (first > 0 && queue[first - 1].albumId == albumId) first--
        var last = index
        while (last < queue.lastIndex && queue[last + 1].albumId == albumId) last++
        return first..last
    }

    /** Removes the whole album around [index] from the queue (not from the phone). */
    fun removeAlbumAt(index: Int) {
        val run = albumRunAt(index)
        controller?.removeMediaItems(run.first, run.last + 1)
    }
    fun clear() { controller?.run { stop(); clearMediaItems() } }

    /**
     * Exit from the menu: stops the music and shuts the playback service down, notification and all,
     * once the screen has closed and this controller lets go. The place in the queue is saved on the way.
     */
    fun exit() {
        controller?.pause()
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
