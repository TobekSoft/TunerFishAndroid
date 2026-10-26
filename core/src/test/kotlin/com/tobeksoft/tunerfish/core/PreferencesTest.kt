package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesTest {
    @Test
    fun theme_isStoredAsAWord_andUnknownWordsFallBackToSystem() {
        assertEquals(ThemeMode.Dark, ThemeMode.parse("Dark"))
        assertEquals(ThemeMode.System, ThemeMode.parse("Sepia"))
        assertEquals(ThemeMode.System, ThemeMode.parse(null))
    }

    @Test
    fun pickedFolders_getReadableNames() {
        assertEquals("Internal storage/Music/TunerFish", StorageNames.describe("primary:Music/TunerFish"))
        assertEquals("Internal storage", StorageNames.describe("primary:"))
        assertEquals("SD card/Music", StorageNames.describe("1A2B-3C4D:Music"))
        assertEquals("SD card", StorageNames.describe("1A2B-3C4D:"))
        assertEquals("Documents/Albums", StorageNames.describe("home:Albums"))
        assertEquals("usbdrive/x", StorageNames.describe("usbdrive:x/"))
    }
}
