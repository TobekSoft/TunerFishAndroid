package com.tobeksoft.tunerfish.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android-only behaviour around downloads and offline browsing (LibraryTest mirrors the desktop, so not there). */
class DownloadsTest {
    @Test
    fun fileArtists_matchesTheServerOrder_forArtistsReadBackFromTheDownloads() {
        val items = listOf(
            BaseItem(id = "1", name = "The The", sortName = "the"),
            BaseItem(id = "2", name = "The Beatles", sortName = "beatles"),
            BaseItem(id = "3", name = "Björk", sortName = "björk"),
            BaseItem(id = "4", name = "10cc", sortName = "10cc"),
        )
        val fromServer = Library.sortArtists(items)
        // As offline browsing rebuilds them: from the stored id, name and sort key, in any order.
        val readBack = fromServer.reversed().map { Artist(it.id, it.name, it.sortKey, SortOrder.groupOf(it.sortKey)) }

        assertEquals(fromServer, Library.fileArtists(readBack))
    }

    @Test
    fun track_takesItsSizeFromTheFirstMediaSource() {
        val json = Json { ignoreUnknownKeys = true }
        val item = json.decodeFromString<BaseItem>("""{"Id":"t1","Name":"Once","MediaSources":[{"Size":31415926,"Path":"x"}]}""")

        assertEquals(31415926L, Track.from(item).bytes)
        assertEquals(null, Track.from(BaseItem(id = "t2", name = "No sources")).bytes)
    }

    @Test
    fun dataUse_asksOnlyAboveTheThreshold() {
        assertFalse(DataUse.shouldAsk(null))
        assertFalse(DataUse.shouldAsk(DataUse.ASK_ABOVE_BYTES))
        assertTrue(DataUse.shouldAsk(DataUse.ASK_ABOVE_BYTES + 1))
    }

    @Test
    fun dataUse_sizeText() {
        assertEquals("85 MB", DataUse.sizeText(85_000_000))
        assertEquals("320 MB", DataUse.sizeText(319_600_000))
        assertEquals("1.2 GB", DataUse.sizeText(1_234_000_000))
    }

    private val gb = 1_000_000_000L
    private fun album(id: String, gbs: Long, played: String?, kept: Boolean = false, inQueue: Boolean = false) =
        CleanupCandidate(id, gbs * gb, kept, inQueue, played)

    @Test
    fun cleanup_underTheLimitRemovesNothing() =
        assertEquals(emptyList(), Cleanup.choose(listOf(album("a", 3, "2026-01-01"), album("b", 2, null)), DownloadLimit.FiveGB))

    @Test
    fun cleanup_removesTheLongestUnplayedFirst_untilUnderTheLimit() {
        val albums = listOf(
            album("new", 2, "2026-10-20T10:00:00Z"),
            album("old", 2, "2026-01-01T10:00:00Z"),
            album("middle", 2, "2026-05-01T10:00:00Z"),
        )
        assertEquals(listOf("old"), Cleanup.choose(albums, DownloadLimit.FiveGB))
    }

    @Test
    fun cleanup_neverRemovesKeptOrQueuedAlbums_evenIfStillOver() {
        val albums = listOf(
            album("kept", 4, "2020-01-01", kept = true),
            album("queued", 4, "2020-01-02", inQueue = true),
            album("plain", 1, "2026-01-01"),
        )
        assertEquals(listOf("plain"), Cleanup.choose(albums, DownloadLimit.FiveGB))
    }

    @Test
    fun cleanup_noLimitRemovesNothing() =
        assertEquals(emptyList(), Cleanup.choose(listOf(album("a", 500, "2020-01-01")), DownloadLimit.NoLimit))

    @Test
    fun downloadLimit_unknownIsTwentyGB() {
        assertEquals(DownloadLimit.FiftyGB, DownloadLimit.parse("FiftyGB"))
        assertEquals(DownloadLimit.OneGB, DownloadLimit.parse("OneGB"))
        assertEquals(DownloadLimit.TwentyGB, DownloadLimit.parse("20"))
    }
}
