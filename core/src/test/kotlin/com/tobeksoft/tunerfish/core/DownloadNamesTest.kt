package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadNamesTest {
    @Test
    fun safe_replacesCharactersFatRefuses() =
        assertEquals("AC_DC _ What_ _Live_", DownloadNames.safe("""AC/DC : What? "Live""""))

    @Test
    fun safe_keepsAccentsAndOtherScripts() =
        assertEquals("Björk – Homogenic 東京", DownloadNames.safe("Björk – Homogenic 東京"))

    @Test
    fun safe_collapsesSpacesAndTrimsDotsAndSpaces() =
        assertEquals("Some Album", DownloadNames.safe("  Some   Album... "))

    @Test
    fun safe_emptyOrOnlyDotsIsUnknown() {
        assertEquals("Unknown", DownloadNames.safe(""))
        assertEquals("Unknown", DownloadNames.safe(".."))
    }

    @Test
    fun safe_cutsLongNamesOnACharacterBoundary() {
        val s = DownloadNames.safe("é".repeat(300)) // 2 bytes each
        assertEquals(100, s.length)
        assertTrue(s.toByteArray().size <= 200)
    }

    @Test
    fun trackFile_padsTheNumber() =
        assertEquals("01 Once.flac", DownloadNames.trackFile(1, 1, "Once", multiDisc = false, extension = "flac"))

    @Test
    fun trackFile_addsTheDiscOnMultiDiscAlbums() =
        assertEquals("2-07 Once.flac", DownloadNames.trackFile(2, 7, "Once", multiDisc = true, extension = "flac"))

    @Test
    fun trackFile_withoutANumberIsJustTheTitle() =
        assertEquals("Once.mp3", DownloadNames.trackFile(null, null, "Once", multiDisc = true, extension = "mp3"))

    @Test
    fun trackFile_makesTheTitleSafe() =
        assertEquals("03 Why_ Why_.mp3", DownloadNames.trackFile(1, 3, "Why? Why?", multiDisc = false, extension = "mp3"))

    @Test
    fun extension_fromContainer() {
        assertEquals("flac", DownloadNames.extension("flac", "audio/mpeg"))
        assertEquals("m4a", DownloadNames.extension("mov,mp4,m4a,3gp,3g2,mj2", null))
        assertEquals("mp3", DownloadNames.extension("MP3", null))
    }

    @Test
    fun extension_fromContentTypeWhenNoContainer() {
        assertEquals("flac", DownloadNames.extension(null, "audio/flac"))
        assertEquals("mp3", DownloadNames.extension("", "audio/mpeg; charset=binary"))
        assertEquals("audio", DownloadNames.extension(null, "application/octet-stream"))
    }

    @Test
    fun downloadState_unknownIsQueued() {
        assertEquals(DownloadState.Done, DownloadState.parse("Done"))
        assertEquals(DownloadState.Queued, DownloadState.parse("Finished"))
        assertEquals(DownloadState.Queued, DownloadState.parse(null))
    }

    @Test
    fun isReplaceable_musicAndImagesOnly() {
        for (name in listOf("01 - Once.flac", "Once.MP3", "folder.jpg", "Cover.PNG", "a.m4a", "b.opus"))
            assertTrue(DownloadNames.isReplaceable(name), name)
        for (name in listOf("Ten.cue", "rip.log", "notes.txt", "Scans", ".nomedia", "flac"))
            assertEquals(false, DownloadNames.isReplaceable(name), name)
    }
}
