package com.jamal2367.tinyppimobile.testing

import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.SettingsRepository
import com.jamal2367.tinyppimobile.data.remote.UpdateEndpoints
import kotlinx.serialization.json.Json

/**
 * The whole app against a [FakeBox], set up before the activity starts: the
 * settings point at the box, and the update check asks the box rather than
 * GitHub.
 */
class AppBox(
    snapshot: Snapshot = Screens.film.copy(libraryRevision = 1),
    settings: suspend SettingsRepository.() -> Unit = {},
) : AutoCloseable {
    val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    val box = FakeBox()
    private val json = Json { encodeDefaults = true }

    init {
        // A phone is used by touch, and in touch mode nothing takes focus on
        // its own - left out of it, the first focusable thing anywhere in the
        // pager does, and the pager keeps that page in view.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        Robo.clearSettings(app)
        stream(snapshot)
        box.answers["/api/library"] = json.encodeToString(Library.serializer(), Library(movies = listOf(LibraryFilm(1, "Heat", 1995, added = "2026-01-01 10:00:00"))))
        box.answers["/api/series"] = json.encodeToString(SeriesLibrary.serializer(), SeriesLibrary(shows = listOf(LibraryShow(7, "Dark", 2017, unseen = 3))))
        box.answers["/api/continue"] = json.encodeToString(ContinueList.serializer(), ContinueList(items = listOf(ContinueItem("movie", 1, "Heat", resume = 10, duration = 100))))
        box.answers["/api/history"] = """{"now":5,"t":[0,1],"max":[100,200],"avg":[10,20],"events":[]}"""
        box.answers["/release"] = "{}"
        app.container.updateEndpoints = UpdateEndpoints(api = box.config.baseUrl + "/release", releases = "https://example.com/releases")
        Robo.settings(app) {
            setPrimary(box.config)
            settings()
        }
        Robo.until { app.container.router.settings.primary == box.config }
    }

    fun stream(vararg snapshots: Snapshot) {
        box.stream = snapshots.map { json.encodeToString(Snapshot.serializer(), it) }
    }

    override fun close() = box.close()
}
