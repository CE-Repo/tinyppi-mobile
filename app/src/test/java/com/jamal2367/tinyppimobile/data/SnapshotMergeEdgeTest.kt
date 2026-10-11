package com.jamal2367.tinyppimobile.data

import com.jamal2367.tinyppimobile.data.remote.SnapshotMerge
import com.jamal2367.tinyppimobile.data.remote.asSnapshotObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge against frames that are not the shape it expects: every one of
 * them leaves what the client holds alone rather than throwing it away.
 */
class SnapshotMergeEdgeTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun merge(base: String, delta: String): JsonObject = SnapshotMerge.merge(obj(base), obj(delta))

    private val groups = """[{"id":"v","rows":[{"id":"a","value":"1","detail":"x"},{"id":"b","value":"2"}]},
        {"id":"w"},"odd",{"id":"z","rows":["odd"]}]"""

    @Test
    fun `a delta without a seq keeps the old one`() {
        assertEquals(obj("""{"seq":1}"""), merge("""{"seq":1}""", "{}"))
    }

    @Test
    fun `a group patch that names no usable row changes nothing`() {
        val base = """{"groups":$groups}"""
        for (patch in listOf(
            """"x"""",
            """{}""",
            """{"rows":"x"}""",
            """{"rows":["x",[],[{"a":1}]]}""",
        )) {
            assertEquals(obj(base), merge(base, """{"groups":$patch}"""))
        }
    }

    @Test
    fun `a group patch onto no list keeps what was there`() {
        assertEquals(obj("""{"groups":"x"}"""), merge("""{"groups":"x"}""", """{"groups":{"rows":[["a","9"]]}}"""))
        assertEquals(obj("""{}"""), merge("""{}""", """{"groups":{"rows":[["a","9"]]}}"""))
    }

    @Test
    fun `a moved row with missing cells is blanked`() {
        val merged = merge("""{"groups":$groups}""", """{"groups":{"rows":[["a"],["b","5","d"]]}}""")
        val expected = obj(
            """{"groups":[{"id":"v","rows":[{"id":"a","value":"","detail":""},{"id":"b","value":"5","detail":"d"}]},
            {"id":"w"},"odd",{"id":"z","rows":["odd"]}]}""",
        )
        assertEquals(expected, merged)
    }

    @Test
    fun `rows without an id are left alone`() {
        val merged = merge("""{"groups":[{"rows":[{"value":"1"}]}]}""", """{"groups":{"rows":[["a","9"]]}}""")
        assertEquals(obj("""{"groups":[{"rows":[{"value":"1"}]}]}"""), merged)
    }

    @Test
    fun `a metadata patch that cannot be applied changes nothing`() {
        val base = """{"metadata":[{"kind":"row","value":"1"},"odd"]}"""
        for (patch in listOf(
            """"x"""",
            """{}""",
            """{"rows":"x"}""",
            """{"rows":["x",[],[{"a":1}],["x","1"],["0"],["1","9"],["7","9"]]}""",
        )) {
            assertEquals(obj(base), merge(base, """{"metadata":$patch}"""))
        }
        assertEquals(obj("""{"metadata":"x"}"""), merge("""{"metadata":"x"}""", """{"metadata":{"rows":[["0","9"]]}}"""))
    }

    @Test
    fun `a metadata patch writes values and cells`() {
        val merged = merge(
            """{"metadata":[{"value":"1"},{"cells":["1"]}]}""",
            """{"metadata":{"rows":[["0","2"],[1,["3"]]]}}""",
        )
        assertEquals(obj("""{"metadata":[{"value":"2"},{"cells":["3"]}]}"""), merged)
    }

    @Test
    fun `anything but an object reads as an empty snapshot`() {
        assertEquals(JsonObject(emptyMap()), JsonPrimitive(1).asSnapshotObject())
        assertEquals(JsonObject(emptyMap()), JsonArray(emptyList()).asSnapshotObject())
        assertEquals(obj("""{"a":1}"""), obj("""{"a":1}""").asSnapshotObject())
        assertEquals(JsonObject(emptyMap()), SnapshotMerge.empty())
    }
}
