package com.tobeksoft.tunerfish.core

/** An album artist as the app shows it. Filed by [sortKey]; [name] is shown unchanged. */
data class Artist(val id: String, val name: String, val sortKey: String, val group: String, val marks: Marks = Marks.None) {
    companion object {
        fun from(item: BaseItem): Artist {
            val key = SortOrder.keyFor(item)
            return Artist(item.id, item.name, key, SortOrder.groupOf(key), Marks.from(item))
        }
    }
}

data class Album(
    val id: String,
    val title: String,
    val sortTitle: String,
    val year: Int?,
    val artist: String,
    val imageTag: String?,
    val marks: Marks = Marks.None,
    /** Its (first) album artist, for an album shown outside its artist (the views). */
    val artistId: String? = null,
) {
    companion object {
        fun from(item: BaseItem, artist: String) = Album(
            id = item.id,
            title = item.name,
            sortTitle = SortOrder.keyFor(item),
            year = item.productionYear,
            artist = item.albumArtist ?: artist,
            imageTag = item.primaryImageTag,
            marks = Marks.from(item),
            artistId = item.albumArtists?.firstOrNull()?.id,
        )
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String?,
    val album: String,
    val disc: Int?,
    val number: Int?,
    val durationMs: Long?,
    val container: String? = null,
    val bytes: Long? = null,
) {
    companion object {
        fun from(item: BaseItem) = Track(
            id = item.id,
            title = item.name,
            artist = item.artists?.joinToString(", ")?.ifBlank { null } ?: item.albumArtist ?: "",
            albumId = item.albumId,
            album = item.album ?: "",
            disc = item.parentIndexNumber,
            number = item.indexNumber,
            durationMs = item.runTimeTicks?.let { it / 10_000 },
            container = item.container,
            bytes = item.mediaSources?.firstOrNull()?.size,
        )
    }
}

/** Stored as its name (a word, never a number). Unknown names fall back to [YearAscending]. */
enum class AlbumSortMode { Title, YearAscending, YearDescending;
    companion object {
        fun parse(value: String?): AlbumSortMode = entries.firstOrNull { it.name == value } ?: YearAscending
    }
}

/** Filing and filtering rules, the same as the desktop app's. */
object Library {
    /** Artists in filing order: by sort key, then display name for ties. */
    fun sortArtists(items: List<BaseItem>): List<Artist> = fileArtists(items.map(Artist::from))

    /** The same order for artists already made, e.g. read back from the downloads for browsing offline. */
    fun fileArtists(artists: List<Artist>): List<Artist> =
        artists.sortedWith(compareBy(SortOrder.comparator, Artist::sortKey).thenBy(SortOrder.comparator, Artist::name))

    /** Artists whose display name contains the filter text, ignoring case. A blank filter keeps them all. */
    /** Hidden artists and albums are left out unless [showHidden]. */
    fun visibleArtists(artists: List<Artist>, showHidden: Boolean) = if (showHidden) artists else artists.filter { !it.marks.hidden }
    fun visibleAlbums(albums: List<Album>, showHidden: Boolean) = if (showHidden) albums else albums.filter { !it.marks.hidden }

    fun filterArtists(artists: List<Artist>, filter: String): List<Artist> {
        val f = filter.trim()
        return if (f.isEmpty()) artists else artists.filter { it.name.contains(f, ignoreCase = true) }
    }

    /** The jump-bar groups that have at least one artist in the list. */
    fun groupsPresent(artists: List<Artist>): Set<String> = artists.mapTo(HashSet()) { it.group }

    /** Index of the first artist filed under the group, or -1. */
    fun firstIndexInGroup(artists: List<Artist>, group: String): Int = artists.indexOfFirst { it.group == group }

    fun sortAlbums(albums: List<Album>, mode: AlbumSortMode): List<Album> {
        val byTitle = compareBy(SortOrder.comparator, Album::sortTitle).thenBy(SortOrder.comparator, Album::title)
        return when (mode) {
            AlbumSortMode.Title -> albums.sortedWith(byTitle)
            AlbumSortMode.YearAscending -> albums.sortedWith(compareBy<Album> { it.year ?: Int.MAX_VALUE }.then(byTitle))
            AlbumSortMode.YearDescending -> albums.sortedWith(compareByDescending<Album> { it.year ?: Int.MIN_VALUE }.then(byTitle))
        }
    }

    /** "1 album", "12 albums", or "" for none. */
    fun albumCountText(count: Int): String = when (count) {
        0 -> ""
        1 -> "1 album"
        else -> "$count albums"
    }

    /** m:ss, or h:mm:ss for an hour or more. */
    fun formatDuration(ms: Long?): String {
        if (ms == null || ms < 0) return ""
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
