package com.tobeksoft.tunerfish.downloads

import androidx.core.net.toUri
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tobeksoft.tunerfish.AlbumDownload
import com.tobeksoft.tunerfish.DownloadedAlbum
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.Album
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.Cleanup
import com.tobeksoft.tunerfish.core.DownloadNames
import com.tobeksoft.tunerfish.core.DownloadState
import com.tobeksoft.tunerfish.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** A file or folder couldn't be made or written: the album is marked Failed rather than tried again. */
class StorageException(message: String) : Exception(message)

/** Where an album's files go. */
sealed interface Place {
    /** Its own folder, already made. */
    data class Own(val dir: DocumentFile) : Place
    /** Not made yet: [name] is free in [artistDir]. */
    data class New(val artistDir: DocumentFile, val name: String) : Place
    /** Another app's folder has its name: replace it or don't download. */
    data class Taken(val dir: DocumentFile) : Place
}

/**
 * Album downloads. Queueing an album downloads it (PlayerViewModel asks for that); "Download"
 * does too without queueing, and keeps it. The database records each album and file;
 * [DownloadWorker] does the work in the background, one album at a time.
 *
 * Past the size limit (Settings.downloadLimit), finished albums that aren't kept or queued are
 * removed, the longest unplayed first (core's Cleanup decides which).
 */
class Downloads(private val app: TunerFishApp) {
    private val _progress = MutableStateFlow(app.db.downloadProgress())
    @Volatile private var folders = app.db.downloadFolders()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Removing an album and the worker recording a finished track take turns: otherwise a track
     * finishing mid-removal is in neither the removed list nor the database, and stays on the phone.
     */
    private val removing = Any()
    /** Every album with a download, by id. Updated as tracks arrive. */
    val progress: StateFlow<Map<String, AlbumDownload>> = _progress

    /** Downloads the album into the current download folder; [keep] means never removed automatically. */
    fun request(album: Album, artist: Artist, tracks: List<Track>, keep: Boolean) {
        if (tracks.isEmpty()) return
        app.db.addDownload(album, artist, tracks, app.settings.downloadFolder, keep)
        // "Download" on an album once told "Don't download" (or not answered yet) asks again.
        val state = app.db.downloadProgress()[album.id]?.state
        if (keep && (state == DownloadState.Skipped || state == DownloadState.Conflict)) {
            dismissed.remove(album.id)
            app.db.setDownloadState(album.id, DownloadState.Conflict)
        }
        refresh()
        start()
    }

    /** Albums whose Replace question was dismissed this run; it's not asked again until "Download". */
    private val dismissed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun shouldAsk(albumId: String) = albumId !in dismissed

    fun dismiss(albumId: String) {
        dismissed += albumId
    }

    /**
     * The downloaded file to play for a track, or null to stream it. A file that has gone (deleted
     * outside the app) is forgotten and its album downloaded again. Called as each track opens.
     */
    fun localFile(trackId: String): Uri? {
        val uri = app.db.trackFile(trackId)?.let(Uri::parse) ?: return null
        val exists = if (uri.scheme == "file") File(uri.path ?: "").isFile
        else DocumentFile.fromSingleUri(app, uri)?.exists() == true
        if (exists) return uri
        app.db.forgetTrackFile(trackId)
        refresh()
        start()
        return null
    }

    fun setKept(albumId: String, kept: Boolean) {
        app.db.setKept(albumId, kept)
        refresh()
        if (!kept) cleanUpSoon()
    }

    /** Tries a failed album again. */
    fun retry(albumId: String) {
        app.db.setDownloadState(albumId, DownloadState.Queued)
        refresh()
        start()
    }

    /** Removes the album from the phone: forgotten at once, its files deleted in the background. */
    fun remove(albumId: String) {
        val (album, files) = synchronized(removing) {
            val album = app.db.downloadedAlbum(albumId) ?: return
            val files = app.db.downloadedTracks(albumId).mapNotNull { it.file }
            app.db.removeDownload(albumId)
            album to files
        }
        refresh()
        io.launch { deleteFiles(album.folder, files) }
    }

    /**
     * Where the album goes: <root>/<Album Artist>/<Album>. A folder of that name belonging to another
     * TunerFish album (a second "Live") means "<Album> (2)"; one that isn't TunerFish's is [Place.Taken].
     */
    fun place(a: DownloadedAlbum): Place {
        a.folder?.let(::document)?.takeIf { it.isDirectory }?.let { return Place.Own(it) }
        val root = (if (a.root == null) app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let(DocumentFile::fromFile)
            else DocumentFile.fromTreeUri(app, a.root.toUri()))
            ?.takeIf { it.canWrite() } ?: throw StorageException("The download folder can't be written to")
        val artistName = DownloadNames.safe(a.album.artist.ifBlank { a.artist.name })
        val artistDir = root.findFile(artistName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(artistName) ?: throw StorageException("Couldn't make the folder $artistName")
        val title = DownloadNames.safe(a.album.title)
        for (n in generateSequence(1) { it + 1 }) {
            val name = if (n == 1) title else "$title ($n)"
            val existing = artistDir.findFile(name) ?: return Place.New(artistDir, name)
            if (existing.isDirectory && !app.db.folderUsed(existing.uri.toString())) return Place.Taken(existing)
        }
        error("unreachable")
    }

    /**
     * Takes over another app's copy of the album: deletes its music files and images (the server
     * still has the music), keeps anything else, and makes the folder this album's.
     */
    fun adopt(albumId: String, dir: DocumentFile): DocumentFile {
        for (f in dir.listFiles()) {
            if (f.isFile && DownloadNames.isReplaceable(f.name ?: "")) f.delete()
        }
        app.db.setDownloadFolder(albumId, dir.uri.toString())
        return dir
    }

    /** "Replace": clear another app's copy and download the album into its folder. */
    fun replace(albumId: String) {
        io.launch {
            val a = app.db.downloadedAlbum(albumId) ?: return@launch
            try {
                (place(a) as? Place.Taken)?.let { adopt(albumId, it.dir) }
                app.db.setDownloadState(albumId, DownloadState.Queued)
            } catch (e: StorageException) {
                app.db.setDownloadState(albumId, DownloadState.Failed)
            }
            refresh()
            start()
        }
    }

    /** "Don't download": the album streams, and isn't asked about again. */
    fun skip(albumId: String) {
        app.db.setDownloadState(albumId, DownloadState.Skipped)
        refresh()
    }

    /** [cleanUp] off the main thread, e.g. after the limit is lowered. */
    fun cleanUpSoon() {
        io.launch { cleanUp() }
    }

    /** Brings downloads back under the size limit. Run after each album finishes and when the limit changes. */
    fun cleanUp() {
        for (id in Cleanup.choose(app.db.cleanupCandidates(), app.settings.downloadLimit)) {
            val (album, files) = synchronized(removing) {
                val album = app.db.downloadedAlbum(id) ?: return@synchronized null
                val files = app.db.downloadedTracks(id).mapNotNull { it.file }
                app.db.removeDownload(id)
                album to files
            } ?: continue
            deleteFiles(album.folder, files)
        }
        refresh()
    }

    /**
     * Records a track the worker has written. False if its album was removed meanwhile: the worker
     * then deletes the file with [discard], since the removal didn't know about it.
     */
    fun recordTrack(albumId: String, trackId: String, file: String, bytes: Long): Boolean = synchronized(removing) {
        if (app.db.downloadedAlbum(albumId) == null) return false
        app.db.setTrackFile(trackId, file, bytes)
        true
    }

    /** Tidies up after an album removed while downloading: [file] (if any), its cover, and empty folders. */
    fun discard(folder: DocumentFile, file: DocumentFile?) =
        deleteFiles(folder.uri.toString(), listOfNotNull(file?.uri?.toString()))

    /**
     * Deletes the album's files, then its cover and any half-written ".part" file, then the album
     * folder and the artist folder if nothing else is left in them. Files the app didn't write stay.
     */
    private fun deleteFiles(folder: String?, files: List<String>) {
        for (f in files) runCatching { document(f)?.delete() }
        val dir = folder?.let(::document)?.takeIf { it.isDirectory } ?: return
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (name == DownloadNames.COVER || name.endsWith(".part")) f.delete()
        }
        if (dir.listFiles().isNotEmpty()) return
        val artistDir = parentOf(folder)
        dir.delete()
        artistDir?.takeIf { it.isDirectory && it.listFiles().isEmpty() }?.delete()
    }

    /** A file or folder from its stored URI: file:// for the app's own storage, else a document in a picked tree. */
    fun document(uri: String): DocumentFile? {
        val u = uri.toUri()
        return if (u.scheme == "file") DocumentFile.fromFile(File(u.path ?: return null))
        else DocumentFile.fromTreeUri(app, u)
    }

    /** The folder holding [uri]'s folder. A tree's document ids are paths, so the parent is the id up to the last "/". */
    private fun parentOf(uri: String): DocumentFile? {
        val u = uri.toUri()
        if (u.scheme == "file") return File(u.path ?: return null).parentFile?.let(DocumentFile::fromFile)
        val id = DocumentsContract.getDocumentId(u)
        if ('/' !in id) return null
        return DocumentFile.fromTreeUri(app, DocumentsContract.buildDocumentUriUsingTree(u, id.substringBeforeLast('/')))
    }

    /** The downloaded cover of an album (its folder's cover.jpg), or null if the album isn't downloaded. */
    fun coverUri(albumId: String): Uri? {
        val folder = (folders[albumId] ?: return null).toUri()
        return if (folder.scheme == "file") Uri.fromFile(File(folder.path ?: return null, DownloadNames.COVER))
        // Folders from the picker are documents in a tree; their children's ids are the path below them.
        else DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getDocumentId(folder) + "/" + DownloadNames.COVER)
    }

    /**
     * Runs the worker after any that's already running, so nothing asked for is missed. With "Wi-Fi
     * only" it waits for an unmetered network. [restart] replaces the waiting worker, for when that
     * setting changes.
     */
    fun start(restart: Boolean = false) {
        val network = if (app.settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val work = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, policy, work)
    }

    fun refresh() {
        _progress.value = app.db.downloadProgress()
        folders = app.db.downloadFolders()
    }

    private companion object {
        const val WORK_NAME = "downloads"
    }
}
