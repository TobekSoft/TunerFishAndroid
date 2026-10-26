package com.tobeksoft.tunerfish.core

/** Light or dark colours. System follows the phone. Stored as its name; unknown names fall back to [System]. */
enum class ThemeMode { Light, Dark, System;
    companion object {
        fun parse(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: System
    }
}

object StorageNames {
    /**
     * A readable name for a folder picked with Android's folder picker, from its tree document id:
     * "primary:Music/TunerFish" → "Internal storage/Music/TunerFish", "1A2B-3C4D:Music" → "SD card/Music".
     * The volume's root shows as just the volume name.
     */
    fun describe(treeDocumentId: String): String {
        val volume = treeDocumentId.substringBefore(':')
        val path = treeDocumentId.substringAfter(':', "").trim('/')
        val volumeName = when {
            volume.equals("primary", ignoreCase = true) -> "Internal storage"
            volume.equals("home", ignoreCase = true) -> "Documents"
            Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}").matches(volume) -> "SD card"
            else -> volume
        }
        return if (path.isEmpty()) volumeName else "$volumeName/$path"
    }
}
