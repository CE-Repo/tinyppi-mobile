package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.remote.SnapshotMerge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fold that turns a stream of deltas back into whole snapshots.
 *
 * Worth testing on its own because it is the one piece of this app that can be
 * wrong without failing: a merge that drops a key leaves a screen showing a
 * reading from two minutes ago, and nothing anywhere reports an error.
 *
 * The frames below are the shapes `_snapshot_delta` in the add-on actually
 * produces, not invented ones.
 */
class SnapshotMergeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun merge(base: String, delta: String): JsonObject = SnapshotMerge.merge(
        json.parseToJsonElement(base).jsonObject,
        json.parseToJsonElement(delta).jsonObject,
    )

    @Test
    fun `set replaces the keys it names and leaves the rest alone`() {
        val merged = merge(
            base = """{"seq":1,"playing":true,"title":"Dune","time":"0:10:00"}""",
            delta = """{"seq":2,"set":{"time":"0:10:01"}}""",
        )

        assertEquals("Dune", merged["title"]?.jsonPrimitive?.content)
        assertEquals("0:10:01", merged["time"]?.jsonPrimitive?.content)
        assertEquals(2, merged["seq"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `del removes a key the new snapshot no longer carries`() {
        val merged = merge(
            base = """{"seq":1,"playing":true,"paused":true}""",
            delta = """{"seq":2,"del":["paused"]}""",
        )

        assertNull(merged["paused"])
        assertTrue(merged["playing"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `a row patch writes into the card it names and touches no other`() {
        val base = """
            {"seq":1,"groups":[
              {"id":"video","title":"Video","rows":[
                {"id":"video.1","label":"Mode","value":"3840x2160p23","detail":""},
                {"id":"video.2","label":"Depth","value":"10 bit","detail":""}
              ]}
            ]}
        """.trimIndent()
        val delta = """{"seq":2,"groups":{"rows":[["video.1","3840x2160p59","(new)"]]}}"""

        val rows = merge(base, delta)["groups"]!!.jsonArray[0].jsonObject["rows"]!!.jsonArray

        assertEquals("3840x2160p59", rows[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("(new)", rows[0].jsonObject["detail"]!!.jsonPrimitive.content)
        // The row nobody mentioned keeps both of its halves.
        assertEquals("10 bit", rows[1].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("Depth", rows[1].jsonObject["label"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a whole list replaces the cards outright`() {
        // What the add-on sends when the shape changed: a card appeared, a row
        // was renamed. Rows cannot be patched into a list the client has never
        // been told about, so the lot travels.
        val merged = merge(
            base = """{"seq":1,"groups":[{"id":"video","title":"Video","rows":[]}]}""",
            delta = """{"seq":2,"groups":[{"id":"audio","title":"Audio","rows":[]}]}""",
        )

        val groups = merged["groups"]!!.jsonArray
        assertEquals(1, groups.size)
        assertEquals("audio", groups[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `metadata is patched by position, and a value keeps the shape it had`() {
        val base = """
            {"seq":1,"metadata":[
              {"kind":"row","name":"Min PQ","value":"0"},
              {"kind":"columns","name":"Trim 100","cells":["1","2","3"]}
            ]}
        """.trimIndent()
        val delta = """{"seq":2,"metadata":{"rows":[[0,"7"],[1,["4","5","6"]]]}}"""

        val rows = merge(base, delta)["metadata"]!!.jsonArray

        assertEquals("7", rows[0].jsonObject["value"]!!.jsonPrimitive.content)
        // A list arrived, so it belongs under `cells` and not under `value`.
        assertEquals(3, rows[1].jsonObject["cells"]!!.jsonArray.size)
        assertEquals("4", rows[1].jsonObject["cells"]!!.jsonArray[0].jsonPrimitive.content)
        assertNull(rows[1].jsonObject["value"])
    }

    @Test
    fun `a patch naming a row this client does not hold is skipped`() {
        val merged = merge(
            base = """{"seq":1,"metadata":[{"kind":"row","name":"Min PQ","value":"0"}]}""",
            delta = """{"seq":2,"metadata":{"rows":[[4,"7"]]}}""",
        )

        val rows = merged["metadata"]!!.jsonArray
        assertEquals(1, rows.size)
        assertEquals("0", rows[0].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a merged frame still decodes as a snapshot`() {
        val merged = merge(
            base = """
                {"seq":1,"playing":true,"title":"Dune","hdr_type":"dolbyvision",
                 "output_type":"dolbyvision","metrics":{"l1":{"min":0,"max":1000,"avg":120}}}
            """.trimIndent(),
            delta = """{"seq":2,"set":{"output_type":"hdr10"}}""",
        )

        val snapshot = json.decodeFromJsonElement(Snapshot.serializer(), merged)

        assertEquals("Dune", snapshot.title)
        assertEquals(1000.0, snapshot.metrics.l1.max!!, 0.001)
        assertTrue(snapshot.isConverting)
    }

    @Test
    fun `an untouched snapshot is not reported as a conversion`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """{"playing":true,"hdr_type":"hdr10","output_type":"hdr10"}""",
        )
        assertFalse(snapshot.isConverting)
    }

    @Test
    fun `an empty source reads as SDR, which is what the add-on means by it`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """{"playing":true,"hdr_type":"","output_type":"sdr"}""",
        )
        assertEquals("sdr", snapshot.sourceType)
        assertFalse(snapshot.isConverting)
    }
}
