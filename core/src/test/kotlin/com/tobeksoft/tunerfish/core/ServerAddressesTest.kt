package com.tobeksoft.tunerfish.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One server, several addresses (home network, NetBird, ...): the first that answers as the same server wins. */
class ServerAddressesTest {
    private val servers = mutableListOf<MockWebServer>()
    private val client = JellyfinClient("dev", "TunerFish", "0.1.0", "Pixel", probeTimeoutMs = 1500)

    @AfterTest
    fun stop() = servers.forEach { runCatching { it.shutdown() } }

    /** A fake Jellyfin at its own address, with the given server id. */
    private fun jellyfin(id: String): String {
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path == "/System/Info/Public" -> MockResponse().setBody("""{"Id":"$id","ServerName":"x"}""")
                request.path!!.startsWith("/Artists/AlbumArtists") -> MockResponse().setBody("""{"Items":[{"Id":"a","Name":"From $id"}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        s.start()
        servers += s
        return s.url("/").toString()
    }

    /** An address where nothing answers. */
    private fun deadAddress(): String {
        val s = MockWebServer().apply { start() }
        val url = s.url("/").toString()
        s.shutdown()
        return url
    }

    @Test
    fun connect_prefersTheFirstAddressThatAnswers() = runTest {
        val home = jellyfin("srv1")
        val netbird = jellyfin("srv1")
        client.configure(listOf(home, netbird), "tok", "u1", "srv1")

        assertTrue(client.connect())
        assertEquals(JellyfinClient.normaliseServerUrl(home), client.serverUrl)
    }

    @Test
    fun connect_fallsBackWhenTheFirstAddressIsDown() = runTest {
        val home = deadAddress()
        val netbird = jellyfin("srv1")
        client.configure(listOf(home, netbird), "tok", "u1", "srv1")

        assertTrue(client.connect())
        assertEquals(JellyfinClient.normaliseServerUrl(netbird), client.serverUrl)
    }

    @Test
    fun connect_saysWhenItChangesAddress_orFindsNone() = runTest {
        val home = deadAddress()
        val netbird = jellyfin("srv1")
        client.configure(listOf(home, netbird), "tok", "u1", "srv1")
        val said = mutableListOf<String>()
        client.onConnect = { said += it }

        client.connect()
        client.connect() // same address again: nothing to say
        assertEquals(listOf("Using ${JellyfinClient.normaliseServerUrl(netbird)}"), said)

        client.configure(listOf(home), "tok", "u1", "srv1")
        assertFalse(client.connect())
        assertTrue(said.last().startsWith("No address answers"), said.last())
    }

    @Test
    fun connect_skipsADifferentServer() = runTest {
        val stranger = jellyfin("someone-else")
        val netbird = jellyfin("srv1")
        client.configure(listOf(stranger, netbird), "tok", "u1", "srv1")

        assertTrue(client.connect())
        assertEquals(JellyfinClient.normaliseServerUrl(netbird), client.serverUrl)
        assertFalse(client.isReachable(stranger))
    }

    @Test
    fun connect_failsAndStaysPut_whenNothingAnswers() = runTest {
        val home = deadAddress()
        client.configure(listOf(home, deadAddress()), "tok", "u1", "srv1")

        assertFalse(client.connect())
        assertEquals(JellyfinClient.normaliseServerUrl(home), client.serverUrl)
    }

    @Test
    fun aFailedRequest_switchesAddressAndRetries() = runTest {
        val home = deadAddress()
        val netbird = jellyfin("srv1")
        client.configure(listOf(home, netbird), "tok", "u1", "srv1")

        val artists = client.albumArtists() // no connect() first: the request itself finds the way

        assertEquals("From srv1", artists.items.single().name)
        assertEquals(JellyfinClient.normaliseServerUrl(netbird), client.serverUrl)
    }

    @Test
    fun serverIdAt_readsThePublicInfo_orNullWhenNothingAnswers() = runTest {
        assertEquals("srv1", client.serverIdAt(jellyfin("srv1")))
        assertEquals(null, client.serverIdAt(deadAddress()))
    }

    @Test
    fun addresses_areNormalisedAndDeduplicated() {
        client.configure(listOf("192.168.1.20:8096/", "http://192.168.1.20:8096", " ", "100.64.0.5:8096"), null, null, null)

        assertEquals(listOf("http://192.168.1.20:8096", "http://100.64.0.5:8096"), client.addresses)
        assertEquals("http://192.168.1.20:8096", client.serverUrl)

        client.configure(listOf("100.64.0.5:8096", "192.168.1.20:8096"), null, null, null)
        assertEquals("http://192.168.1.20:8096", client.serverUrl) // reordering doesn't switch by itself

        client.configure(listOf("100.64.0.5:8096"), null, null, null)
        assertEquals("http://100.64.0.5:8096", client.serverUrl)
    }
}
