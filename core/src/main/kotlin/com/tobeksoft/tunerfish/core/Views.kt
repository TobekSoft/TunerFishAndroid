package com.tobeksoft.tunerfish.core

import java.time.Instant

/**
 * What the artists screen shows: the whole library, or a view of the user's own data. The views by
 * marks are filters: the artist list, shortened to the artists that are marked or have a marked
 * album ([filter]). The played views are rankings: albums only, from Jellyfin's play history.
 */
enum class LibraryView(val label: String) {
    Library("Library"),
    Favourites("Favourites"),
    Liked("Liked"),
    RecentlyPlayed("Recents"),
    MostPlayed("Most"),
    Hidden("Hidden");

    val byMarks: Boolean get() = this == Favourites || this == Liked || this == Hidden

    /** Whether something with these marks belongs in this view (the played views take whatever they're given). */
    fun includes(marks: Marks): Boolean = when (this) {
        Favourites -> marks.favourite
        Liked -> marks.liked
        Hidden -> marks.hidden
        else -> true
    }

    /** Hidden things show only in Hidden, or with "Show hidden". */
    private fun shows(marks: Marks, showHidden: Boolean) = this == Hidden || showHidden || !marks.hidden

    /** A played view's albums, in the order given. */
    fun albums(all: List<Album>, showHidden: Boolean): List<Album> =
        all.filter { includes(it.marks) && shows(it.marks, showHidden) }

    /**
     * A view by marks out of the library's [artists] and the [albums] that might be in it: an artist
     * marked itself is listed with all its albums (a favourite artist = all their albums, even ones
     * added later); an artist with marked albums is listed with only those. A hidden artist hides all
     * its albums. An album whose artist isn't in [artists] is filed under its own artist name.
     */
    fun filter(artists: List<Artist>, albums: List<Album>, showHidden: Boolean): FilteredArtists {
        if (!byMarks) return FilteredArtists(emptyList(), emptyMap())
        val known = artists.associateBy { it.id }
        val whole = artists.filter { includes(it.marks) && shows(it.marks, showHidden) }
        val wholeIds = whole.mapTo(HashSet()) { it.id }
        val some = albums.filter { it.artistId != null && it.artistId !in wholeIds && includes(it.marks) && shows(it.marks, showHidden) }
            .groupBy { it.artistId!! }
            .filterKeys { id -> known[id]?.let { shows(it.marks, showHidden) } ?: true }
        val owners = some.map { (id, list) ->
            known[id] ?: list.first().artist.let { Artist(id, it, it, SortOrder.groupOf(it)) }
        }
        return FilteredArtists(
            com.tobeksoft.tunerfish.core.Library.fileArtists(whole + owners), // the object, not the entry
            some.mapValues { (_, list) -> list.mapTo(HashSet()) { it.id } },
        )
    }

    /**
     * The albums screen for an artist reached through this view: of [all] its albums, the ones in
     * [only], or all of them if null (the artist itself is marked, or "Show all"). Hidden albums are
     * left out as in the library, except in Hidden.
     */
    fun albumsOf(all: List<Album>, only: Set<String>?, showHidden: Boolean): List<Album> {
        val shown = if (only == null) all else all.filter { it.id in only }
        return if (this == Hidden) shown else com.tobeksoft.tunerfish.core.Library.visibleAlbums(shown, showHidden)
    }
}

/** The first match ("Breathe (The Dark Side of the Moon)") and how many [more] there are. */
data class MatchNote(val text: String, val more: Int = 0)

/**
 * A shortened artist list (a view by marks, a search): its artists in filing order, and for each, the
 * albums it shows (all of them if absent) and why it's listed, if that needs saying.
 */
class FilteredArtists(
    val artists: List<Artist>,
    private val albumIds: Map<String, Set<String>>,
    private val notes: Map<String, MatchNote> = emptyMap(),
) {
    /** The ids of the albums the artist shows here, or null for all of them. */
    fun albumsOf(artistId: String): Set<String>? = albumIds[artistId]

    /** What matched, for an artist listed for some of its albums or tracks. */
    fun noteOf(artistId: String): MatchNote? = notes[artistId]

    companion object {
        /** The whole list, every artist with all its albums. */
        fun all(artists: List<Artist>) = FilteredArtists(artists, emptyMap())
    }
}

/** A played track, as far as the played views care: its album, how often and when it was last played. */
data class PlayedTrack(val albumId: String, val plays: Int, val lastPlayed: Instant?) {
    companion object {
        fun from(item: BaseItem): PlayedTrack? {
            val albumId = item.albumId ?: return null
            val data = item.userData ?: return null
            return PlayedTrack(albumId, data.playCount, data.lastPlayedDate?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }
    }
}

/** Albums out of the tracks' play history. Album-first: an album counts as played as a whole. */
object PlayHistory {
    /** How many albums a played view lists. */
    const val ALBUMS = 100

    /** Albums by their latest played track, newest first. */
    fun recent(tracks: List<PlayedTrack>, limit: Int = ALBUMS): List<String> =
        tracks.groupBy { it.albumId }
            .mapValues { (_, t) -> t.mapNotNull { it.lastPlayed }.maxOrNull() ?: Instant.MIN }
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }

    /** The albums worth looking at for [most]: the most plays in all, before their track counts are known. */
    fun candidates(tracks: List<PlayedTrack>, limit: Int = ALBUMS * 2): List<String> =
        tracks.groupBy { it.albumId }.mapValues { (_, t) -> t.sumOf { it.plays } }
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }

    /**
     * Albums by how many times they've been played through: all plays of their tracks over the number
     * of tracks ([trackCounts]), so a long album isn't ahead for its length, nor one track played to
     * death ahead of an album heard whole. Ties go to the one played most recently.
     */
    fun most(tracks: List<PlayedTrack>, trackCounts: Map<String, Int>, limit: Int = ALBUMS): List<String> =
        tracks.groupBy { it.albumId }
            .map { (album, t) ->
                val count = maxOf(trackCounts[album] ?: 0, t.size)
                Triple(album, t.sumOf { it.plays }.toDouble() / count, t.mapNotNull { it.lastPlayed }.maxOrNull() ?: Instant.MIN)
            }
            .sortedWith(compareByDescending<Triple<String, Double, Instant>> { it.second }.thenByDescending { it.third })
            .take(limit).map { it.first }
}
