package com.tobeksoft.tunerfish.playback

import com.tobeksoft.tunerfish.core.LogArea
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.JellyfinException
import com.tobeksoft.tunerfish.core.PlayRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Tells Jellyfin what's playing (start, progress, stop), so it keeps the user's play counts and last
 * played dates, the history the "played" views are built from. When the server can't be told (no
 * network, Wi-Fi only), a play that counts by [PlayRule] is kept in PendingPlays and sent as "played
 * at" the next time a report gets through.
 *
 * Player events arrive on the main thread; the reports go out one at a time, in order, on a
 * background queue that outlives the service, so the last stop report still goes when it closes.
 */
@OptIn(UnstableApi::class) // Player.PositionInfo's item index
class PlayReporter(private val app: TunerFishApp, private val player: Player) : Player.Listener {
    /** The track being reported: [started] once the server has its start report. */
    private class Track(val id: String) {
        val session: String = UUID.randomUUID().toString()
        var positionMs = 0L
        var durationMs: Long? = null
        @Volatile var started = false
    }

    private var current: Track? = null
    private val reports = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (report in reports) report()
        }
        send { sendPending() } // plays left from last time
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val t = current
        when {
            isPlaying && (t == null || t.id != player.currentMediaItem?.mediaId) -> begin()
            t != null && player.playbackState != Player.STATE_ENDED -> progress(t, paused = !isPlaying)
        }
    }

    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
        val t = current ?: return
        if (old.mediaItemIndex == new.mediaItemIndex && old.mediaItem?.mediaId == t.id) {
            progress(t, paused = !player.isPlaying) // a seek within the track
            return
        }
        finish(t, old.positionMs) // moved on: the old track stopped where it was
        if (player.isPlaying) begin()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // The queue was replaced (Play album) or emptied without a discontinuity: close the old track.
        val t = current ?: return
        if (t.id == mediaItem?.mediaId) return
        finish(t, t.positionMs)
        if (player.isPlaying) begin()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state != Player.STATE_ENDED) return
        current?.let { finish(it, it.durationMs ?: player.currentPosition) } // the end of the queue
    }

    /** Every few seconds while playing (PlaybackService): keeps the server's idea of the position fresh. */
    fun tick() {
        val t = current ?: return
        if (t.id == player.currentMediaItem?.mediaId) progress(t, paused = !player.isPlaying)
    }

    /** The service is closing: the playing track stops here. */
    fun close() {
        current?.let { finish(it, player.currentPosition) }
    }

    private fun begin() {
        val id = player.currentMediaItem?.mediaId ?: return
        val t = Track(id).also { current = it }
        note(t)
        send {
            if (!canReport()) return@send
            try {
                app.jellyfin.reportStart(t.id, t.positionMs, paused = false, playSessionId = t.session)
                t.started = true
                sendPending() // before this track's stop, so dates go in order
            } catch (_: IOException) {
                // Offline: the stop decides whether it's kept for later.
            }
        }
    }

    private fun progress(t: Track, paused: Boolean) {
        note(t)
        val position = t.positionMs
        send {
            if (!t.started || !canReport()) return@send
            try {
                app.jellyfin.reportProgress(t.id, position, paused, t.session)
            } catch (_: IOException) {
            }
        }
    }

    private fun finish(t: Track, positionMs: Long) {
        if (current === t) current = null
        val at = Instant.now()
        send {
            if (t.started && canReport()) {
                try {
                    app.jellyfin.reportStopped(t.id, positionMs, t.session)
                    return@send // the server decides whether it counts
                } catch (_: IOException) {
                }
            }
            if (app.isSignedIn && PlayRule.counts(positionMs, t.durationMs)) {
                app.db.addPendingPlay(t.id, at)
                app.log(LogArea.Plays, "Server not told of a play (track ${t.id}): kept to send later")
            }
        }
    }

    /** Sends the plays kept while offline, oldest first, until one can't be sent. */
    private suspend fun sendPending() {
        if (!canReport()) return
        for (play in app.db.pendingPlays()) {
            try {
                app.jellyfin.markPlayed(play.trackId, play.at)
                app.log(LogArea.Plays, "Sent a play kept from ${play.at} (track ${play.trackId})")
            } catch (e: JellyfinException) {
                // The server refused it (e.g. the track is gone): don't try again.
                app.log(LogArea.Plays, "Server refused a kept play (track ${play.trackId}): ${e.message}")
            } catch (_: IOException) {
                return
            }
            app.db.removePendingPlay(play.id)
        }
    }

    /** Position and length from the player, while it's still on [t]'s track. */
    private fun note(t: Track) {
        if (player.currentMediaItem?.mediaId != t.id) return
        t.positionMs = player.currentPosition
        player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { t.durationMs = it }
    }

    private fun canReport() = app.isSignedIn && app.serverAllowed

    private fun send(report: suspend () -> Unit) {
        reports.trySend(report)
    }
}
