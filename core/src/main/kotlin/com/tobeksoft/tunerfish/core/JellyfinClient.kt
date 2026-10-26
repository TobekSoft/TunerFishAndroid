package com.tobeksoft.tunerfish.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Thin client for the handful of Jellyfin endpoints TunerFish needs.
 * The server does all the scanning and tag reading; we just ask it questions.
 *
 * One server can have several addresses (say the home network and a NetBird address for when
 * you're out). They're tried in order and the first that answers as the same server is used;
 * the token and user are the same whichever address is in use.
 *
 * @param clientName how this app is listed under the server's devices and sessions.
 * @param deviceName the phone's name in that list.
 */
class JellyfinClient(
    private val deviceId: String,
    private val clientName: String,
    private val clientVersion: String,
    private val deviceName: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
    probeTimeoutMs: Long = 4000,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val probeHttp = http.newBuilder().callTimeout(probeTimeoutMs, TimeUnit.MILLISECONDS).build()
    // A FLAC can take minutes over NetBird: no limit on the whole call, only on silence.
    private val downloadHttp = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** The server's addresses, most preferred first. */
    var addresses: List<String> = emptyList()
        private set
    /** The address in use now: one of [addresses]. */
    var serverUrl: String = ""
        private set
    /** The server's own id, so a different server answering at one of the addresses is never sent the token. */
    var serverId: String? = null
        private set
    var token: String? = null
        private set
    var userId: String? = null
        private set

    fun configure(addresses: List<String>, token: String?, userId: String?, serverId: String?) {
        this.addresses = addresses.map(::normaliseServerUrl).filter { it.isNotEmpty() }.distinct()
        // Keep the address in use if it's still listed, so editing the list doesn't switch servers needlessly.
        if (serverUrl !in this.addresses) serverUrl = this.addresses.firstOrNull() ?: ""
        this.token = token
        this.userId = userId
        this.serverId = serverId
    }

    val authHeader: String
        get() {
            val h = "MediaBrowser Client=\"$clientName\", Device=\"$deviceName\", " +
                "DeviceId=\"$deviceId\", Version=\"$clientVersion\""
            return token?.let { "$h, Token=\"$it\"" } ?: h
        }

    private fun request(path: String) = Request.Builder()
        .url(serverUrl + path)
        .header("Authorization", authHeader)

    /** Signs in at one address. The caller decides which other addresses to keep (see [configure]). */
    suspend fun login(serverUrl: String, userName: String, password: String): AuthResult {
        configure(listOf(serverUrl), null, null, null)
        val body = buildJsonObject { put("Username", userName); put("Pw", password) }.toString()
        val req = request("/Users/AuthenticateByName")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val auth = json.decodeFromString<AuthResult>(send(req))
        configure(listOf(serverUrl), auth.accessToken, auth.user.id, auth.serverId)
        return auth
    }

    /** Best effort; the local token is forgotten either way. */
    suspend fun logout() {
        try {
            send(request("/Sessions/Logout").post(ByteArray(0).toRequestBody()).build())
        } catch (_: IOException) {
        }
    }

    /** The id of the Jellyfin server answering at [address], or null if nothing answers there. */
    suspend fun serverIdAt(address: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(normaliseServerUrl(address) + "/System/Info/Public").build()
            probeHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else json.decodeFromString<PublicSystemInfo>(resp.body?.string() ?: "").id.ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Whether this server answers at [address] (and is the same server, if its id is known). */
    suspend fun isReachable(address: String): Boolean =
        serverIdAt(address)?.let { serverId == null || it == serverId } ?: false

    /** For sign-ins saved before the server id was recorded. */
    fun rememberServerId(id: String) {
        serverId = id
    }

    /** Told when [connect] changes address or finds none answering, for the app's log. */
    var onConnect: (String) -> Unit = {}

    /**
     * Checks every address at once and switches to the first in order that answers.
     * Returns false (and stays put) if none does.
     */
    suspend fun connect(): Boolean = coroutineScope {
        val checks = addresses.map { a -> a to async { isReachable(a) } }
        for ((address, reachable) in checks) {
            if (reachable.await()) {
                if (address != serverUrl) onConnect("Using $address")
                serverUrl = address
                checks.forEach { it.second.cancel() }
                return@coroutineScope true
            }
        }
        onConnect("No address answers (${addresses.joinToString()})")
        false
    }

    /** Every album artist in the library (filed client-side, see [Library.sortArtists]). */
    suspend fun albumArtists(): ItemsResult =
        get("/Artists/AlbumArtists?userId=$userId&sortBy=SortName&sortOrder=Ascending&fields=SortName&enableUserData=true")

    /** Albums whose album artist is the given artist. */
    suspend fun albums(albumArtistId: String): ItemsResult =
        get("/Items?userId=$userId&includeItemTypes=MusicAlbum&recursive=true&albumArtistIds=$albumArtistId&sortBy=SortName&fields=SortName&enableUserData=true")

    /** Every album in the library with the user's marks, or only those passing a Jellyfin [filter] (IsFavorite, Likes). */
    suspend fun allAlbums(filter: String? = null): ItemsResult =
        get("/Items?userId=$userId&includeItemTypes=MusicAlbum&recursive=true&sortBy=SortName&fields=SortName" +
            "&enableUserData=true&enableImageTypes=Primary&imageTypeLimit=1" + (filter?.let { "&filters=$it" } ?: ""))

    /**
     * Jellyfin's own search: items of [type] (MusicAlbum or Audio) whose title contains [term], with
     * their album artists, at most [limit] of them.
     */
    suspend fun search(term: String, type: String, limit: Int = 300): ItemsResult =
        get("/Items?userId=$userId&searchTerm=${URLEncoder.encode(term.trim(), "UTF-8").replace("+", "%20")}" +
            "&includeItemTypes=$type&recursive=true&limit=$limit&enableUserData=true&enableImages=false")

    /** Albums by id, with their track counts, in no particular order. */
    suspend fun albumsById(ids: List<String>): List<BaseItem> =
        ids.chunked(50).flatMap { chunk ->
            get<ItemsResult>("/Items?userId=$userId&ids=${chunk.joinToString(",")}&fields=SortName,ChildCount&enableUserData=true").items
        }

    /** The user's played tracks, most recent ([sortBy] DatePlayed) or most played (PlayCount) first. */
    suspend fun playedTracks(sortBy: String, limit: Int = 2000): ItemsResult =
        get("/Items?userId=$userId&includeItemTypes=Audio&recursive=true&filters=IsPlayed&sortBy=$sortBy" +
            "&sortOrder=Descending&limit=$limit&enableUserData=true&enableImages=false")

    /** Tracks on an album, in disc / track order. */
    suspend fun tracks(albumId: String): ItemsResult =
        get("/Items?userId=$userId&parentId=$albumId&includeItemTypes=Audio&recursive=true&sortBy=ParentIndexNumber,IndexNumber,SortName&fields=SortName,MediaSources")

    /** Direct stream of the original file at the current address; the player decodes whatever format it is. */
    fun streamUrl(itemId: String): String =
        "$serverUrl/Audio/$itemId/stream".toHttpUrl().newBuilder()
            .addQueryParameter("static", "true")
            .addQueryParameter("api_key", token ?: "")
            .build().toString()

    fun imageUrl(itemId: String, tag: String?, maxHeight: Int): String =
        "$serverUrl/Items/$itemId/Images/Primary".toHttpUrl().newBuilder()
            .addQueryParameter("maxHeight", maxHeight.toString())
            .addQueryParameter("quality", "90")
            .apply { if (tag != null) addQueryParameter("tag", tag) }
            .build().toString()

    // Playback reports: Jellyfin keeps the user's play count, last played and played flag from these.
    // TunerFish plays the original file (static stream or a download), so the method is DirectPlay.

    /** A track started playing. [playSessionId] ties its reports together. */
    suspend fun reportStart(itemId: String, positionMs: Long, paused: Boolean, playSessionId: String) =
        postJson("/Sessions/Playing", playState(itemId, positionMs, paused, playSessionId))

    /** Where playback is now, and whether it's paused: sent on pause, resume, seek and now and then. */
    suspend fun reportProgress(itemId: String, positionMs: Long, paused: Boolean, playSessionId: String) =
        postJson("/Sessions/Playing/Progress", playState(itemId, positionMs, paused, playSessionId))

    /** The track stopped or playback moved on, at [positionMs]. The server decides whether it counts as played. */
    suspend fun reportStopped(itemId: String, positionMs: Long, playSessionId: String) =
        postJson(
            "/Sessions/Playing/Stopped",
            buildJsonObject {
                put("ItemId", itemId)
                put("MediaSourceId", itemId)
                put("PositionTicks", positionMs * TICKS_PER_MS)
                put("PlaySessionId", playSessionId)
            }.toString(),
        )

    /** Marks a track played at [at]: a play made offline, sent later. Adds one to its play count. */
    suspend fun markPlayed(itemId: String, at: Instant) {
        val url = "$serverUrl/UserPlayedItems/$itemId".toHttpUrl().newBuilder()
            .addQueryParameter("userId", userId ?: "")
            .addQueryParameter("datePlayed", at.toString())
            .build()
        withRetry { send(Request.Builder().url(url).header("Authorization", authHeader).post(ByteArray(0).toRequestBody()).build()) }
    }

    // Marks: the user's own heart and thumbs on an artist or album (see Marks).

    /** Sets or clears the heart. */
    suspend fun setFavourite(itemId: String, on: Boolean) = userCall("/UserFavoriteItems/$itemId", if (on) "POST" else "DELETE")

    /** Thumbs up (true), thumbs down (false), or neither (null). */
    suspend fun setLikes(itemId: String, likes: Boolean?) =
        if (likes == null) userCall("/UserItems/$itemId/Rating", "DELETE")
        else userCall("/UserItems/$itemId/Rating", "POST", "likes" to likes.toString())

    private suspend fun userCall(path: String, method: String, vararg query: Pair<String, String>) {
        val url = "$serverUrl$path".toHttpUrl().newBuilder()
            .addQueryParameter("userId", userId ?: "")
            .apply { for ((k, v) in query) addQueryParameter(k, v) }
            .build()
        val body = if (method == "POST") ByteArray(0).toRequestBody() else null
        withRetry { send(Request.Builder().url(url).header("Authorization", authHeader).method(method, body).build()) }
    }

    private fun playState(itemId: String, positionMs: Long, paused: Boolean, playSessionId: String) = buildJsonObject {
        put("ItemId", itemId)
        put("MediaSourceId", itemId)
        put("PositionTicks", positionMs * TICKS_PER_MS)
        put("IsPaused", paused)
        put("CanSeek", true)
        put("PlayMethod", "DirectPlay")
        put("PlaySessionId", playSessionId)
    }.toString()

    private suspend fun postJson(path: String, body: String) {
        withRetry { send(request(path).post(body.toRequestBody("application/json".toMediaType())).build()) }
    }

    /** Downloads an item's cover at up to [maxHeight] pixels high, as JPEG. */
    suspend fun <T> downloadImage(itemId: String, maxHeight: Int, write: (contentType: String?, body: InputStream) -> T): T =
        fetch("/Items/$itemId/Images/Primary?maxHeight=$maxHeight&quality=90&format=Jpg", write)

    private suspend fun <T> fetch(pathAndQuery: String, write: (String?, InputStream) -> T): T = withContext(Dispatchers.IO) {
        val req = request(pathAndQuery).get().build()
        downloadHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw JellyfinException(resp.code, "GET ${req.url.encodedPath} failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("GET ${req.url.encodedPath}: empty response")
            write(resp.header("Content-Type"), body.byteStream())
        }
    }

    /** A GET that, if the server can't be reached, looks for another address and tries once more. */
    private suspend inline fun <reified T> get(pathAndQuery: String): T =
        json.decodeFromString(withRetry { send(request(pathAndQuery).get().build()) })

    /** Runs [call]; if the server can't be reached, looks for another address and runs it once more. */
    private suspend fun <T> withRetry(call: suspend () -> T): T = try {
        call()
    } catch (e: IOException) {
        if (e is JellyfinException || addresses.size < 2) throw e
        val before = serverUrl
        if (!connect() || serverUrl == before) throw e
        call()
    }

    private suspend fun send(req: Request): String = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw JellyfinException(resp.code, "${req.method} ${req.url.encodedPath} failed: HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    companion object {
        /** Jellyfin positions are in ticks of 100 ns. */
        const val TICKS_PER_MS = 10_000L

        /** "192.168.1.20:8096/" → "http://192.168.1.20:8096". Keeps https if given. */
        fun normaliseServerUrl(text: String): String {
            val t = text.trim().trimEnd('/')
            if (t.isEmpty()) return t
            return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "http://$t"
        }
    }
}

class JellyfinException(val status: Int, message: String) : IOException(message)
