package com.tobeksoft.tunerfish

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tobeksoft.tunerfish.core.Marks
import com.tobeksoft.tunerfish.core.AppLog
import com.tobeksoft.tunerfish.core.LogArea
import com.tobeksoft.tunerfish.core.LogEntry
import com.tobeksoft.tunerfish.core.Album
import com.tobeksoft.tunerfish.core.Artist
import com.tobeksoft.tunerfish.core.CleanupCandidate
import com.tobeksoft.tunerfish.core.DownloadState
import com.tobeksoft.tunerfish.core.SortOrder
import com.tobeksoft.tunerfish.core.Track
import java.time.Instant

/** One saved queue entry: enough to show it before the server answers, and to rebuild its cover URL. */
data class QueuedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val imageTag: String?,
)

/** An album with a download, holding what offline browsing needs. [root] is the download folder it went to. */
data class DownloadedAlbum(
    val album: Album,
    val artist: Artist,
    val root: String?,
    val folder: String?,
    val state: DownloadState,
    val kept: Boolean,
)

/** A track of a downloaded album; [file] is the file's URI, null until it's downloaded. */
data class DownloadedTrack(val track: Track, val file: String?)

/** One line of the Downloads screen. */
data class DownloadRow(
    val album: Album,
    val state: DownloadState,
    val kept: Boolean,
    val done: Int,
    val total: Int,
    val bytes: Long,
)

/** A play made offline, waiting to be sent to Jellyfin. [id] is its row. */
data class PendingPlay(val id: Long, val trackId: String, val at: Instant)

/** How far an album's download has got, for the screens. */
data class AlbumDownload(val state: DownloadState, val done: Int, val total: Int, val kept: Boolean)

/**
 * The app's SQLite database (tunerfish.db). The schema version is SQLite's PRAGMA user_version,
 * which SQLiteOpenHelper keeps: bump VERSION and add a step to onUpgrade for each change.
 * Never rename or reuse a table or column. One instance for the app (TunerFishApp.db).
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, "tunerfish.db", null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createQueue(db)
        createDownloads(db)
        createPendingPlays(db)
        createUserMarks(db)
        createAppLog(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDownloads(db)
        if (oldVersion < 3) createPendingPlays(db)
        if (oldVersion < 4) createUserMarks(db)
        if (oldVersion < 5) createAppLog(db)
    }

    private fun createQueue(db: SQLiteDatabase) = db.execSQL(
        """
        CREATE TABLE QueueItems (
            Position INTEGER PRIMARY KEY,
            TrackId  TEXT NOT NULL,
            Title    TEXT NOT NULL,
            Artist   TEXT NOT NULL,
            Album    TEXT NOT NULL,
            AlbumId  TEXT,
            ImageTag TEXT
        )
        """.trimIndent(),
    )

    // Version 2: downloads. State is a DownloadState name; times are ISO-8601 (UTC).
    private fun createDownloads(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE DownloadedAlbums (
                AlbumId       TEXT PRIMARY KEY,
                Title         TEXT NOT NULL,
                SortTitle     TEXT NOT NULL,
                Year          INTEGER,
                Artist        TEXT NOT NULL,
                ImageTag      TEXT,
                ArtistId      TEXT NOT NULL,
                ArtistName    TEXT NOT NULL,
                ArtistSortKey TEXT NOT NULL,
                Root          TEXT,
                Folder        TEXT,
                State         TEXT NOT NULL,
                Kept          INTEGER NOT NULL DEFAULT 0,
                Added         TEXT NOT NULL,
                LastPlayed    TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE DownloadedTracks (
                TrackId    TEXT PRIMARY KEY,
                AlbumId    TEXT NOT NULL REFERENCES DownloadedAlbums (AlbumId) ON DELETE CASCADE,
                Title      TEXT NOT NULL,
                Artist     TEXT NOT NULL,
                Disc       INTEGER,
                Number     INTEGER,
                DurationMs INTEGER,
                Container  TEXT,
                File       TEXT,
                Bytes      INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX DownloadedTracks_AlbumId ON DownloadedTracks (AlbumId)")
    }

    // Version 3: plays made while the server couldn't be told, sent when it can (PlayReporter).
    // One row per play, so a track played twice offline counts twice. PlayedAt is ISO-8601 (UTC).
    private fun createPendingPlays(db: SQLiteDatabase) = db.execSQL(
        """
        CREATE TABLE PendingPlays (
            TrackId  TEXT NOT NULL,
            PlayedAt TEXT NOT NULL
        )
        """.trimIndent(),
    )

    // Version 4: the user's marks (heart, thumbs) on artists and albums, as last fetched from the server,
    // so the phone can show and filter by them offline. A change made here is Pending until the server
    // has it; it wins over what the server says until then. Likes: 1 up (liked), 0 down (hidden), null.
    private fun createUserMarks(db: SQLiteDatabase) = db.execSQL(
        """
        CREATE TABLE UserMarks (
            ItemId    TEXT PRIMARY KEY,
            Favourite INTEGER NOT NULL DEFAULT 0,
            Likes     INTEGER,
            Pending   INTEGER NOT NULL DEFAULT 0,
            FetchedAt TEXT,
            ChangedAt TEXT
        )
        """.trimIndent(),
    )

    // Version 5: the app's own log (core AppLog), newest AppLog.KEEP entries only. At is ISO-8601 (UTC),
    // Area a LogArea name. Read in order of rowid, which only goes up.
    private fun createAppLog(db: SQLiteDatabase) = db.execSQL(
        """
        CREATE TABLE AppLog (
            At      TEXT NOT NULL,
            Area    TEXT NOT NULL,
            Message TEXT NOT NULL
        )
        """.trimIndent(),
    )

    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    // ---------------------------------------------------------------- play queue

    /** The play queue as last saved, in order. */
    fun loadQueue(): List<QueuedTrack> =
        readableDatabase.rawQuery(
            "SELECT TrackId, Title, Artist, Album, AlbumId, ImageTag FROM QueueItems ORDER BY Position", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    QueuedTrack(
                        trackId = c.getString(0),
                        title = c.getString(1),
                        artist = c.getString(2),
                        album = c.getString(3),
                        albumId = c.stringOrNull(4),
                        imageTag = c.stringOrNull(5),
                    ),
                )
            }
        }

    /** Replaces the saved queue with [tracks], all or nothing. */
    fun saveQueue(tracks: List<QueuedTrack>) = transaction { db ->
        db.delete("QueueItems", null, null)
        val insert = db.compileStatement(
            "INSERT INTO QueueItems (Position, TrackId, Title, Artist, Album, AlbumId, ImageTag) VALUES (?, ?, ?, ?, ?, ?, ?)",
        )
        tracks.forEachIndexed { i, t ->
            insert.clearBindings()
            insert.bindLong(1, i.toLong())
            insert.bindString(2, t.trackId)
            insert.bindString(3, t.title)
            insert.bindString(4, t.artist)
            insert.bindString(5, t.album)
            t.albumId?.let { insert.bindString(6, it) }
            t.imageTag?.let { insert.bindString(7, it) }
            insert.executeInsert()
        }
    }

    // ---------------------------------------------------------------- downloads

    /**
     * Records an album to download (or, if it's already there, that it was asked for again). A new
     * album goes to [root]; one already recorded stays where it is. [keep] marks it as never removed
     * automatically. Tracks it doesn't have yet are added, and the album is queued again if any
     * track still needs downloading.
     */
    fun addDownload(album: Album, artist: Artist, tracks: List<Track>, root: String?, keep: Boolean) = transaction { db ->
        val now = Instant.now().toString()
        db.insertWithOnConflict("DownloadedAlbums", null, ContentValues().apply {
            put("AlbumId", album.id)
            put("Title", album.title)
            put("SortTitle", album.sortTitle)
            put("Year", album.year)
            put("Artist", album.artist)
            put("ImageTag", album.imageTag)
            put("ArtistId", artist.id)
            put("ArtistName", artist.name)
            put("ArtistSortKey", artist.sortKey)
            put("Root", root)
            put("State", DownloadState.Queued.name)
            put("Kept", if (keep) 1 else 0)
            put("Added", now)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL(
            "UPDATE DownloadedAlbums SET LastPlayed = ?, Kept = MAX(Kept, ?) WHERE AlbumId = ?",
            arrayOf<Any>(now, if (keep) 1 else 0, album.id),
        )
        for (t in tracks) {
            db.insertWithOnConflict("DownloadedTracks", null, ContentValues().apply {
                put("TrackId", t.id)
                put("AlbumId", album.id)
                put("Title", t.title)
                put("Artist", t.artist)
                put("Disc", t.disc)
                put("Number", t.number)
                put("DurationMs", t.durationMs)
                put("Container", t.container)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.execSQL(
            """
            UPDATE DownloadedAlbums SET State = 'Queued'
            WHERE AlbumId = ? AND State IN ('Done', 'Failed')
              AND EXISTS (SELECT 1 FROM DownloadedTracks WHERE AlbumId = ? AND File IS NULL)
            """.trimIndent(),
            arrayOf(album.id, album.id),
        )
    }

    /**
     * The next album to download: albums in the play queue first, in queue order, then the rest in
     * the order they were asked for.
     */
    fun nextDownload(): DownloadedAlbum? =
        readableDatabase.rawQuery(
            """
            SELECT $ALBUM_COLUMNS FROM DownloadedAlbums a
            LEFT JOIN (SELECT t.AlbumId AS Id, MIN(q.Position) AS P
                       FROM QueueItems q JOIN DownloadedTracks t ON t.TrackId = q.TrackId
                       GROUP BY t.AlbumId) q ON q.Id = a.AlbumId
            WHERE a.State IN ('Queued', 'Downloading')
            ORDER BY q.P IS NULL, q.P, a.Added
            LIMIT 1
            """.trimIndent(), null,
        ).use { c -> if (c.moveToFirst()) c.toDownloadedAlbum() else null }

    /** The album's tracks in disc and track order. */
    fun downloadedTracks(albumId: String): List<DownloadedTrack> =
        readableDatabase.rawQuery(
            """
            SELECT t.TrackId, t.Title, t.Artist, t.Disc, t.Number, t.DurationMs, t.Container, t.File, a.Title
            FROM DownloadedTracks t JOIN DownloadedAlbums a ON a.AlbumId = t.AlbumId
            WHERE t.AlbumId = ?
            ORDER BY t.Disc IS NULL, t.Disc, t.Number IS NULL, t.Number, t.Title
            """.trimIndent(), arrayOf(albumId),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    DownloadedTrack(
                        Track(
                            id = c.getString(0),
                            title = c.getString(1),
                            artist = c.getString(2),
                            albumId = albumId,
                            album = c.getString(8),
                            disc = c.intOrNull(3),
                            number = c.intOrNull(4),
                            durationMs = c.longOrNull(5),
                            container = c.stringOrNull(6),
                        ),
                        file = c.stringOrNull(7),
                    ),
                )
            }
        }

    /** Every track on the phone, with only what searching needs (id, title, album). */
    fun offlineTracks(): List<Track> =
        readableDatabase.rawQuery("SELECT TrackId, Title, AlbumId FROM DownloadedTracks WHERE File IS NOT NULL", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Track(c.getString(0), c.getString(1), "", c.getString(2), "", null, null, null))
            }
        }

    fun setDownloadState(albumId: String, state: DownloadState) =
        writableDatabase.execSQL("UPDATE DownloadedAlbums SET State = ? WHERE AlbumId = ?", arrayOf(state.name, albumId))

    fun setDownloadFolder(albumId: String, folder: String) =
        writableDatabase.execSQL("UPDATE DownloadedAlbums SET Folder = ? WHERE AlbumId = ?", arrayOf(folder, albumId))

    /** Whether any album's download uses [folder]. */
    fun folderUsed(folder: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM DownloadedAlbums WHERE Folder = ? LIMIT 1", arrayOf(folder))
            .use { it.moveToFirst() }

    fun setTrackFile(trackId: String, file: String, bytes: Long) =
        writableDatabase.execSQL("UPDATE DownloadedTracks SET File = ?, Bytes = ? WHERE TrackId = ?", arrayOf<Any>(file, bytes, trackId))

    /** The downloaded file of a track (a file:// or content:// URI), or null if it hasn't landed. */
    fun trackFile(trackId: String): String? =
        readableDatabase.rawQuery("SELECT File FROM DownloadedTracks WHERE TrackId = ?", arrayOf(trackId))
            .use { c -> if (c.moveToFirst()) c.stringOrNull(0) else null }

    // Browsing offline: only albums with at least one track on the phone.

    fun offlineArtists(): List<Artist> =
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT a.ArtistId, a.ArtistName, a.ArtistSortKey FROM DownloadedAlbums a
            WHERE EXISTS (SELECT 1 FROM DownloadedTracks t WHERE t.AlbumId = a.AlbumId AND t.File IS NOT NULL)
            """.trimIndent(), null,
        ).use { c ->
            buildList { while (c.moveToNext()) add(Artist(c.getString(0), c.getString(1), c.getString(2), SortOrder.groupOf(c.getString(2)))) }
                .distinctBy { it.id }
        }

    fun offlineAlbums(artistId: String): List<Album> = offlineAlbumsWhere("a.ArtistId = ?", arrayOf(artistId))

    /** Every album on the phone (the views by marks). */
    fun offlineAlbums(): List<Album> = offlineAlbumsWhere("1", null)

    /** The albums on the phone played (or queued) most recently first: Recents, offline. */
    fun offlineRecentlyPlayed(limit: Int): List<Album> =
        offlineAlbumsWhere("a.LastPlayed IS NOT NULL", null, "a.LastPlayed DESC LIMIT $limit")

    private fun offlineAlbumsWhere(where: String, args: Array<String>?, orderBy: String? = null): List<Album> =
        readableDatabase.rawQuery(
            """
            SELECT $ALBUM_COLUMNS FROM DownloadedAlbums a
            WHERE $where
              AND EXISTS (SELECT 1 FROM DownloadedTracks t WHERE t.AlbumId = a.AlbumId AND t.File IS NOT NULL)
            """.trimIndent() + (orderBy?.let { "\nORDER BY $it" } ?: ""), args,
        ).use { c -> buildList { while (c.moveToNext()) add(c.toDownloadedAlbum().album) } }

    /** Each downloaded album's folder, by album id (for covers). */
    fun downloadFolders(): Map<String, String> =
        readableDatabase.rawQuery("SELECT AlbumId, Folder FROM DownloadedAlbums WHERE Folder IS NOT NULL", null)
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    /** The album's tracks that are on the phone. */
    fun downloadedTrackIds(albumId: String): Set<String> =
        readableDatabase.rawQuery("SELECT TrackId FROM DownloadedTracks WHERE AlbumId = ? AND File IS NOT NULL", arrayOf(albumId))
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    /** The track's file has gone (deleted outside the app): download it again. */
    fun forgetTrackFile(trackId: String) = transaction { db ->
        db.execSQL("UPDATE DownloadedTracks SET File = NULL, Bytes = NULL WHERE TrackId = ?", arrayOf(trackId))
        db.execSQL(
            "UPDATE DownloadedAlbums SET State = 'Queued' WHERE State IN ('Done', 'Failed') " +
                "AND AlbumId = (SELECT AlbumId FROM DownloadedTracks WHERE TrackId = ?)",
            arrayOf(trackId),
        )
    }

    /** Every album with a download, by id. */
    fun downloadProgress(): Map<String, AlbumDownload> =
        readableDatabase.rawQuery(
            """
            SELECT a.AlbumId, a.State, a.Kept, COUNT(t.TrackId), COUNT(t.File)
            FROM DownloadedAlbums a LEFT JOIN DownloadedTracks t ON t.AlbumId = a.AlbumId
            GROUP BY a.AlbumId
            """.trimIndent(), null,
        ).use { c ->
            buildMap {
                while (c.moveToNext()) put(
                    c.getString(0),
                    AlbumDownload(DownloadState.parse(c.getString(1)), done = c.getInt(4), total = c.getInt(3), kept = c.getInt(2) != 0),
                )
            }
        }

    fun downloadedAlbum(albumId: String): DownloadedAlbum? =
        readableDatabase.rawQuery("SELECT $ALBUM_COLUMNS FROM DownloadedAlbums a WHERE a.AlbumId = ?", arrayOf(albumId))
            .use { c -> if (c.moveToFirst()) c.toDownloadedAlbum() else null }

    /** Every album with a download, with its size so far: unfinished first, then by artist and title. */
    fun downloadList(): List<DownloadRow> =
        readableDatabase.rawQuery(
            """
            SELECT $ALBUM_COLUMNS, COUNT(t.TrackId), COUNT(t.File), IFNULL(SUM(t.Bytes), 0)
            FROM DownloadedAlbums a LEFT JOIN DownloadedTracks t ON t.AlbumId = a.AlbumId
            GROUP BY a.AlbumId
            """.trimIndent(), null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val a = c.toDownloadedAlbum()
                    add(a.artist.sortKey to DownloadRow(a.album, a.state, a.kept, done = c.getInt(14), total = c.getInt(13), bytes = c.getLong(15)))
                }
            }
        }.sortedWith(
            compareBy<Pair<String, DownloadRow>> { it.second.state == DownloadState.Done }
                .thenBy(SortOrder.comparator) { it.first }
                .thenBy(SortOrder.comparator) { it.second.album.sortTitle },
        ).map { it.second }

    /** Finished downloads as the clean-up sees them; "in the queue" means any of its tracks is. */
    fun cleanupCandidates(): List<CleanupCandidate> =
        readableDatabase.rawQuery(
            """
            SELECT a.AlbumId, IFNULL(SUM(t.Bytes), 0), a.Kept,
                   EXISTS (SELECT 1 FROM QueueItems q JOIN DownloadedTracks u ON u.TrackId = q.TrackId WHERE u.AlbumId = a.AlbumId),
                   a.LastPlayed
            FROM DownloadedAlbums a LEFT JOIN DownloadedTracks t ON t.AlbumId = a.AlbumId
            WHERE a.State = 'Done'
            GROUP BY a.AlbumId
            """.trimIndent(), null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(CleanupCandidate(c.getString(0), c.getLong(1), c.getInt(2) != 0, c.getInt(3) != 0, c.stringOrNull(4)))
            }
        }

    fun setKept(albumId: String, kept: Boolean) =
        writableDatabase.execSQL("UPDATE DownloadedAlbums SET Kept = ? WHERE AlbumId = ?", arrayOf<Any>(if (kept) 1 else 0, albumId))

    /** A track of the album started playing: it's recent, so the clean-up leaves it till later. */
    fun touchAlbum(albumId: String) =
        writableDatabase.execSQL("UPDATE DownloadedAlbums SET LastPlayed = ? WHERE AlbumId = ?", arrayOf(Instant.now().toString(), albumId))

    /** Forgets the album's download (its tracks go with it); the caller deletes the files. */
    fun removeDownload(albumId: String) =
        writableDatabase.delete("DownloadedAlbums", "AlbumId = ?", arrayOf(albumId))

    /** Whether any download lives in the folder [root] (null: the app's own storage). */
    fun hasDownloadsIn(root: String?): Boolean =
        readableDatabase.rawQuery(
            if (root == null) "SELECT 1 FROM DownloadedAlbums WHERE Root IS NULL LIMIT 1"
            else "SELECT 1 FROM DownloadedAlbums WHERE Root = ? LIMIT 1",
            if (root == null) null else arrayOf(root),
        ).use { it.moveToFirst() }

    // ---------------------------------------------------------------- plays made offline

    fun addPendingPlay(trackId: String, at: Instant) =
        writableDatabase.execSQL("INSERT INTO PendingPlays (TrackId, PlayedAt) VALUES (?, ?)", arrayOf(trackId, at.toString()))

    /** Plays waiting to be sent, oldest first. */
    fun pendingPlays(): List<PendingPlay> =
        readableDatabase.rawQuery("SELECT rowid, TrackId, PlayedAt FROM PendingPlays ORDER BY PlayedAt", null).use { c ->
            buildList { while (c.moveToNext()) add(PendingPlay(c.getLong(0), c.getString(1), Instant.parse(c.getString(2)))) }
        }

    fun removePendingPlay(id: Long) = writableDatabase.delete("PendingPlays", "rowid = ?", arrayOf(id.toString()))

    // ---------------------------------------------------------------- app log

    /** Adds an entry (already cleaned, see AppLog.clean) and drops the oldest beyond AppLog.KEEP. */
    fun addLog(e: LogEntry) = transaction { db ->
        db.execSQL("INSERT INTO AppLog (At, Area, Message) VALUES (?, ?, ?)", arrayOf(e.at.toString(), e.area.name, e.message))
        db.execSQL("DELETE FROM AppLog WHERE rowid <= (SELECT MAX(rowid) FROM AppLog) - ${AppLog.KEEP}")
    }

    /** The log, oldest first. */
    fun log(): List<LogEntry> =
        readableDatabase.rawQuery("SELECT At, Area, Message FROM AppLog ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add(LogEntry(Instant.parse(c.getString(0)), LogArea.parse(c.getString(1)), c.getString(2))) }
        }

    fun clearLog() = writableDatabase.execSQL("DELETE FROM AppLog")

    // ---------------------------------------------------------------- marks

    /** Records what the server says about these items, except where a change made here is still pending. */
    fun saveServerMarks(marks: Map<String, Marks>) = transaction { db ->
        val now = Instant.now().toString()
        val insert = db.compileStatement("INSERT OR IGNORE INTO UserMarks (ItemId) VALUES (?)")
        val update = db.compileStatement(
            "UPDATE UserMarks SET Favourite = ?, Likes = ?, FetchedAt = ? WHERE ItemId = ? AND Pending = 0",
        )
        for ((id, m) in marks) {
            insert.bindString(1, id)
            insert.executeInsert()
            update.clearBindings()
            update.bindLong(1, if (m.favourite) 1 else 0)
            m.likes?.let { update.bindLong(2, if (it) 1 else 0) }
            update.bindString(3, now)
            update.bindString(4, id)
            update.executeUpdateDelete()
        }
    }

    /** The marks recorded for these items (missing ones have none). */
    fun marks(ids: Collection<String>): Map<String, Marks> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<String, Marks>()
        // In chunks: SQLite allows only so many parameters in one statement.
        for (chunk in ids.chunked(500)) {
            readableDatabase.rawQuery(
                "SELECT ItemId, Favourite, Likes FROM UserMarks WHERE ItemId IN (${chunk.joinToString(",") { "?" }})",
                chunk.toTypedArray(),
            ).use { c -> while (c.moveToNext()) result[c.getString(0)] = c.toMarks(1) }
        }
        return result
    }

    /** A change made on the phone: shown at once, sent when the server can be reached. */
    fun changeMarks(id: String, marks: Marks) = writableDatabase.execSQL(
        """
        INSERT OR REPLACE INTO UserMarks (ItemId, Favourite, Likes, Pending, FetchedAt, ChangedAt)
        VALUES (?, ?, ?, 1, (SELECT FetchedAt FROM UserMarks WHERE ItemId = ?), ?)
        """.trimIndent(),
        arrayOf<Any?>(id, if (marks.favourite) 1 else 0, marks.likes?.let { if (it) 1 else 0 }, id, Instant.now().toString()),
    )

    /** Changes waiting to be sent. */
    fun pendingMarks(): Map<String, Marks> =
        readableDatabase.rawQuery("SELECT ItemId, Favourite, Likes FROM UserMarks WHERE Pending = 1", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.toMarks(1)) }
        }

    /** The server has [marks] for [id]; no longer pending, unless it was changed again meanwhile. */
    fun marksSent(id: String, marks: Marks) = writableDatabase.execSQL(
        "UPDATE UserMarks SET Pending = 0, FetchedAt = ? WHERE ItemId = ? AND Favourite = ? AND Likes IS ?",
        arrayOf<Any?>(Instant.now().toString(), id, if (marks.favourite) 1 else 0, marks.likes?.let { if (it) 1 else 0 }),
    )

    // ---------------------------------------------------------------- helpers

    private fun Cursor.toMarks(first: Int) =
        Marks(favourite = getInt(first) != 0, likes = if (isNull(first + 1)) null else getInt(first + 1) != 0)


    private fun Cursor.toDownloadedAlbum(): DownloadedAlbum {
        val sortKey = getString(8)
        return DownloadedAlbum(
            album = Album(
                id = getString(0), title = getString(1), sortTitle = getString(2), year = intOrNull(3),
                artist = getString(4), imageTag = stringOrNull(5), artistId = getString(6),
            ),
            artist = Artist(id = getString(6), name = getString(7), sortKey = sortKey, group = SortOrder.groupOf(sortKey)),
            root = stringOrNull(9),
            folder = stringOrNull(10),
            state = DownloadState.parse(getString(11)),
            kept = getInt(12) != 0,
        )
    }

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            return block(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val VERSION = 5
        const val ALBUM_COLUMNS =
            "a.AlbumId, a.Title, a.SortTitle, a.Year, a.Artist, a.ImageTag, a.ArtistId, a.ArtistName, a.ArtistSortKey, " +
                "a.Root, a.Folder, a.State, a.Kept"

        fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
        fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
        fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)
    }
}
