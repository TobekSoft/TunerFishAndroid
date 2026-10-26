package com.tobeksoft.tunerfish.downloads

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.tobeksoft.tunerfish.DownloadedAlbum
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.DownloadNames
import com.tobeksoft.tunerfish.core.DownloadState
import com.tobeksoft.tunerfish.core.LogArea
import com.tobeksoft.tunerfish.core.JellyfinException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

/**
 * Downloads every album waiting in the database, one at a time (see AppDatabase.nextDownload for
 * the order), and the tracks of each in order. Finished tracks are recorded as they land, so an
 * interrupted album carries on where it stopped; a half-written track is a ".part" file that is
 * started again.
 *
 * A network failure stops the run and WorkManager tries again later. A track the server refuses,
 * or a file that can't be written, marks the album Failed and moves on to the next; another app's
 * copy in its folder marks it Conflict.
 */
@OptIn(UnstableApi::class) // reads through Media3's cache classes
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.applicationContext as TunerFishApp

    override suspend fun doWork(): Result {
        if (!app.isSignedIn) return Result.success()
        if (app.jellyfin.addresses.size > 1 && !app.jellyfin.connect()) return Result.retry()
        try {
            while (true) {
                val next = app.db.nextDownload() ?: return Result.success()
                try {
                    download(next)
                } catch (e: JellyfinException) {
                    if (e.status == 401) return Result.success() // signed out; the app asks to sign in again
                    app.log(LogArea.Downloads, "${next.album.title}: failed, ${e.message}")
                    app.db.setDownloadState(next.album.id, DownloadState.Failed)
                } catch (e: StorageException) {
                    app.log(LogArea.Downloads, "${next.album.title}: failed, ${e.message}")
                    app.db.setDownloadState(next.album.id, DownloadState.Failed)
                } catch (e: ConflictException) {
                    app.log(LogArea.Downloads, "${next.album.title}: another app's copy is in its folder")
                    app.db.setDownloadState(next.album.id, DownloadState.Conflict) // the app asks Replace or not
                }
            }
        } catch (e: IOException) {
            app.log(LogArea.Downloads, "Network lost (${e.message}): trying again later")
            return Result.retry()
        } finally {
            app.downloads.refresh()
        }
    }

    private suspend fun download(a: DownloadedAlbum) {
        val id = a.album.id
        app.db.setDownloadState(id, DownloadState.Downloading)
        app.downloads.refresh()
        val dir = albumFolder(a)

        if (dir.findFile(DownloadNames.COVER) == null) {
            try {
                app.jellyfin.downloadImage(id, 1200) { _, body -> writeFile(dir, DownloadNames.COVER, body) }
            } catch (_: JellyfinException) {
                // No cover on the server: the album is fine without one.
            }
        }

        val tracks = app.db.downloadedTracks(id)
        val multiDisc = tracks.mapNotNull { it.track.disc }.distinct().size > 1
        var done = tracks.count { it.file != null }
        for (t in tracks.filter { it.file == null }) {
            // Removed while downloading: the removal may have run before the cover or folder existed.
            if (app.db.downloadedAlbum(id) == null) return app.downloads.discard(dir, null)
            showProgress(a, done, tracks.size)
            val ext = DownloadNames.extension(t.track.container, null)
            val name = DownloadNames.trackFile(t.track.disc, t.track.number, t.track.title, multiDisc, ext)
            val (file, bytes) = withContext(Dispatchers.IO) { readStream(t.track.id) { writeFile(dir, name, it) } }
            if (!app.downloads.recordTrack(id, t.track.id, file.uri.toString(), bytes)) {
                return app.downloads.discard(dir, file) // removed while this track was being written
            }
            done++
            app.downloads.refresh()
        }
        if (app.db.downloadedAlbum(id) == null) return app.downloads.discard(dir, null)
        app.db.setDownloadState(id, DownloadState.Done)
        app.log(LogArea.Downloads, "${a.album.title}: downloaded")
        app.downloads.cleanUp()
    }

    /**
     * The album's folder, made if need be. Another app's folder of the same name is cleared and used
     * only with "Replace other apps' copies without asking"; otherwise the album waits as a Conflict.
     */
    private fun albumFolder(a: DownloadedAlbum): DocumentFile = when (val place = app.downloads.place(a)) {
        is Place.Own -> place.dir
        is Place.New -> {
            val dir = place.artistDir.createDirectory(place.name) ?: throw StorageException("Couldn't make the folder ${place.name}")
            app.db.setDownloadFolder(a.album.id, dir.uri.toString())
            dir
        }
        is Place.Taken ->
            if (app.settings.replaceOtherCopies) app.downloads.adopt(a.album.id, place.dir)
            else throw ConflictException()
    }


    /**
     * Reads a track through the stream cache, so whatever the player has already fetched of it is
     * used rather than fetched again. An HTTP refusal becomes a JellyfinException, like other calls.
     */
    private fun <T> readStream(trackId: String, read: (InputStream) -> T): T {
        val spec = DataSpec.Builder().setUri(app.jellyfin.streamUrl(trackId)).setKey(trackId).build()
        try {
            return DataSourceInputStream(app.streamSource.createDataSource(), spec).use(read)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            throw JellyfinException(e.responseCode, "Download failed: HTTP ${e.responseCode}")
        }
    }

    /** Writes [body] to "[name].part", then renames it to [name]: a half-written file never has the real name. */
    private fun writeFile(dir: DocumentFile, name: String, body: InputStream): Pair<DocumentFile, Long> {
        val part = "$name.part"
        dir.findFile(name)?.delete()
        dir.findFile(part)?.delete()
        val file = dir.createFile("application/octet-stream", part) ?: throw StorageException("Couldn't make $part")
        val out = applicationContext.contentResolver.openOutputStream(file.uri) ?: throw StorageException("Couldn't open $part")
        val bytes = out.use { copy(body, it) }
        if (!file.renameTo(name)) throw StorageException("Couldn't rename $part")
        return file to bytes
    }

    /** Reading fails as an IOException (the network: try again later), writing as a StorageException (give up). */
    private fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            if (isStopped) throw CancellationException("Download stopped")
            val n = input.read(buffer)
            if (n < 0) return total
            try {
                output.write(buffer, 0, n)
            } catch (e: IOException) {
                throw StorageException("Couldn't write the file: ${e.message}")
            }
            total += n
        }
    }

    private suspend fun showProgress(a: DownloadedAlbum, done: Int, total: Int) {
        val notification = NotificationCompat.Builder(applicationContext, TunerFishApp.DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${a.album.title}")
            .setContentText("$done of $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
        // Android 12+ refuses this when the app is in the background; the download carries on regardless.
        runCatching { setForeground(info) }
    }

    private class ConflictException : Exception("Another app's copy is in the album's folder")

    private companion object {
        const val NOTIFICATION_ID = 2
    }
}
