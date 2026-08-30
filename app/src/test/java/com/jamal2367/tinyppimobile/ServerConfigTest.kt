package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.HdrGrade
import com.jamal2367.tinyppimobile.util.MediaUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two addresses, and the addresses built out of them.
 *
 * The failover walks whatever [AppSettings.servers] hands it, so which boxes
 * end up in that list and in what order is the whole of the routing policy.
 */
class ServerConfigTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099, token = "ABCD2345")
    private val remote = ServerConfig(
        enabled = true,
        useHttps = true,
        host = "tinyppi.example.com",
        port = 443,
        token = "WXYZ6789",
    )

    @Test
    fun `a base url is a scheme, a host and a port, and nothing else`() {
        assertEquals("http://192.168.1.10:8099", local.baseUrl)
        assertEquals("https://tinyppi.example.com:443", remote.baseUrl)
    }

    @Test
    fun `a trailing slash typed into the host does not reach the url`() {
        val typed = local.copy(host = "192.168.1.10/")
        assertEquals("http://192.168.1.10:8099", typed.baseUrl)
    }

    @Test
    fun `an address with no host is not worth a request`() {
        assertFalse(local.copy(host = "").isUsable)
        assertFalse(local.copy(enabled = false).isUsable)
        assertFalse(local.copy(port = 0).isUsable)
        assertTrue(local.isUsable)
    }

    @Test
    fun `automatic hands over both, local first`() {
        val settings = AppSettings(
            primary = local,
            secondary = remote,
            connectionMode = ConnectionMode.AUTO,
        )
        assertEquals(listOf(local, remote), settings.servers())
        assertTrue(settings.isConfigured)
    }

    @Test
    fun `each of the fixed modes hands over one`() {
        val settings = AppSettings(primary = local, secondary = remote)

        assertEquals(
            listOf(local),
            settings.copy(connectionMode = ConnectionMode.PRIMARY_ONLY).servers(),
        )
        assertEquals(
            listOf(remote),
            settings.copy(connectionMode = ConnectionMode.SECONDARY_ONLY).servers(),
        )
    }

    @Test
    fun `a mode pointed at an address nobody filled in is not configured`() {
        val settings = AppSettings(
            primary = local,
            secondary = ServerConfig(),
            connectionMode = ConnectionMode.SECONDARY_ONLY,
        )
        assertTrue(settings.servers().isEmpty())
        assertFalse(settings.isConfigured)
    }

    @Test
    fun `an artwork address carries the picture's own tag and the token`() {
        val url = MediaUrls.art(local, ArtTags(poster = "1a2b3c4d"), MediaUrls.ArtKind.POSTER)

        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=poster&v=1a2b3c4d&token=ABCD2345",
            url,
        )
    }

    @Test
    fun `a title with no poster is asked for no poster`() {
        // Most of what a box plays has no library entry, so this is the normal
        // case: the add-on sends an empty tag and there is nothing to fetch.
        assertNull(MediaUrls.art(local, ArtTags(), MediaUrls.ArtKind.POSTER))
        assertNull(MediaUrls.art(null, ArtTags(poster = "1a2b"), MediaUrls.ArtKind.POSTER))
    }

    @Test
    fun `a logo needs no token, and an absent one is no address`() {
        assertEquals(
            "http://192.168.1.10:8099/media/codecs/Dolby_Vision.png",
            MediaUrls.logo(local, "codecs/Dolby_Vision.png"),
        )
        assertNull(MediaUrls.logo(local, ""))
    }

    @Test
    fun `both spellings of HDR10 plus name the same grade`() {
        // The Home window carries `hdr10plus` - Kodi's boolean parser reads a
        // `+` as an AND - while the logo maps spell it with the plus.
        assertEquals(HdrGrade.HDR10_PLUS, HdrGrade.of("hdr10plus"))
        assertEquals(HdrGrade.HDR10_PLUS, HdrGrade.of("hdr10+"))
        assertEquals(HdrGrade.HDR10, HdrGrade.of("hdr10"))
        assertEquals(HdrGrade.DOLBY_VISION, HdrGrade.of("dolbyvision"))
        assertEquals(HdrGrade.HLG, HdrGrade.of("hlg"))
        // An empty token is SDR, not "unknown": the add-on writes one only for
        // the HDR formats.
        assertEquals(HdrGrade.SDR, HdrGrade.of(""))
    }
}
