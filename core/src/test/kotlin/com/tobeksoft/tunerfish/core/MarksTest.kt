package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarksTest {
    @Test
    fun theHeart_isIndependentOfTheThumbs() {
        val m = Marks.None.withFavourite(true).withLiked(true)
        assertTrue(m.favourite && m.liked)
        assertTrue(m.withFavourite(false).liked)
    }

    @Test
    fun likedAndHidden_areOneDial() {
        val liked = Marks.None.withLiked(true)
        val hidden = liked.withHidden(true)
        assertTrue(hidden.hidden)
        assertFalse(hidden.liked) // hiding clears the like
        assertTrue(hidden.withLiked(true).liked) // and a like brings it back out of hiding
        assertEquals(Marks.None, liked.withLiked(false))
        assertEquals(Marks.None, hidden.withHidden(false))
    }

    @Test
    fun turningOffOneEnd_leavesTheOtherAlone() {
        assertTrue(Marks.None.withHidden(true).withLiked(false).hidden)
        assertTrue(Marks.None.withLiked(true).withHidden(false).liked)
    }

    @Test
    fun anUnsentChange_winsOverTheServer() {
        val server = Marks(favourite = true)
        assertEquals(server, Marks.current(server, pending = null))
        assertEquals(Marks.None, Marks.current(server, pending = Marks.None))
    }

    @Test
    fun hiddenArtistsAndAlbums_areLeftOut_unlessShown() {
        val artists = listOf(
            Artist("a1", "Kept", "kept", "K"),
            Artist("a2", "Free with a magazine", "free", "F", Marks(likes = false)),
        )
        assertEquals(listOf("a1"), Library.visibleArtists(artists, showHidden = false).map { it.id })
        assertEquals(listOf("a1", "a2"), Library.visibleArtists(artists, showHidden = true).map { it.id })

        val albums = listOf(Album("b1", "Bundled", "bundled", null, "X", null, Marks(likes = false)))
        assertTrue(Library.visibleAlbums(albums, showHidden = false).isEmpty())
    }
}
