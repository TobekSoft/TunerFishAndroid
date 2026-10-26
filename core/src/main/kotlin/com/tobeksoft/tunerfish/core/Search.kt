package com.tobeksoft.tunerfish.core

/** What the filter box looks in, any combination. Stored as words, e.g. "Artists Albums". */
enum class SearchScope { Artists, Albums, Tracks;
    companion object {
        val DEFAULT: Set<SearchScope> = setOf(Artists, Albums)

        /** An unknown word, or none at all, resets the setting to [DEFAULT]. */
        fun parse(value: String?): Set<SearchScope> {
            val words = value?.split(' ')?.filter { it.isNotBlank() } ?: return DEFAULT
            val scope = words.map { w -> entries.firstOrNull { it.name == w } ?: return DEFAULT }.toSet()
            return scope.ifEmpty { DEFAULT }
        }

        fun format(scope: Set<SearchScope>): String = entries.filter { it in scope }.joinToString(" ") { it.name }
    }
}

/** An album whose title matched, or a track on it ([track]); filed under the album's artist. */
data class SearchHit(val artistId: String, val albumId: String, val album: String, val track: String? = null, val hidden: Boolean = false) {
    /** What the artist's row says matched: the album, or "Track (Album)". */
    val label: String get() = if (track == null) album else "$track ($album)"
}

/**
 * The filter box: artist names (as [Library.filterArtists]), and album and track titles across the
 * whole collection. Album-first: a match is shown as its artist, with only the matching albums.
 */
object Search {
    /** Albums and tracks are looked for once the text is this long; artist names from the first letter. */
    const val MIN_LENGTH = 2

    /** Whether [text] contains [term], ignoring case and accents. */
    fun matches(text: String, term: String): Boolean =
        SortOrder.plainLetters(text).contains(SortOrder.plainLetters(term.trim()), ignoreCase = true)

    /** Whether [term] needs albums and tracks looked up (the server's search, or the phone's downloads). */
    fun looksBeyondArtists(term: String, scope: Set<SearchScope>): Boolean =
        term.trim().length >= MIN_LENGTH && (SearchScope.Albums in scope || SearchScope.Tracks in scope)

    /** Matches among the phone's albums and tracks, as far as [scope] goes. Albums come first. */
    fun hits(albums: List<Album>, tracks: List<Track>, term: String, scope: Set<SearchScope>): List<SearchHit> {
        if (!looksBeyondArtists(term, scope)) return emptyList()
        val albumHits = if (SearchScope.Albums !in scope) emptyList() else albums.mapNotNull { a ->
            val artist = a.artistId ?: return@mapNotNull null
            if (matches(a.title, term)) SearchHit(artist, a.id, a.title, hidden = a.marks.hidden) else null
        }
        val byId = albums.associateBy { it.id }
        val trackHits = if (SearchScope.Tracks !in scope) emptyList() else tracks.mapNotNull { t ->
            val a = byId[t.albumId] ?: return@mapNotNull null
            val artist = a.artistId ?: return@mapNotNull null
            if (matches(t.title, term)) SearchHit(artist, a.id, a.title, t.title, a.marks.hidden) else null
        }
        return albumHits + trackHits
    }

    /** Matches from the server's search results: [albums] (MusicAlbum items) first, then [tracks] (Audio). */
    fun hits(albums: List<BaseItem>, tracks: List<BaseItem>): List<SearchHit> =
        albums.mapNotNull { a ->
            val artist = a.albumArtists?.firstOrNull()?.id ?: return@mapNotNull null
            SearchHit(artist, a.id, a.name, hidden = Marks.from(a).hidden)
        } + tracks.mapNotNull { t ->
            val artist = t.albumArtists?.firstOrNull()?.id ?: return@mapNotNull null
            SearchHit(artist, t.albumId ?: return@mapNotNull null, t.album ?: "", t.name)
        }

    /**
     * Narrows [base] (the library, or a view by marks) to [term]. An artist whose name matches keeps
     * what [base] shows of it; otherwise an artist with matching albums or tracks is kept with only
     * those albums (of the ones [base] shows), and a note of what matched. [hits] may be from an
     * earlier, shorter text: only those that still match count. Hidden albums' hits are left out
     * unless [showHidden]. Too little text to search titles, with artist names left out, leaves [base] as it is.
     */
    fun narrow(base: FilteredArtists, term: String, scope: Set<SearchScope>, hits: List<SearchHit>, showHidden: Boolean): FilteredArtists {
        val t = term.trim()
        // Nothing to go on yet: no text, or too little for titles with artist names left out.
        if (t.isEmpty() || (SearchScope.Artists !in scope && !looksBeyondArtists(t, scope))) return base
        val found = if (!looksBeyondArtists(t, scope)) emptyMap() else hits
            .filter { h ->
                (showHidden || !h.hidden) && matches(h.track ?: h.album, t) &&
                    (if (h.track == null) SearchScope.Albums in scope else SearchScope.Tracks in scope)
            }
            .groupBy { it.artistId }
        val artists = mutableListOf<Artist>()
        val albumIds = mutableMapOf<String, Set<String>>()
        val notes = mutableMapOf<String, MatchNote>()
        for (a in base.artists) {
            val shown = base.albumsOf(a.id)
            if (SearchScope.Artists in scope && a.name.contains(t, ignoreCase = true)) {
                artists += a
                shown?.let { albumIds[a.id] = it }
                continue
            }
            val mine = found[a.id]?.filter { shown == null || it.albumId in shown }.orEmpty()
            if (mine.isEmpty()) continue
            artists += a
            albumIds[a.id] = mine.mapTo(LinkedHashSet()) { it.albumId }
            notes[a.id] = MatchNote(mine.first().label, mine.size - 1)
        }
        return FilteredArtists(artists, albumIds, notes)
    }
}
