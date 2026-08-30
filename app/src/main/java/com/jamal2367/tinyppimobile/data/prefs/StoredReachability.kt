package com.jamal2367.tinyppimobile.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.jamal2367.tinyppimobile.data.remote.ReachabilityMemory

/**
 * The note about the last address that answered, on disk.
 *
 * Deliberately not in the DataStore the rest of the settings live in. This is
 * read from an OkHttp thread while the very first request is being addressed,
 * before any flow has emitted anything; shared preferences answer that
 * synchronously, which is the whole requirement. It is one string and a
 * timestamp, so the file is read in a moment.
 *
 * The note goes stale on purpose. Outside the flat the remote address is the
 * one that works and a relaunch should go straight to it - but back home the
 * local address is the shorter way to the same box, and nothing tells the
 * app which network it is on. A few hours covers an evening out and still has
 * the next morning start at home.
 */
class StoredReachability(context: Context) : ReachabilityMemory {

    private val appContext = context.applicationContext

    /**
     * Opened on first use, never in a constructor: the container is built on
     * the main thread during startup, and the first read of a preferences file
     * is disk I/O.
     */
    private val prefs by lazy {
        appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    /** What was last written, so a request per second is not a write per second. */
    @Volatile
    private var written: String? = null

    @Volatile
    private var writtenAt: Long = 0L

    override fun lastReachable(): String? {
        val stored = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        // A clock that moved backwards - a time zone, a manual correction -
        // makes the age negative, and an age nobody can read is not a fresh note.
        val age = System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L)
        return stored.takeIf { age in 0..MAX_AGE_MS }
    }

    override fun remember(baseUrl: String) {
        val now = System.currentTimeMillis()
        // The same address again only has to be written when its note is old
        // enough to be worth keeping alive - every successful call comes
        // through here, and a scan produces a great many of them.
        if (baseUrl == written && now - writtenAt < REFRESH_AFTER_MS) return
        written = baseUrl
        writtenAt = now
        prefs.edit {
            putString(KEY_BASE_URL, baseUrl)
            putLong(KEY_AT, now)
        }
    }

    override fun forget() {
        written = null
        writtenAt = 0L
        prefs.edit { clear() }
    }

    private companion object {
        const val NAME = "tinyppi_reachability"
        const val KEY_BASE_URL = "last_reachable_base_url"
        const val KEY_AT = "last_reachable_at"

        /** How long a note is worth following before the local address is tried again. */
        const val MAX_AGE_MS = 4L * 60 * 60 * 1000

        /** How often an unchanged note is written again, to keep it from going stale mid-use. */
        const val REFRESH_AFTER_MS = 10L * 60 * 1000
    }
}
