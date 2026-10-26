package com.tobeksoft.tunerfish.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewsTest {
    private fun album(id: String, marks: Marks = Marks.None) = Album(id, id, id, null, "", null, marks)
    private fun artist(id: String, marks: Marks = Marks.None) = Artist(id, id, id, "A", marks)

    private val fav = Marks(favourite = true)
    private val liked = Marks(likes = true)
    private val hidden = Marks(likes = false)
    private val favHidden = Marks(favourite = true, likes = false)

    private fun album(id: String, artistId: String, marks: Marks = Marks.None) = Album(id, id, id, null, artistId, null, marks, artistId)
    private val library = listOf(artist("abba"), artist("beatles", fav), artist("cure"), artist("doors", hidden))
    private fun FilteredArtists.ids() = artists.map { it.id }

    @Test
    fun playedViews_dropHiddenAlbums_unlessShowHidden() {
        val all = listOf(album("a", fav), album("b", favHidden), album("c", hidden))
        assertEquals(listOf("a"), LibraryView.RecentlyPlayed.albums(all, showHidden = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), LibraryView.MostPlayed.albums(all, showHidden = true).map { it.id })
    }

    @Test
    fun aMarkedArtist_isListedWithAllItsAlbums() {
        val v = LibraryView.Favourites.filter(library, emptyList(), showHidden = false)
        assertEquals(listOf("beatles"), v.ids())
        assertNull(v.albumsOf("beatles"))
    }

    @Test
    fun anArtistWithMarkedAlbums_isListedWithOnlyThose() {
        val albums = listOf(album("arrival", "abba", fav), album("waterloo", "abba"), album("gold", "abba", fav))
        val v = LibraryView.Favourites.filter(library, albums, showHidden = false)
        assertEquals(listOf("abba", "beatles"), v.ids()) // filing order
        assertEquals(setOf("arrival", "gold"), v.albumsOf("abba"))
    }

    @Test
    fun aMarkedArtistWithAMarkedAlbum_stillShowsAllItsAlbums() {
        val v = LibraryView.Favourites.filter(library, listOf(album("help", "beatles", fav)), showHidden = false)
        assertEquals(listOf("beatles"), v.ids())
        assertNull(v.albumsOf("beatles"))
    }

    @Test
    fun hiddenArtists_hideTheirMarkedAlbums_unlessShowHidden() {
        val albums = listOf(album("la-woman", "doors", fav), album("bad", "cure", favHidden))
        assertEquals(listOf("beatles"), LibraryView.Favourites.filter(library, albums, showHidden = false).ids())
        val shown = LibraryView.Favourites.filter(library, albums, showHidden = true)
        assertEquals(listOf("beatles", "cure", "doors"), shown.ids())
        assertEquals(setOf("bad"), shown.albumsOf("cure"))
    }

    @Test
    fun hidden_listsHiddenArtistsWhole_andArtistsWithHiddenAlbums() {
        val albums = listOf(album("junk", "cure", hidden), album("pornography", "cure"))
        val v = LibraryView.Hidden.filter(library, albums, showHidden = false)
        assertEquals(listOf("cure", "doors"), v.ids())
        assertEquals(setOf("junk"), v.albumsOf("cure"))
        assertNull(v.albumsOf("doors"))
    }

    @Test
    fun anAlbumWhoseArtistIsntListed_isFiledUnderItsArtistName() {
        val v = LibraryView.Liked.filter(library, listOf(Album("x", "X", "x", null, "Blondie", null, liked, "blondie")), showHidden = false)
        assertEquals(listOf("Blondie"), v.artists.map { it.name })
        assertEquals(setOf("x"), v.albumsOf("blondie"))
    }

    @Test
    fun onlyTheViewsByMarks_filter() {
        assertTrue(LibraryView.Library.filter(library, emptyList(), showHidden = true).artists.isEmpty())
        assertTrue(LibraryView.MostPlayed.filter(library, emptyList(), showHidden = true).artists.isEmpty())
    }

    @Test
    fun theAlbumsScreen_showsTheViewsAlbums_orAll() {
        val all = listOf(album("a", "x", fav), album("b", "x"), album("c", "x", hidden))
        assertEquals(listOf("a"), LibraryView.Favourites.albumsOf(all, setOf("a"), showHidden = false).map { it.id })
        assertEquals(listOf("a", "b"), LibraryView.Favourites.albumsOf(all, null, showHidden = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), LibraryView.Favourites.albumsOf(all, null, showHidden = true).map { it.id })
        assertEquals(listOf("c"), LibraryView.Hidden.albumsOf(all, setOf("c"), showHidden = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), LibraryView.Hidden.albumsOf(all, null, showHidden = false).map { it.id })
    }

    private fun t(album: String, plays: Int, day: Int?) =
        PlayedTrack(album, plays, day?.let { Instant.parse("2026-10-%02dT12:00:00Z".format(it)) })

    @Test
    fun recent_ordersAlbumsByTheirLatestTrack() {
        val tracks = listOf(t("old", 9, 1), t("new", 1, 2), t("old", 1, 3), t("undated", 1, null))
        assertEquals(listOf("old", "new", "undated"), PlayHistory.recent(tracks))
        assertEquals(listOf("old"), PlayHistory.recent(tracks, limit = 1))
    }

    @Test
    fun most_isPlaysThroughTheWholeAlbum_notJustItsLength() {
        // long: 20 tracks heard once each = once through; short: 5 tracks heard 3 times = three times.
        // riff: one track of 10 played 20 times = twice through.
        val tracks = List(20) { t("long", 1, 1) } + List(5) { t("short", 3, 2) } + t("riff", 20, 3)
        val counts = mapOf("long" to 20, "short" to 5, "riff" to 10)
        assertEquals(listOf("short", "riff", "long"), PlayHistory.most(tracks, counts))
    }

    @Test
    fun most_breaksTiesByTheMostRecent_andCopesWithoutTrackCounts() {
        val tracks = listOf(t("a", 2, 1), t("b", 2, 5))
        assertEquals(listOf("b", "a"), PlayHistory.most(tracks, emptyMap())) // counts unknown: tracks seen
    }

    @Test
    fun candidates_areTheAlbumsWithTheMostPlaysInAll() {
        val tracks = listOf(t("a", 1, 1), t("b", 5, 1), t("c", 2, 1), t("c", 2, 1))
        assertEquals(listOf("b", "c"), PlayHistory.candidates(tracks, limit = 2))
    }

    @Test
    fun aPlayedTrack_comesFromItsUserData() {
        val item = BaseItem(id = "t", albumId = "al", userData = UserItemData(playCount = 4, lastPlayedDate = "2026-10-24T08:32:44.0312444Z"))
        assertEquals(PlayedTrack("al", 4, Instant.parse("2026-10-24T08:32:44.0312444Z")), PlayedTrack.from(item))
        assertEquals(null, PlayedTrack.from(item.copy(albumId = null)))
    }
}
