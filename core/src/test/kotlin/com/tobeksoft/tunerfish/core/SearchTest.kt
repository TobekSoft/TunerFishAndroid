package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    private fun artist(id: String, name: String = id) = Artist(id, name, name.lowercase(), "A")
    private fun album(id: String, title: String, artistId: String, marks: Marks = Marks.None) =
        Album(id, title, title.lowercase(), null, artistId, null, marks, artistId)
    private fun track(title: String, albumId: String) = Track(title, title, "", albumId, "", null, null, null)

    private val floyd = artist("floyd", "Pink Floyd")
    private val faith = artist("faith", "Faith Hill")
    private val prodigy = artist("prodigy", "The Prodigy")
    private val library = FilteredArtists.all(listOf(faith, floyd, prodigy))

    private val albums = listOf(
        album("dsotm", "The Dark Side of the Moon", "floyd"), album("wall", "The Wall", "floyd"),
        album("fat", "The Fat of the Land", "prodigy"), album("breathe-album", "Breathe", "faith"),
    )
    private val tracks = listOf(track("Breathe", "dsotm"), track("Time", "dsotm"), track("Breathe", "fat"), track("Wish You Were Here", "wall"))
    private val all = setOf(SearchScope.Artists, SearchScope.Albums, SearchScope.Tracks)

    private fun search(term: String, scope: Set<SearchScope> = all, base: FilteredArtists = library, showHidden: Boolean = false) =
        Search.narrow(base, term, scope, Search.hits(albums, tracks, term, scope), showHidden)

    @Test
    fun anAlbumTitle_findsItsArtist_withOnlyThatAlbum() {
        val r = search("dark side")
        assertEquals(listOf("floyd"), r.artists.map { it.id })
        assertEquals(setOf("dsotm"), r.albumsOf("floyd"))
        assertEquals(MatchNote("The Dark Side of the Moon"), r.noteOf("floyd"))
    }

    @Test
    fun aTrackTitle_findsEveryArtistWithIt_andTheAlbumItsOn() {
        val r = search("breathe")
        assertEquals(listOf("faith", "floyd", "prodigy"), r.artists.map { it.id })
        assertEquals(setOf("dsotm"), r.albumsOf("floyd"))
        assertEquals(MatchNote("Breathe (The Dark Side of the Moon)"), r.noteOf("floyd"))
        assertEquals(setOf("breathe-album"), r.albumsOf("faith")) // the album title, before its tracks
    }

    @Test
    fun anArtistName_keepsAllItsAlbums_withNoNote() {
        val r = search("floyd")
        assertEquals(listOf("floyd"), r.artists.map { it.id })
        assertNull(r.albumsOf("floyd"))
        assertNull(r.noteOf("floyd"))
    }

    @Test
    fun theScope_choosesWhatIsLookedIn() {
        assertTrue(search("breathe", setOf(SearchScope.Artists)).artists.isEmpty())
        assertEquals(listOf("faith"), search("breathe", setOf(SearchScope.Albums)).artists.map { it.id })
        assertEquals(listOf("floyd", "prodigy"), search("breathe", setOf(SearchScope.Tracks)).artists.map { it.id })
        assertTrue(search("floyd", setOf(SearchScope.Albums, SearchScope.Tracks)).artists.isEmpty())
    }

    @Test
    fun severalMatches_onOneArtist_areCountedInTheNote() {
        val r = search("the", setOf(SearchScope.Albums))
        assertEquals(setOf("dsotm", "wall"), r.albumsOf("floyd"))
        assertEquals(MatchNote("The Dark Side of the Moon", more = 1), r.noteOf("floyd"))
    }

    @Test
    fun oneLetter_looksOnlyAtArtistNames() {
        assertTrue(Search.hits(albums, tracks, "t", all).isEmpty())
        assertEquals(listOf("faith", "floyd", "prodigy"), search("i").artists.map { it.id })
        assertEquals(listOf("faith", "floyd", "prodigy"), search("z", setOf(SearchScope.Tracks)).artists.map { it.id }) // left as it is
    }

    @Test
    fun accentsAndCase_dontMatter_forTitles() {
        val withAccent = listOf(album("x", "Déjà Vu", "faith"))
        assertEquals(1, Search.hits(withAccent, emptyList(), "deja vu", all).size)
        assertEquals(1, Search.hits(withAccent, emptyList(), "DÉJÀ", all).size)
    }

    @Test
    fun hitsForAShorterText_areNarrowedToTheLongerOne() {
        val earlier = Search.hits(albums, tracks, "th", all) // still arriving for "the dark"
        val r = Search.narrow(library, "the dark", all, earlier, showHidden = false)
        assertEquals(listOf("floyd"), r.artists.map { it.id })
        assertEquals(setOf("dsotm"), r.albumsOf("floyd"))
    }

    @Test
    fun hiddenAlbums_areLeftOut_unlessShowHidden() {
        val hidden = listOf(album("dsotm", "The Dark Side of the Moon", "floyd", Marks(likes = false)))
        val hits = Search.hits(hidden, emptyList(), "dark", all)
        assertTrue(Search.narrow(library, "dark", all, hits, showHidden = false).artists.isEmpty())
        assertEquals(listOf("floyd"), Search.narrow(library, "dark", all, hits, showHidden = true).artists.map { it.id })
    }

    @Test
    fun inAView_onlyTheViewsArtistsAndAlbums_areSearched() {
        // Favourites: Pink Floyd for The Wall only, The Prodigy whole.
        val favourites = FilteredArtists(listOf(floyd, prodigy), mapOf("floyd" to setOf("wall")))
        assertTrue(search("dark side", base = favourites).artists.isEmpty()) // not a favourite album
        assertEquals(setOf("wall"), search("wish", base = favourites).albumsOf("floyd"))
        assertEquals(setOf("fat"), search("breathe", base = favourites).albumsOf("prodigy"))
        assertEquals(setOf("wall"), search("pink", base = favourites).albumsOf("floyd")) // a name match keeps the view's albums
    }

    @Test
    fun serverResults_becomeHits_underTheirAlbumArtist() {
        val albumItem = BaseItem(id = "dsotm", name = "The Dark Side of the Moon", albumArtists = listOf(NameId("Pink Floyd", "floyd")))
        val trackItem = BaseItem(id = "t1", name = "Breathe", album = "The Dark Side of the Moon", albumId = "dsotm",
            albumArtists = listOf(NameId("Pink Floyd", "floyd")))
        assertEquals(
            listOf(SearchHit("floyd", "dsotm", "The Dark Side of the Moon"), SearchHit("floyd", "dsotm", "The Dark Side of the Moon", "Breathe")),
            Search.hits(listOf(albumItem), listOf(trackItem)),
        )
        assertTrue(Search.hits(listOf(albumItem.copy(albumArtists = null)), emptyList()).isEmpty())
    }

    @Test
    fun theScope_isStoredAsWords() {
        assertEquals("Artists Tracks", SearchScope.format(setOf(SearchScope.Tracks, SearchScope.Artists)))
        assertEquals(setOf(SearchScope.Artists, SearchScope.Tracks), SearchScope.parse("Artists Tracks"))
        assertEquals(SearchScope.DEFAULT, SearchScope.parse(null))
        assertEquals(SearchScope.DEFAULT, SearchScope.parse(""))
        assertEquals(SearchScope.DEFAULT, SearchScope.parse("Artists Genres")) // unknown word: reset
    }
}
