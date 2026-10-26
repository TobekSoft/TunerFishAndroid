package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Same library as the desktop tests' FakeJellyfinServer, so the expected orders match theirs. */
class LibraryTest {
    private val artistItems = listOf(
        BaseItem(id = "ar1", name = "The Beatles", sortName = "beatles"),
        BaseItem(id = "ar2", name = "Now That's What I Call Music 5", sortName = "now that's what i call music 5"),
        BaseItem(id = "ar3", name = "The The", sortName = "the"),
        BaseItem(id = "ar4", name = "Elvis Costello", sortName = "elvis costello"),
        BaseItem(id = "ar5", name = "ABBA", sortName = "abba"),
        BaseItem(id = "ar6", name = "Björk", sortName = "björk"),
        BaseItem(id = "ar7", name = "10cc", sortName = "10cc"),
        BaseItem(id = "ar8", name = "+++ (Crosses)", sortName = "+++ (crosses)"),
    )

    private val costello = listOf(
        BaseItem(id = "ec1", name = "This Year's Model", sortName = "this year's model", productionYear = 1978),
        BaseItem(id = "ec2", name = "My Aim Is True", productionYear = 1977),
        BaseItem(id = "ec3", name = "The Juliet Letters", sortName = "juliet letters", productionYear = 1993),
        BaseItem(id = "ec4", name = "A Case for Song", sortName = "case for song", productionYear = 1990),
    ).map { Album.from(it, "Elvis Costello") }

    private fun names(artists: List<Artist>) = artists.joinToString(" | ") { it.name }
    private fun titles(albums: List<Album>) = albums.joinToString(" | ") { it.title }

    @Test
    fun artists_areFiledByJellyfinSortName() {
        val artists = Library.sortArtists(artistItems)

        assertEquals(
            "+++ (Crosses) | 10cc | ABBA | The Beatles | Björk | Elvis Costello | Now That's What I Call Music 5 | The The",
            names(artists),
        )
        assertEquals("beatles", artists[3].sortKey)
    }

    @Test
    fun albumTitles_areFiledByJellyfinSortName_fallingBackToTheTitle() {
        val albums = Library.sortAlbums(costello, AlbumSortMode.Title)

        assertEquals("A Case for Song | The Juliet Letters | My Aim Is True | This Year's Model", titles(albums))
        assertEquals("My Aim Is True", albums[2].sortTitle) // no sort name from the server
    }

    @Test
    fun yearSort_isUnaffectedBySortNames() {
        assertEquals(
            "My Aim Is True | This Year's Model | A Case for Song | The Juliet Letters",
            titles(Library.sortAlbums(costello, AlbumSortMode.YearAscending)),
        )
        assertEquals(
            "The Juliet Letters | A Case for Song | This Year's Model | My Aim Is True",
            titles(Library.sortAlbums(costello, AlbumSortMode.YearDescending)),
        )
    }

    @Test
    fun albumsWithoutAYear_goLastInBothYearOrders() {
        val albums = costello + Album.from(BaseItem(id = "x", name = "Demos"), "Elvis Costello")

        assertEquals("Demos", Library.sortAlbums(albums, AlbumSortMode.YearAscending).last().title)
        assertEquals("Demos", Library.sortAlbums(albums, AlbumSortMode.YearDescending).last().title)
    }

    @Test
    fun filter_matchesDisplayNameIgnoringCase_andGroupsFollowIt() {
        val all = Library.sortArtists(artistItems)
        assertEquals("# A B E N T", Library.groupsPresent(all).sorted().joinToString(" "))

        val filtered = Library.filterArtists(all, "  the ")
        assertEquals("The Beatles | The The", names(filtered))
        assertEquals("B T", Library.groupsPresent(filtered).sorted().joinToString(" "))

        assertEquals(all, Library.filterArtists(all, " "))
    }

    @Test
    fun firstIndexInGroup_findsTheStartOfEachBlock() {
        val all = Library.sortArtists(artistItems)

        assertEquals("Björk", all[Library.firstIndexInGroup(all, "B") + 1].name)
        assertEquals(3, Library.firstIndexInGroup(all, "B"))
        assertEquals(-1, Library.firstIndexInGroup(all, "Q"))
    }

    @Test
    fun albumSortMode_isStoredAsAWord_andUnknownWordsFallBack() {
        assertEquals(AlbumSortMode.Title, AlbumSortMode.parse("Title"))
        assertEquals(AlbumSortMode.YearAscending, AlbumSortMode.parse("SomethingFromTheFuture"))
        assertEquals(AlbumSortMode.YearAscending, AlbumSortMode.parse(null))
    }

    @Test
    fun durations_areMinutesAndSeconds() {
        assertEquals("3:05", Library.formatDuration(185_000))
        assertEquals("1:02:03", Library.formatDuration(3_723_000))
        assertEquals("", Library.formatDuration(null))
    }
}
