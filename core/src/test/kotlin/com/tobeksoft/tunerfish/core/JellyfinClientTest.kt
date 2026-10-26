package com.tobeksoft.tunerfish.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JellyfinClientTest {
    private lateinit var server: MockWebServer
    private val client = JellyfinClient("dev123", "TunerFish", "0.1.0", "Pixel")

    @BeforeTest
    fun start() { server = MockWebServer().apply { start() } }

    @AfterTest
    fun stop() = server.shutdown()

    private val base get() = server.url("/").toString()

    @Test
    fun login_sendsCredentials_andRemembersTheToken() = runTest {
        server.enqueue(MockResponse().setBody("""{"AccessToken":"tok","User":{"Id":"u1","Name":"me"},"ServerId":"srv1","Extra":1}"""))

        val auth = client.login(base, "me", "secret")

        assertEquals("tok", auth.accessToken)
        assertEquals("tok", client.token)
        assertEquals("u1", client.userId)
        assertEquals("srv1", client.serverId)
        val req = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", req.path)
        assertEquals("""{"Username":"me","Pw":"secret"}""", req.body.readUtf8())
        assertEquals(
            """MediaBrowser Client="TunerFish", Device="Pixel", DeviceId="dev123", Version="0.1.0"""",
            req.getHeader("Authorization"),
        )
    }

    @Test
    fun requestsAfterLogin_carryTheToken_andAskForTheUsersArtists() = runTest {
        client.configure(listOf(base), "tok", "u1", null)
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"ar1","Name":"The Beatles","SortName":"beatles"}],"TotalRecordCount":1}"""))

        val result = client.albumArtists()

        assertEquals("The Beatles", result.items.single().name)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/Artists/AlbumArtists?userId=u1"), req.path)
        assertTrue(req.getHeader("Authorization")!!.endsWith(""", Token="tok""""))
    }

    @Test
    fun search_asksJellyfinForOneTypeOfItem_withTheTextEncoded() = runTest {
        client.configure(listOf(base), "tok", "u1", null)
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"al1","Name":"The Dark Side of the Moon","AlbumArtists":[{"Name":"Pink Floyd","Id":"ar1"}]}]}"""))

        val result = client.search(" dark side & co ", "MusicAlbum")

        assertEquals("ar1", result.items.single().albumArtists!!.single().id)
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/Items?userId=u1&searchTerm=dark%20side%20%26%20co&includeItemTypes=MusicAlbum&recursive=true"), path)
    }

    @Test
    fun albumsAndTracks_useTheItemsEndpoint() = runTest {
        client.configure(listOf(base), "tok", "u1", null)
        server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"t1","Name":"Song","RunTimeTicks":1850000000,"IndexNumber":1}]}"""))

        client.albums("ar4")
        val tracks = client.tracks("al1")

        assertTrue(server.takeRequest().path!!.contains("includeItemTypes=MusicAlbum&recursive=true&albumArtistIds=ar4"))
        assertTrue(server.takeRequest().path!!.contains("parentId=al1&includeItemTypes=Audio"))
        assertEquals(185_000L, Track.from(tracks.items.single()).durationMs)
    }

    @Test
    fun failedRequests_throwWithTheStatus() = runTest {
        client.configure(listOf(base), "tok", "u1", null)
        server.enqueue(MockResponse().setResponseCode(401))

        val e = assertFailsWith<JellyfinException> { client.albumArtists() }
        assertEquals(401, e.status)
    }

    @Test
    fun streamAndImageUrls_areBuiltFromTheServerAndToken() {
        client.configure(listOf("http://srv:8096/"), "a b", "u1", null)

        assertEquals("http://srv:8096/Audio/t1/stream?static=true&api_key=a%20b", client.streamUrl("t1"))
        assertEquals("http://srv:8096/Items/al1/Images/Primary?maxHeight=300&quality=90&tag=x", client.imageUrl("al1", "x", 300))
        assertEquals("http://srv:8096/Items/al1/Images/Primary?maxHeight=300&quality=90", client.imageUrl("al1", null, 300))
    }

    @Test
    fun serverUrls_getAScheme_andLoseTrailingSlashes() {
        assertEquals("http://192.168.1.20:8096", JellyfinClient.normaliseServerUrl(" 192.168.1.20:8096/ "))
        assertEquals("https://music.example.org", JellyfinClient.normaliseServerUrl("https://music.example.org/"))
    }

    @Test
    fun downloadImage_asksForAJpegOfTheGivenHeight() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("jpg"))

        client.downloadImage("a1", 1200) { _, body -> body.readBytes() }

        assertEquals("/Items/a1/Images/Primary?maxHeight=1200&quality=90&format=Jpg", server.takeRequest().path)
    }

    @Test
    fun playReports_sayWhichTrackWhereAndWhetherPaused() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }

        client.reportStart("t1", 0, paused = false, playSessionId = "ps1")
        client.reportProgress("t1", 12_500, paused = true, playSessionId = "ps1")
        client.reportStopped("t1", 200_000, playSessionId = "ps1")

        val start = server.takeRequest()
        assertEquals("/Sessions/Playing", start.path)
        assertEquals(
            """{"ItemId":"t1","MediaSourceId":"t1","PositionTicks":0,"IsPaused":false,"CanSeek":true,""" +
                """"PlayMethod":"DirectPlay","PlaySessionId":"ps1"}""",
            start.body.readUtf8(),
        )
        val progress = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", progress.path)
        assertTrue(progress.body.readUtf8().contains(""""PositionTicks":125000000,"IsPaused":true"""))
        val stopped = server.takeRequest()
        assertEquals("/Sessions/Playing/Stopped", stopped.path)
        assertEquals(
            """{"ItemId":"t1","MediaSourceId":"t1","PositionTicks":2000000000,"PlaySessionId":"ps1"}""",
            stopped.body.readUtf8(),
        )
    }

    @Test
    fun anOfflinePlay_isMarkedPlayedAtItsTime() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        server.enqueue(MockResponse().setBody("{}"))

        client.markPlayed("t1", java.time.Instant.parse("2026-10-24T05:12:00Z"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/UserPlayedItems/t1?userId=u1&datePlayed=2026-10-24T05%3A12%3A00Z", req.path)
        assertTrue(req.getHeader("Authorization")!!.contains("Token=\"tok\""))
    }

    @Test
    fun artistsAndAlbums_comeWithTheUsersMarks() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        server.enqueue(MockResponse().setBody(
            """{"Items":[{"Id":"a1","Name":"Loved","UserData":{"IsFavorite":true,"Likes":null,"PlayCount":3}},""" +
                """{"Id":"a2","Name":"Binned","UserData":{"IsFavorite":false,"Likes":false}},{"Id":"a3","Name":"Plain"}]}""",
        ))

        val artists = Library.sortArtists(client.albumArtists().items).associateBy { it.id }

        assertTrue(server.takeRequest().path!!.contains("enableUserData=true"))
        assertEquals(Marks(favourite = true), artists.getValue("a1").marks)
        assertTrue(artists.getValue("a2").marks.hidden)
        assertEquals(Marks.None, artists.getValue("a3").marks)
    }

    @Test
    fun views_askForMarkedAlbums_playedTracks_andAlbumsById() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"al1","Name":"A","AlbumArtists":[{"Name":"X","Id":"ar1"}]}]}"""))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"t1","AlbumId":"al1","UserData":{"PlayCount":2,"LastPlayedDate":"2026-10-24T08:00:00Z"}}]}"""))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"al1","Name":"A","ChildCount":9}]}"""))

        val favourites = client.allAlbums("IsFavorite").items
        val played = client.playedTracks("DatePlayed").items.mapNotNull(PlayedTrack::from)
        val byId = client.albumsById(listOf("al1"))

        assertEquals("ar1", Album.from(favourites.single(), "").artistId)
        assertEquals(2, played.single().plays)
        assertEquals(9, byId.single().childCount)
        val paths = List(3) { server.takeRequest().path!! }
        assertTrue(paths[0].contains("includeItemTypes=MusicAlbum") && paths[0].contains("filters=IsFavorite"))
        assertTrue(paths[1].contains("filters=IsPlayed") && paths[1].contains("sortBy=DatePlayed&sortOrder=Descending"))
        assertTrue(paths[2].contains("ids=al1") && paths[2].contains("ChildCount"))
    }

    @Test
    fun marks_areSetAndClearedForTheUser() = runTest {
        client.configure(listOf(base), "tok", "u1", "srv1")
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }

        client.setFavourite("al1", true)
        client.setFavourite("al1", false)
        client.setLikes("al1", true)
        client.setLikes("al1", null)

        fun next() = server.takeRequest().let { "${it.method} ${it.path}" }
        assertEquals("POST /UserFavoriteItems/al1?userId=u1", next())
        assertEquals("DELETE /UserFavoriteItems/al1?userId=u1", next())
        assertEquals("POST /UserItems/al1/Rating?userId=u1&likes=true", next())
        assertEquals("DELETE /UserItems/al1/Rating?userId=u1", next())
    }
}
