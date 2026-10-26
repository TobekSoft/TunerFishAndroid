package com.tobeksoft.tunerfish

import com.tobeksoft.tunerfish.playback.ToneLevels
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.tobeksoft.tunerfish.core.JellyfinClient
import com.tobeksoft.tunerfish.core.ThemeMode
import com.tobeksoft.tunerfish.downloads.Downloads
import java.io.File
import java.time.Instant
import java.time.ZoneId
import android.util.Log
import com.tobeksoft.tunerfish.core.AppLog
import com.tobeksoft.tunerfish.core.LogArea
import com.tobeksoft.tunerfish.core.LogEntry

/** Holds the app-wide singletons: settings, the database, downloads and the Jellyfin client (shared with the playback service). */
@OptIn(UnstableApi::class) // Media3's cache classes, for streamSource
class TunerFishApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var jellyfin: JellyfinClient
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var downloads: Downloads
        private set
    lateinit var version: String
        private set

    /**
     * Streams from the server, through a cache keyed by Jellyfin item id (set as the DataSpec key).
     * The player and the download worker both read through it, so the part of a track already
     * heard isn't fetched again for its download. Small: the downloads are what's kept.
     */
    val streamSource: CacheDataSource.Factory by lazy {
        val cache = SimpleCache(
            File(cacheDir, "streams"), LeastRecentlyUsedCacheEvictor(STREAM_CACHE_BYTES), StandaloneDatabaseProvider(this),
        )
        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    }

    /** Observable so the whole UI re-themes as soon as it changes. Saved immediately. */
    var theme by mutableStateOf(ThemeMode.System)
        private set

    fun changeTheme(mode: ThemeMode) {
        theme = mode
        settings.theme = mode
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        theme = settings.theme
        ToneLevels.bass = settings.bass
        ToneLevels.treble = settings.treble
        jellyfin = JellyfinClient(settings.deviceId, getString(R.string.app_name), version, Build.MODEL)
        jellyfin.configure(settings.serverUrls, settings.accessToken, settings.userId, settings.serverId)
        db = AppDatabase(this)
        catchCrashes()
        log(LogArea.App, "Started $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        jellyfin.onConnect = { log(LogArea.Server, it) }
        downloads = Downloads(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Adds to the app's log (Settings → App log), and to logcat. Never fails: a log mustn't break the app. */
    fun log(area: LogArea, message: String) {
        val text = AppLog.clean(message)
        Log.i("TunerFish", "$area: $text")
        runCatching { db.addLog(LogEntry(Instant.now(), area, text)) }
    }

    /** A crash goes in the log, and the next start offers to share it; then Android's own handling (closing). */
    private fun catchCrashes() {
        val android = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                log(LogArea.Crash, "${thread.name}: ${AppLog.crash(error)}")
                settings.crashToShare = true
            }
            android?.uncaughtException(thread, error)
        }
    }

    /** The log as shared: who and what it's from, then the entries. */
    fun logText(): String = AppLog.text(
        listOf(
            "${getString(R.string.app_name)} $version",
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
        db.log(), ZoneId.systemDefault(),
    )

    val isSignedIn: Boolean get() = settings.accessToken != null && settings.serverUrls.isNotEmpty()

    /** On Wi-Fi (or a cable) now. A phone's hotspot counts as Wi-Fi: using one is the user's call. */
    val onWifi: Boolean
        get() {
            val cm = getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

    /** Whether the server may be used at all now: always, unless "Wi-Fi only" is on and there's no Wi-Fi. */
    val serverAllowed: Boolean get() = !settings.wifiOnly || onWifi

    companion object {
        const val DOWNLOAD_CHANNEL = "downloads"
        private const val STREAM_CACHE_BYTES = 500L * 1024 * 1024
    }
}
