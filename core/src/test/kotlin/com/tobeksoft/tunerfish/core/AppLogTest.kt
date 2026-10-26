package com.tobeksoft.tunerfish.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogTest {
    @Test
    fun theToken_neverGetsIn_fromAStreamUrl_aHeader_orJson() {
        assertEquals(
            "Source error: http://192.168.1.33:8096/Audio/abc/stream?static=true&api_key=…",
            AppLog.redact("Source error: http://192.168.1.33:8096/Audio/abc/stream?static=true&api_key=0123456789abcdef"),
        )
        assertEquals("…?api_key=…&tag=x", AppLog.redact("…?api_key=secret&tag=x"))
        assertEquals("""MediaBrowser Client="TunerFish", Token="…"""", AppLog.redact("""MediaBrowser Client="TunerFish", Token="secret""""))
        assertEquals("""{"AccessToken":"…","User":{}}""", AppLog.redact("""{"AccessToken": "secret","User":{}}"""))
        assertEquals("""{"Username":"me","Pw":"…"}""", AppLog.redact("""{"Username":"me","Pw":"hunter2"}"""))
    }

    @Test
    fun ordinaryText_isLeftAlone() {
        val text = "Can't reach http://100.64.0.5:8096: timeout"
        assertEquals(text, AppLog.redact(text))
    }

    @Test
    fun longMessages_areCut() {
        assertEquals(AppLog.MAX_MESSAGE, AppLog.clean("x".repeat(AppLog.MAX_MESSAGE + 500)).length)
    }

    @Test
    fun aCrash_keepsTheErrorAndWhereItHappened_withoutSecrets() {
        val text = AppLog.crash(IllegalStateException("bad url ?api_key=secret"))
        assertTrue(text.startsWith("java.lang.IllegalStateException: bad url ?api_key=…"), text)
        assertTrue("AppLogTest" in text) // the stack trace
        assertFalse("secret" in text)
    }

    @Test
    fun theSharedText_isTheHeader_thenTheEntriesOldestFirst_inLocalTime() {
        val utc = ZoneId.of("UTC")
        val entries = listOf(
            LogEntry(Instant.parse("2026-10-25T05:00:02Z"), LogArea.Server, "Using http://a"),
            LogEntry(Instant.parse("2026-10-25T05:00:01Z"), LogArea.App, "Started 0.9.11"),
        )
        assertEquals(
            "TunerFish 0.9.11\nSM-G960F, Android 10\n\n" +
                "2026-10-25 05:00:01  App       Started 0.9.11\n" +
                "2026-10-25 05:00:02  Server    Using http://a",
            AppLog.text(listOf("TunerFish 0.9.11", "SM-G960F, Android 10"), entries, utc),
        )
        assertTrue(AppLog.line(entries[0], ZoneId.of("Australia/Sydney")).startsWith("2026-10-25 16:00:02"))
    }

    @Test
    fun areas_areStoredAsWords() {
        assertEquals(LogArea.Downloads, LogArea.parse("Downloads"))
        assertEquals(LogArea.App, LogArea.parse("Gremlins"))
    }
}
