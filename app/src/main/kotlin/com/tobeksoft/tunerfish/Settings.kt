package com.tobeksoft.tunerfish

import android.content.Context
import androidx.core.content.edit
import com.tobeksoft.tunerfish.core.AlbumSortMode
import com.tobeksoft.tunerfish.core.DownloadLimit
import com.tobeksoft.tunerfish.core.SearchScope
import com.tobeksoft.tunerfish.core.ThemeMode
import com.tobeksoft.tunerfish.core.Tone
import java.util.UUID

/**
 * Everything the app remembers, in the app's private SharedPreferences.
 * Choices are stored as words, never numbers, and an unknown value resets only that one
 * setting. Keys use the desktop app's names. Never rename or reuse a key.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Connection (the password is never stored, only the session token)

    /**
     * The server's addresses, most preferred first (e.g. home network, then NetBird), one per line.
     * Falls back to the single-address key "ServerUrl" from before there could be several.
     */
    var serverUrls: List<String>
        get() = prefs.getString("ServerUrls", null)?.lines()?.filter { it.isNotBlank() }
            ?: listOfNotNull(prefs.getString("ServerUrl", null))
        set(value) = prefs.edit { putString("ServerUrls", value.joinToString("\n")) }
    /** Jellyfin's id for the server, so the token is only ever sent to that server. */
    var serverId: String?
        get() = prefs.getString("ServerId", null)
        set(value) = prefs.edit { putString("ServerId", value) }
    var userName: String?
        get() = prefs.getString("UserName", null)
        set(value) = prefs.edit { putString("UserName", value) }
    var userId: String?
        get() = prefs.getString("UserId", null)
        set(value) = prefs.edit { putString("UserId", value) }
    var accessToken: String?
        get() = prefs.getString("AccessToken", null)
        set(value) = prefs.edit { putString("AccessToken", value) }

    /** Made once per install; identifies this phone in Jellyfin's device list. */
    val deviceId: String =
        prefs.getString("DeviceId", null) ?: UUID.randomUUID().toString().replace("-", "").also { id ->
            prefs.edit { putString("DeviceId", id) }
        }

    // Browsing
    var albumSort: AlbumSortMode
        get() = AlbumSortMode.parse(prefs.getString("AlbumSort", null))
        set(value) = prefs.edit { putString("AlbumSort", value.name) }
    var lastArtistId: String?
        get() = prefs.getString("LastArtistId", null)
        set(value) = prefs.edit { putString("LastArtistId", value) }

    // Playback: the queue is saved in AppDatabase's QueueItems, and these say where in it to carry on.
    /** Start each fresh launch with an empty queue instead of the saved one. */
    var clearQueueOnExit: Boolean
        get() = prefs.getBoolean("ClearQueueOnExit", false)
        set(value) = prefs.edit { putBoolean("ClearQueueOnExit", value) }
    var queueIndex: Int
        get() = prefs.getInt("QueueIndex", 0)
        set(value) = prefs.edit { putInt("QueueIndex", value) }
    var queuePositionMs: Long
        get() = prefs.getLong("QueuePositionMs", 0)
        set(value) = prefs.edit { putLong("QueuePositionMs", value) }
    /** The queue played to its end: nothing is playing or paused, so the mini player stays hidden. */
    var queueFinished: Boolean
        get() = prefs.getBoolean("QueueFinished", false)
        set(value) = prefs.edit { putBoolean("QueueFinished", value) }

    /** The app crashed last time: offer to share the log at the next start. */
    var crashToShare: Boolean
        get() = prefs.getBoolean("CrashToShare", false)
        set(value) = prefs.edit(commit = true) { putBoolean("CrashToShare", value) } // committed: the process is about to die

    /** Whether the app has asked for permission to show notifications (Android 13+): asked once only. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean("NotificationsAsked", false)
        set(value) = prefs.edit { putBoolean("NotificationsAsked", value) }

    // Sound: bass and treble in whole dB (core's Tone), 0 = flat. Quantities, so plain numbers.
    var bass: Int
        get() = Tone.clamp(prefs.getInt("Bass", 0))
        set(value) = prefs.edit { putInt("Bass", Tone.clamp(value)) }
    var treble: Int
        get() = Tone.clamp(prefs.getInt("Treble", 0))
        set(value) = prefs.edit { putInt("Treble", Tone.clamp(value)) }

    // Marks: show artists and albums marked hidden (thumbs down) while browsing.
    var showHidden: Boolean
        get() = prefs.getBoolean("ShowHidden", false)
        set(value) = prefs.edit { putBoolean("ShowHidden", value) }

    /** What the filter box on the artists screen looks in: artist names, album titles, track titles. */
    var searchScope: Set<SearchScope>
        get() = SearchScope.parse(prefs.getString("FilterScope", null))
        set(value) = prefs.edit { putString("FilterScope", SearchScope.format(value)) }

    // Network
    /** Without Wi-Fi, only what's on the phone: nothing streams and nothing downloads. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean("WifiOnly", false)
        set(value) = prefs.edit { putBoolean("WifiOnly", value) }

    // Appearance
    var theme: ThemeMode
        get() = ThemeMode.parse(prefs.getString("Theme", null))
        set(value) = prefs.edit { putString("Theme", value.name) }

    // Downloads: a folder picked with Android's folder picker (a content:// tree URI), or null for
    // the app's own storage.
    var downloadFolder: String?
        get() = prefs.getString("DownloadFolder", null)
        set(value) = prefs.edit { putString("DownloadFolder", value) }
    /** Replace another app's copy of an album in the download folder without asking first. */
    var replaceOtherCopies: Boolean
        get() = prefs.getBoolean("ReplaceOtherCopies", false)
        set(value) = prefs.edit { putBoolean("ReplaceOtherCopies", value) }
    /**
     * Whether the user has chosen where downloads go (null [downloadFolder] means app storage, so that
     * alone can't tell "chose app storage" from "never asked"). A folder already set counts as chosen.
     */
    var downloadFolderChosen: Boolean
        get() = prefs.getBoolean("DownloadFolderChosen", false) || downloadFolder != null
        set(value) = prefs.edit { putBoolean("DownloadFolderChosen", value) }
    /** Space downloads may take before albums that aren't kept are removed. */
    var downloadLimit: DownloadLimit
        get() = DownloadLimit.parse(prefs.getString("DownloadLimit", null))
        set(value) = prefs.edit { putString("DownloadLimit", value.name) }
}
