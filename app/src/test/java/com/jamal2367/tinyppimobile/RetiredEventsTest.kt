package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The player cache events an older add-on still writes never reach a screen. */
class RetiredEventsTest {

    @Test
    fun `cache events from an older add-on are dropped`() = runTest {
        val api = FakeApi()
        api.history = {
            History(
                events = listOf(
                    PlaybackEvent(t = 4.0, kind = "mode", from = "SDR", to = "DV"),
                    PlaybackEvent(t = 310.0, kind = "cache_low", value = 18.0),
                    PlaybackEvent(t = 318.0, kind = "cache_recovered", value = 100.0),
                    PlaybackEvent(t = 400.0, kind = "temperature", value = 77.0),
                ),
            )
        }

        val events = PlayerRepository(api, FakeApi.json).history().events

        assertEquals(listOf("mode", "temperature"), events.map { it.kind })
    }
}
