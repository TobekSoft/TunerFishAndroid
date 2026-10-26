package com.tobeksoft.tunerfish.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What part of the app a log entry is about. Stored as its name (a word). */
enum class LogArea { App, Server, Playback, Downloads, Plays, Marks, Crash;
    companion object {
        fun parse(value: String?): LogArea = entries.firstOrNull { it.name == value } ?: App
    }
}

data class LogEntry(val at: Instant, val area: LogArea, val message: String)

/**
 * The app's own log: what happened (connections, errors, downloads), kept on the phone for the
 * user to read and share by hand when something goes wrong. Never a secret in it.
 */
object AppLog {
    /** Entries kept; older ones are dropped. */
    const val KEEP = 1000

    /** The longest message kept (a crash's stack trace is cut there). */
    const val MAX_MESSAGE = 8000

    private val apiKey = Regex("""(api_key=)[^&\s"']+""", RegexOption.IGNORE_CASE)
    private val token = Regex("""(Token=")[^"]*(")""")
    private val accessToken = Regex(""""(AccessToken)"\s*:\s*"[^"]*"""")
    private val password = Regex(""""(Pw)"\s*:\s*"[^"]*"""")

    /** [text] without the session token or a password, wherever they turn up (stream URLs, headers, JSON). */
    fun redact(text: String): String = text
        .replace(apiKey, "$1…")
        .replace(token, "$1…$2")
        .replace(accessToken, "\"$1\":\"…\"")
        .replace(password, "\"$1\":\"…\"")

    /** A message as it's stored: no secrets, and not too long. */
    fun clean(message: String): String = redact(message).take(MAX_MESSAGE)

    /** A crash: the error and where it happened. */
    fun crash(error: Throwable): String = clean(error.stackTraceToString())

    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** One entry as a line of text, in [zone]'s time: "2026-10-25 15:20:01  Server    Using http://…". */
    fun line(e: LogEntry, zone: ZoneId): String =
        "${time.format(e.at.atZone(zone))}  ${e.area.name.padEnd(9)} ${e.message}"

    /** The log as shared: the [header] lines (app, phone, Android), a blank line, then the entries oldest first. */
    fun text(header: List<String>, entries: List<LogEntry>, zone: ZoneId): String =
        (header + "" + entries.sortedBy { it.at }.map { line(it, zone) }).joinToString("\n")
}
