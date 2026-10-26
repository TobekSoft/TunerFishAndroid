package com.tobeksoft.tunerfish.playback

import com.tobeksoft.tunerfish.core.LogArea
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import androidx.core.os.bundleOf
import com.google.common.util.concurrent.ListenableFuture
import com.tobeksoft.tunerfish.AppDatabase
import com.tobeksoft.tunerfish.MainActivity
import com.tobeksoft.tunerfish.QueuedTrack
import com.tobeksoft.tunerfish.TunerFishApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Owns the player. The play queue IS the player's playlist: the UI changes it only through a
 * MediaController, so there's nothing to keep in step. Media3 shows the notification and the
 * lock-screen controls and keeps the service in the foreground while playing.
 *
 * Queued items carry only the Jellyfin item id (as a tunerfish://audio/{id} URI), turned into a
 * real address when a track starts: its downloaded file if it has one, otherwise a stream from
 * whichever server address is in use then, so a queue built at home keeps playing over NetBird
 * once you've left.
 *
 * The queue is saved to the database whenever it changes, and the place in it (track and position)
 * to Settings, so the next start carries on where this one stopped, paused. What plays is reported
 * to Jellyfin (PlayReporter) for the user's play history.
 */
@OptIn(UnstableApi::class) // Media3's ResolvingDataSource and cache classes
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var reporter: PlayReporter? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db: AppDatabase get() = (application as TunerFishApp).db

    override fun onCreate() {
        super.onCreate()
        val app = application as TunerFishApp

        // Local files play directly; http goes through the stream cache (see TunerFishApp.streamSource).
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, app.streamSource)) { spec ->
            if (spec.uri.scheme != SCHEME) return@Factory spec
            val id = spec.uri.lastPathSegment ?: ""
            app.downloads.localFile(id)?.let { return@Factory spec.withUri(it) }
            if (!app.serverAllowed) throw IOException("Wi-Fi only, and this track isn't on the phone")
            spec.buildUpon().setUri(app.jellyfin.streamUrl(id).toUri()).setKey(id).build()
        }
        // Bass and treble (ToneProcessor) run in the audio sink, after decoding.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(ToneProcessor()))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)  // keep streaming with the screen off
            .build()
        player.addListener(Reconnect(app, player))

        restoreQueue(app, player)
        val saver = SaveQueue(app, player)
        player.addListener(saver)
        val reporter = PlayReporter(app, player).also { this.reporter = it }
        player.addListener(reporter)
        // The position moves without events; save it (and tell Jellyfin) now and then, in case the app is killed.
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) {
                    saver.savePlace()
                    reporter.tick()
                }
            }
        }

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Swiping the app away stops the service only if nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            SaveQueue(application as TunerFishApp, player).savePlace()
            reporter?.close()
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * When a stream fails because the network changed (left home, NetBird came up), find the address
     * that answers now and carry on from the same spot. Once per failure, so a dead server can't loop.
     * If the server can't be used (no answer, or Wi-Fi only without Wi-Fi), skip ahead to the next
     * queued track that's on the phone, so an offline queue plays what it can.
     */
    private inner class Reconnect(private val app: TunerFishApp, private val player: ExoPlayer) : Player.Listener {
        private var tried = false

        override fun onPlayerError(error: PlaybackException) {
            val track = player.currentMediaItem?.mediaMetadata?.title ?: "?"
            app.log(LogArea.Playback, "$track: ${error.errorCodeName}, ${error.message}" + (error.cause?.message?.let { " ($it)" } ?: ""))
            val network = error.errorCode in 2000..2999 // PlaybackException's IO error range
            if (!network) return
            val retry = !tried
            tried = true
            scope.launch {
                val wanted = player.playWhenReady
                if (retry && app.serverAllowed && app.jellyfin.connect()) {
                    app.log(LogArea.Playback, "Server answers at ${app.jellyfin.serverUrl}: carrying on")
                    player.prepare()
                    player.playWhenReady = wanted
                } else {
                    skipToDownloaded(wanted)
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) tried = false
        }

        private fun skipToDownloaded(play: Boolean) {
            val next = (player.currentMediaItemIndex + 1 until player.mediaItemCount)
                .firstOrNull { app.db.trackFile(player.getMediaItemAt(it).mediaId) != null }
            if (next == null) return app.log(LogArea.Playback, "No server and nothing downloaded further on: stopped")
            app.log(LogArea.Playback, "No server: skipping to ${player.getMediaItemAt(next).mediaMetadata.title}, which is downloaded")
            tried = false
            player.seekTo(next, 0)
            player.prepare()
            player.playWhenReady = play
        }
    }

    /** Puts the saved queue back, paused at the saved spot; it prepares when play is pressed. */
    private fun restoreQueue(app: TunerFishApp, player: ExoPlayer) {
        if (app.settings.clearQueueOnExit) {
            db.saveQueue(emptyList())
            return
        }
        val items = db.loadQueue().map { withStreamUri(it.toMediaItem(app)) }
        if (items.isEmpty()) return
        player.setMediaItems(items, app.settings.queueIndex.coerceIn(0, items.lastIndex), app.settings.queuePositionMs)
    }

    /** Saves the queue when its tracks change, and the place in it when playback moves or stops. */
    private inner class SaveQueue(private val app: TunerFishApp, private val player: Player) : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            db.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).toQueuedTrack() })
            savePlace()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            savePlace()
            // Recently played albums are the last the download clean-up removes.
            mediaItem?.mediaMetadata?.extras?.getString(EXTRA_ALBUM_ID)?.let(db::touchAlbum)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePlace() }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) = savePlace()
        override fun onPlaybackStateChanged(playbackState: Int) = savePlace()

        /**
         * At the end of the queue, saves the start of the last track, so play has something to play,
         * and that it finished (no mini player after a restart).
         */
        fun savePlace() {
            val ended = player.playbackState == Player.STATE_ENDED
            app.settings.queueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            app.settings.queuePositionMs = if (ended) 0 else player.currentPosition
            app.settings.queueFinished = ended
        }
    }

    /** Items arrive with an id and metadata; give each the id-only URI resolved above. */
    private class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map(::withStreamUri).toMutableList(),
        )
    }

    companion object {
        private const val SCHEME = "tunerfish"
        /** MediaMetadata extras that let a saved item rebuild its cover URL on whichever address is in use. */
        const val EXTRA_ALBUM_ID = "AlbumId"
        const val EXTRA_IMAGE_TAG = "ImageTag"

        private fun withStreamUri(item: MediaItem) =
            item.buildUpon().setUri("$SCHEME://audio/${item.mediaId}".toUri()).build()

        private fun MediaItem.toQueuedTrack() = QueuedTrack(
            trackId = mediaId,
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            album = mediaMetadata.albumTitle?.toString() ?: "",
            albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
            imageTag = mediaMetadata.extras?.getString(EXTRA_IMAGE_TAG),
        )

        private fun QueuedTrack.toMediaItem(app: TunerFishApp) = MediaItem.Builder()
            .setMediaId(trackId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumId?.let { app.downloads.coverUri(it) ?: app.jellyfin.imageUrl(it, imageTag, 600).toUri() })
                    .setExtras(bundleOf(EXTRA_ALBUM_ID to albumId, EXTRA_IMAGE_TAG to imageTag))
                    .build(),
            )
            .build()
    }
}
