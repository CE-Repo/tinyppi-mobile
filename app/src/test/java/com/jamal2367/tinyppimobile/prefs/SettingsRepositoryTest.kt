package com.jamal2367.tinyppimobile.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.prefs.SettingsRepository
import com.jamal2367.tinyppimobile.data.prefs.StoredReachability
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the app remembers between launches, written and read back. */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The store itself, for writing what this build never would. */
    @Suppress("UNCHECKED_CAST")
    private fun store(): DataStore<Preferences> {
        val owner = Class.forName("com.jamal2367.tinyppimobile.data.prefs.SettingsRepositoryKt")
        val getter = owner.getDeclaredMethod("getDataStore", Context::class.java).apply { isAccessible = true }
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    @Test
    fun `every setting is written and read back`() = runBlocking {
        val repository = SettingsRepository(context)
        store().edit { it.clear() }
        assertEquals(AppSettings(), repository.settings.first())

        repository.setPrimary(ServerConfig(enabled = false, useHttps = true, host = " box ", port = 9000, token = " ab12 "))
        repository.setSecondary(ServerConfig(enabled = true, host = "far", port = 443))
        repository.setConnectionMode(ConnectionMode.SECONDARY_ONLY)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setLiveUpdates(false)
        repository.setPollInterval(60)
        repository.setChartRange(ChartRange.ALL)
        repository.setKeepScreenOn(true)
        repository.setVolumeKeys(false)
        repository.setShowArtwork(false)
        repository.setShowChapters(false)
        repository.setAdaptiveColor(false)
        repository.setAdaptiveColorIntensity(0.1f)
        repository.setCardFold("live/transport", true)
        repository.setCardFold("live/vs10", true)
        repository.setCardFold("live/vs10", false)
        repository.setCardOrder("live", listOf("b", "a"))
        repository.setCardOrder("history", emptyList())
        repository.setCardHidden("live", "a", true)
        repository.setCardHidden("live", "b", true)
        repository.setCardHidden("live", "b", false)
        repository.setCardHidden("history", "c", true)
        repository.setTabHidden("films", true)
        repository.setTabHidden("series", true)
        repository.setTabHidden("series", false)
        repository.setShelfOrder("films", "YEAR:down")
        repository.setShelfOrder("series", "TITLE")
        repository.setShelfOrder("series", "RATING")

        val written = repository.settings.first()
        assertEquals(ServerConfig(enabled = false, useHttps = true, host = "box", port = 9000, token = "AB12"), written.primary)
        assertEquals(ServerConfig(enabled = true, host = "far", port = 443), written.secondary)
        assertEquals(ConnectionMode.SECONDARY_ONLY, written.connectionMode)
        assertEquals(ThemeMode.DARK, written.themeMode)
        assertEquals(false, written.liveUpdates)
        assertEquals(10, written.pollIntervalSeconds)
        assertEquals(ChartRange.ALL, written.chartRange)
        assertEquals(true, written.keepScreenOn)
        assertEquals(false, written.volumeKeys)
        assertEquals(false, written.showArtwork)
        assertEquals(false, written.showChapters)
        assertEquals(false, written.adaptiveColor)
        assertEquals(0.5f, written.adaptiveColorIntensity)
        assertEquals(setOf("live/transport"), written.cardFolds)
        assertEquals(mapOf("live" to listOf("b", "a")), written.cardOrders)
        assertEquals(setOf("live/a", "history/c"), written.cardHidden)
        assertEquals(setOf("films"), written.hiddenTabs)
        assertEquals(mapOf("films" to "YEAR:down", "series" to "RATING"), written.shelfOrders)

        repository.resetCards("live")
        repository.settings.first().let {
            assertEquals(emptyMap<String, List<String>>(), it.cardOrders)
            assertEquals(setOf("history/c"), it.cardHidden)
        }
        repository.setCardOrder("live", listOf("a"))
        repository.resetCards()
        repository.resetCards("live")
        repository.settings.first().let {
            assertEquals(emptyMap<String, List<String>>(), it.cardOrders)
            assertEquals(emptySet<String>(), it.cardHidden)
        }

        // Values another build may have left behind.
        store().edit {
            it[stringPreferencesKey("theme_mode")] = "SEPIA"
            it[intPreferencesKey("card_order.live")] = 3
        }
        repository.settings.first().let {
            assertEquals(ThemeMode.SYSTEM, it.themeMode)
            assertEquals(emptyMap<String, List<String>>(), it.cardOrders)
        }
    }

    @Test
    fun `the reachable address is remembered for a while`() {
        val prefs = context.getSharedPreferences("tinyppi_reachability", Context.MODE_PRIVATE)
        val memory = StoredReachability(context)
        assertNull(memory.lastReachable())

        memory.remember("http://a:1")
        assertEquals("http://a:1", memory.lastReachable())
        // The same address soon after is not written again.
        prefs.edit().putLong("last_reachable_at", 1L).commit()
        memory.remember("http://a:1")
        assertNull(memory.lastReachable())
        memory.remember("http://b:1")
        assertEquals("http://b:1", memory.lastReachable())

        prefs.edit().putLong("last_reachable_at", System.currentTimeMillis() + 60_000).commit()
        assertNull(memory.lastReachable())

        prefs.edit().putString("last_reachable_base_url", " ").commit()
        assertNull(memory.lastReachable())

        // The same address once its note is old enough is written again.
        StoredReachability::class.java.getDeclaredField("writtenAt").apply { isAccessible = true }.setLong(memory, 0L)
        prefs.edit().putLong("last_reachable_at", 1L).commit()
        memory.remember("http://b:1")
        assertEquals("http://b:1", memory.lastReachable())

        memory.forget()
        assertNull(memory.lastReachable())
        memory.remember("http://b:1")
        assertEquals("http://b:1", memory.lastReachable())
    }
}
