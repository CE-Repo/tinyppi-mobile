package com.jamal2367.tinyppimobile.remote

import com.jamal2367.tinyppimobile.data.remote.AvailableUpdate
import com.jamal2367.tinyppimobile.data.remote.GithubRelease
import com.jamal2367.tinyppimobile.data.remote.ReleaseId
import com.jamal2367.tinyppimobile.data.remote.UpdateChecker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Whether the newest release on GitHub is newer than the running build. */
@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    private val server = MockWebServer()
    private val checker = UpdateChecker(OkHttpClient(), Json { ignoreUnknownKeys = true })
    private val current = ReleaseId(listOf(1, 5, 5), 40)

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun check(body: String, code: Int = 200): AvailableUpdate? {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
        return runBlocking { checker.findNewerRelease(server.url("/latest").toString(), current, "fallback") }
    }

    @Test
    fun `a newer release is offered under its title`() {
        assertEquals(
            AvailableUpdate("TinyPPI 1.6.0 - build 41", "https://x"),
            check("""{"name":"TinyPPI 1.6.0 - build 41","tag_name":"build-41","html_url":"https://x"}"""),
        )
        assertEquals("application/vnd.github+json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `a release without a title or address falls back to the tag and the page`() {
        assertEquals(AvailableUpdate("build-41", "fallback"), check("""{"name":" ","tag_name":"build-41","html_url":""}"""))
        assertEquals(AvailableUpdate("build-41", "fallback"), check("""{"tag_name":"build-41"}"""))
    }

    @Test
    fun `drafts, older builds and failures offer nothing`() {
        assertNull(check("""{"name":"1.9.0","draft":true}"""))
        assertNull(check("""{"name":"1.5.5 build 40"}"""))
        assertNull(check("""{"name":"1.0"}"""))
        assertNull(check("{}", code = 404))
        assertNull(check("not json"))
    }

    @Test
    fun `a release read from nothing is no draft`() {
        assertFalse(GithubRelease().draft)
        assertEquals("GithubRelease", GithubRelease.serializer().descriptor.serialName.substringAfterLast('.'))
    }

    @Test
    fun `an address that cannot be asked offers nothing`() {
        assertNull(runBlocking { checker.findNewerRelease("http://127.0.0.1:1/", current, "f") })
    }

    @Test
    fun `release ids are read from whichever text carries them`() {
        assertEquals(ReleaseId(listOf(1, 2), 7), ReleaseId.of(null, "v1.2", "Build 7"))
        assertEquals(ReleaseId(emptyList(), 0), ReleaseId.of("nothing"))
        assertEquals(ReleaseId(emptyList(), 0), ReleaseId.of("build-99999999999"))
        assertEquals(ReleaseId(listOf(1), 0), ReleaseId.of("1.99999999999"))
    }

    @Test
    fun `a release is behind by version, then by build`() {
        val base = ReleaseId(listOf(1, 2), 5)
        assertTrue(base.isBehind(ReleaseId(listOf(1, 3), 0)))
        assertFalse(base.isBehind(ReleaseId(listOf(1, 1), 99)))
        assertTrue(base.isBehind(ReleaseId(listOf(1, 2, 0), 6)))
        assertFalse(base.isBehind(ReleaseId(listOf(1, 2), 5)))
        assertFalse(base.isBehind(ReleaseId(listOf(1, 2), 0)))
        assertFalse(ReleaseId(listOf(1, 2), 0).isBehind(ReleaseId(listOf(1, 2), 9)))
        assertTrue(ReleaseId(emptyList(), 1).isBehind(ReleaseId(listOf(3), 2)))
        assertEquals(0, ReleaseId.compareVersions(emptyList(), listOf(1)))
        assertEquals(0, ReleaseId.compareVersions(listOf(1), emptyList()))
        assertEquals(-1, ReleaseId.compareVersions(listOf(1), listOf(1, 1)))
        assertEquals(1, ReleaseId.compareVersions(listOf(2), listOf(1, 9)))
    }
}
