package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.remote.SnapshotMerge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one property the delta fold has to have:
 *
 *     merge(previous, delta(previous, current)) == current
 *
 * The fixtures are not written by hand. They were produced by running the
 * add-on's own `_snapshot_delta` - lifted verbatim out of `web/server.py`, which
 * needs nothing from Kodi for that function - over pairs of realistic snapshots,
 * and each carries the `current` that came out of it. So this compares the two
 * implementations rather than comparing one implementation with an idea of what
 * it should do.
 *
 * Between them the six pairs reach every branch the add-on has: a frame that
 * only moves readings, one that patches rows by id, one that replaces the whole
 * card list because its shape changed, one that patches metadata by position,
 * playback ending - which deletes twelve keys at once - and a new title
 * starting, which brings them all back.
 */
class DeltaFixtureTest {

    @Serializable
    private data class Fixture(
        val base: JsonObject,
        val delta: JsonObject,
        val expected: JsonObject,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixtures(): List<Fixture> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(RESOURCE)) {
            "$RESOURCE is missing from the test resources"
        }
        return json.decodeFromString(stream.reader().use { it.readText() })
    }

    @Test
    fun `every delta the add-on produces folds back into the snapshot it was measured from`() {
        val fixtures = fixtures()
        assertEquals(6, fixtures.size)

        fixtures.forEachIndexed { index, fixture ->
            val merged = SnapshotMerge.merge(fixture.base, fixture.delta)
            assertEquals(
                "fixture $index did not reconstruct the snapshot it was cut from",
                fixture.expected,
                merged,
            )
        }
    }

    @Test
    fun `and every reconstruction still decodes as the model the screens read`() {
        fixtures().forEach { fixture ->
            val merged = SnapshotMerge.merge(fixture.base, fixture.delta)
            val fromMerge = json.decodeFromJsonElement(Snapshot.serializer(), merged)
            val fromWhole = json.decodeFromJsonElement(Snapshot.serializer(), fixture.expected)

            // Not merely "it parses": the model built out of a folded frame has
            // to be the same object as the one built out of the whole snapshot,
            // because a screen cannot tell which of the two it was handed.
            assertEquals(fromWhole, fromMerge)
        }
    }

    @Test
    fun `the fixtures actually exercise the branches they were cut for`() {
        // A guard on the fixtures themselves: a generator that quietly stopped
        // producing row patches would leave this suite passing while testing
        // one third of the fold.
        val deltas = fixtures().map { it.delta }

        assertTrue(
            "no fixture patches cards by row",
            deltas.any { it["groups"] is JsonObject },
        )
        assertTrue(
            "no fixture replaces the card list outright",
            deltas.any { it["groups"] != null && it["groups"] !is JsonObject },
        )
        assertTrue(
            "no fixture patches metadata by position",
            deltas.any { it["metadata"] is JsonObject },
        )
        assertTrue(
            "no fixture deletes a key",
            deltas.any { it["del"] != null },
        )
    }

    private companion object {
        const val RESOURCE = "delta_fixtures.json"
    }
}
