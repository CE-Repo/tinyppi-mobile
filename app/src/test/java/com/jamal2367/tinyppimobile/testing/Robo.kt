package com.jamal2367.tinyppimobile.testing

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jamal2367.tinyppimobile.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

/** What the Robolectric tests share: the settings store, and waiting on real threads. */
object Robo {

    /** The app's settings store itself, for clearing it between tests. */
    @Suppress("UNCHECKED_CAST")
    fun store(context: Context): DataStore<Preferences> {
        val owner = Class.forName("com.jamal2367.tinyppimobile.data.prefs.SettingsRepositoryKt")
        val getter = owner.getDeclaredMethod("getDataStore", Context::class.java).apply { isAccessible = true }
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    /** Back to a first launch: the store is shared by every test in the run. */
    fun clearSettings(context: Context) = runBlocking { store(context).edit { it.clear() } }

    /** Write settings through the app's own repository. */
    fun settings(context: Context, block: suspend SettingsRepository.() -> Unit) =
        runBlocking { SettingsRepository(context).block() }

    /** Run the main looper until [condition] holds, for work that finishes on other threads. */
    fun until(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "condition not met in ${timeoutMs}ms" }
            Thread.sleep(10)
        }
    }
}
