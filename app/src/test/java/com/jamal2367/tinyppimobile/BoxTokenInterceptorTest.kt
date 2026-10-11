package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.BoxTokenInterceptor
import com.jamal2367.tinyppimobile.data.remote.FailoverInterceptor
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The token on a picture's request.
 *
 * The add-on reads it from the header only, so a picture without it is refused
 * wherever reading needs the token - and a picture of one box must never carry
 * the token of the other.
 */
class BoxTokenInterceptorTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099, token = "ABCD2345")
    private val remote = ServerConfig(
        enabled = true,
        useHttps = true,
        host = "tinyppi.example.com",
        port = 443,
        token = "WXYZ6789",
    )

    private fun tokenSentTo(url: String, settings: AppSettings): String? {
        val router = ServerRouter().apply { update(settings) }
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(BoxTokenInterceptor(router))
            // Answers in place of the box, so nothing leaves the test.
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return sent?.header(FailoverInterceptor.TOKEN_HEADER)
    }

    @Test
    fun `each box's pictures carry that box's token`() {
        val settings = AppSettings(primary = local, secondary = remote)

        assertEquals("ABCD2345", tokenSentTo("http://192.168.1.10:8099/api/art?kind=poster&v=1", settings))
        assertEquals("WXYZ6789", tokenSentTo("https://tinyppi.example.com/api/art?kind=poster&v=1", settings))
    }

    @Test
    fun `an address that names neither box goes without a token`() {
        val settings = AppSettings(primary = local, secondary = remote)

        assertNull(tokenSentTo("http://192.168.1.10:9000/api/art", settings))
        assertNull(tokenSentTo("http://tinyppi.example.com:443/api/art", settings))
        assertNull(tokenSentTo("https://example.org/logo.png", settings))
    }

    @Test
    fun `a box with no token sends none`() {
        val settings = AppSettings(primary = local.copy(token = "  "))

        assertNull(tokenSentTo("http://192.168.1.10:8099/api/art", settings))
    }
}
