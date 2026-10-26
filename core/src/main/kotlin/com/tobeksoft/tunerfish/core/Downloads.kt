package com.tobeksoft.tunerfish.core

/**
 * Where an album's download has got to. Stored as its name; unknown names fall back to [Queued].
 * [Conflict]: its folder already exists with another app's files, waiting for Replace or Don't
 * download. [Skipped]: Don't download was chosen; it streams.
 */
enum class DownloadState { Queued, Downloading, Done, Failed, Conflict, Skipped;
    companion object {
        fun parse(value: String?): DownloadState = entries.firstOrNull { it.name == value } ?: Queued
    }
}

/**
 * How downloads are named on disk: <Album Artist>/<Album>/<01 Title>.<ext>, plus cover.jpg,
 * so a file manager or another player can make sense of them.
 */
object DownloadNames {
    const val COVER = "cover.jpg"

    /** Longest name in UTF-8 bytes: ext4 and FAT allow 255; leave room for ".part" and " (2)". */
    private const val MAX_BYTES = 200
    private val notAllowed = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /**
     * [name] made safe as one folder or file name on any Android storage, SD cards (FAT) included:
     * characters FAT refuses become "_", runs of spaces become one, and leading and trailing dots
     * and spaces go. Nothing left gives "Unknown".
     */
    fun safe(name: String): String {
        var s = name.replace(notAllowed, "_").replace(Regex("\\s+"), " ").trim(' ', '.')
        if (s.toByteArray().size > MAX_BYTES) {
            val out = StringBuilder()
            var bytes = 0
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val len = String(Character.toChars(cp)).toByteArray().size
                if (bytes + len > MAX_BYTES) break
                out.appendCodePoint(cp)
                bytes += len
                i += Character.charCount(cp)
            }
            s = out.toString().trimEnd(' ', '.')
        }
        return s.ifEmpty { "Unknown" }
    }

    private val replaceable = setOf(
        // music
        "mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "wv", "mpc",
        "aif", "aiff", "alac", "dsf", "dff",
        // cover images
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
    )

    /**
     * Whether Replace deletes this file from another app's copy of an album: music files and images
     * (covers). Anything else (.cue, .log, .txt, subfolders) stays.
     */
    fun isReplaceable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in replaceable

    /** "01 Title.flac"; "2-01 Title.flac" on an album with more than one disc; "Title.flac" with no number. */
    fun trackFile(disc: Int?, number: Int?, title: String, multiDisc: Boolean, extension: String): String {
        val n = number?.let { "%02d".format(it) }
        val prefix = when {
            n == null -> ""
            multiDisc && disc != null -> "$disc-$n "
            else -> "$n "
        }
        return safe(prefix + title) + "." + extension
    }

    /**
     * The file extension for a track, from Jellyfin's container ("flac", or a list like
     * "mov,mp4,m4a,3gp") or else the download's content type.
     */
    fun extension(container: String?, contentType: String?): String {
        val parts = container?.lowercase()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (parts.isNotEmpty()) return if ("m4a" in parts) "m4a" else parts.first()
        return when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            else -> "audio"
        }
    }
}

/** Asking before an album uses a lot of mobile data (FLAC is about 300 MB an album, MP3 usually under 100). */
object DataUse {
    const val ASK_ABOVE_BYTES = 100L * 1000 * 1000

    /** Whether to ask before fetching [bytes] over mobile data. An unknown size (null) doesn't ask. */
    fun shouldAsk(bytes: Long?): Boolean = bytes != null && bytes > ASK_ABOVE_BYTES

    /** "85 MB", "320 MB", "1.2 GB" (decimal units, as phones show storage). */
    fun sizeText(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(java.util.Locale.ROOT, bytes / 1e9)
        else -> "${(bytes + 500_000) / 1_000_000} MB"
    }
}

/** How much space downloads may take before albums that aren't kept are removed. Stored as its name. */
enum class DownloadLimit(val bytes: Long?, val label: String) {
    OneGB(1_000_000_000, "1 GB"),
    FiveGB(5_000_000_000, "5 GB"),
    TenGB(10_000_000_000, "10 GB"),
    TwentyGB(20_000_000_000, "20 GB"),
    FiftyGB(50_000_000_000, "50 GB"),
    NoLimit(null, "No limit");

    companion object {
        fun parse(value: String?): DownloadLimit = entries.firstOrNull { it.name == value } ?: TwentyGB
    }
}

/** A downloaded album as the clean-up sees it. [lastPlayed] is ISO-8601, so it sorts as text. */
data class CleanupCandidate(
    val albumId: String,
    val bytes: Long,
    val kept: Boolean,
    val inQueue: Boolean,
    val lastPlayed: String?,
)

object Cleanup {
    /**
     * The albums to remove to bring downloads back under [limit]: never a kept one or one in the play
     * queue; the longest unplayed first. If even that isn't enough, it removes what it may and stops.
     */
    fun choose(albums: List<CleanupCandidate>, limit: DownloadLimit): List<String> {
        val max = limit.bytes ?: return emptyList()
        var total = albums.sumOf { it.bytes }
        if (total <= max) return emptyList()
        val removable = albums.filter { !it.kept && !it.inQueue }.sortedBy { it.lastPlayed ?: "" }
        val out = mutableListOf<String>()
        for (a in removable) {
            if (total <= max) break
            out += a.albumId
            total -= a.bytes
        }
        return out
    }
}
