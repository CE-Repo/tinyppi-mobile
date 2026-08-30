package com.jamal2367.tinyppimobile.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Folding a delta frame onto the snapshot it was measured against.
 *
 * The add-on sends one whole snapshot when a stream opens and only what moved
 * after that. A whole one is tens of kilobytes and most of it stands still for
 * two hours; a delta is a few dozen bytes, which on a phone is the difference
 * between a connection that costs nothing and one that costs a battery.
 *
 * This is the same fold the add-on's own dashboard does (`merge` in
 * `resources/web/js/core.js`), on the JSON rather than on the model: a delta
 * names keys, and a model that has already turned an absent key into a default
 * can no longer tell "unchanged" from "gone".
 */
internal object SnapshotMerge {

    private const val KEY_SEQ = "seq"
    private const val KEY_SET = "set"
    private const val KEY_DELETE = "del"
    private const val KEY_GROUPS = "groups"
    private const val KEY_METADATA = "metadata"
    private const val KEY_ROWS = "rows"

    /**
     * [base] with [delta] applied.
     *
     * The two long lists are patched row by row where the delta carries a
     * patch and replaced outright where it carries a list - the add-on sends
     * the whole thing whenever the *shape* changed, because rows cannot be
     * written into a card the client has not been told about yet.
     */
    fun merge(base: JsonObject, delta: JsonObject): JsonObject {
        val merged = base.toMutableMap()

        (delta[KEY_SET] as? JsonObject)?.forEach { (key, value) -> merged[key] = value }
        (delta[KEY_DELETE] as? JsonArray)?.forEach { key ->
            merged.remove(key.jsonPrimitive.content)
        }

        delta[KEY_SEQ]?.let { merged[KEY_SEQ] = it }

        mergeGroups(base[KEY_GROUPS], delta[KEY_GROUPS])?.let { merged[KEY_GROUPS] = it }
        mergeMetadata(base[KEY_METADATA], delta[KEY_METADATA])?.let { merged[KEY_METADATA] = it }

        return JsonObject(merged)
    }

    /**
     * The card list with the moved readings written into it, or null when the
     * delta said nothing about it.
     *
     * Rows are found by id rather than by position: the add-on only sends a
     * row patch while the shape holds, but the ids are what make that check
     * and this application agree about which row is which.
     */
    private fun mergeGroups(current: JsonElement?, patch: JsonElement?): JsonElement? {
        if (patch == null) return null
        if (patch is JsonArray) return patch

        val moved = (patch as? JsonObject)?.get(KEY_ROWS)
            ?.let { it as? JsonArray }
            ?.mapNotNull { row ->
                val cells = row as? JsonArray ?: return@mapNotNull null
                val id = cells.getOrNull(0)?.asContent() ?: return@mapNotNull null
                id to cells
            }
            ?.toMap()
            .orEmpty()
        if (moved.isEmpty()) return current

        val groups = current as? JsonArray ?: return current
        return JsonArray(
            groups.map { group ->
                val fields = (group as? JsonObject)?.toMutableMap() ?: return@map group
                val rows = fields[KEY_ROWS] as? JsonArray ?: return@map group
                fields[KEY_ROWS] = JsonArray(
                    rows.map { row ->
                        val fieldsOfRow = (row as? JsonObject)?.toMutableMap() ?: return@map row
                        val change = moved[fieldsOfRow["id"]?.asContent()] ?: return@map row
                        fieldsOfRow["value"] = change.getOrNull(1) ?: JsonPrimitive("")
                        fieldsOfRow["detail"] = change.getOrNull(2) ?: JsonPrimitive("")
                        JsonObject(fieldsOfRow)
                    }
                )
                JsonObject(fields)
            }
        )
    }

    /**
     * The metadata list with the moved rows written into it.
     *
     * Patched by position rather than by name: those rows carry no ids, and
     * the shape check on the far side is what guarantees a position still
     * means the same row. A patch naming a row this client does not hold is
     * skipped rather than grown into - the next whole frame will carry it.
     */
    private fun mergeMetadata(current: JsonElement?, patch: JsonElement?): JsonElement? {
        if (patch == null) return null
        if (patch is JsonArray) return patch

        val changes = (patch as? JsonObject)?.get(KEY_ROWS) as? JsonArray ?: return current
        val rows = (current as? JsonArray)?.toMutableList() ?: return current

        for (change in changes) {
            val pair = change as? JsonArray ?: continue
            val at = pair.getOrNull(0)?.asContent()?.toIntOrNull() ?: continue
            val value = pair.getOrNull(1) ?: continue
            val row = rows.getOrNull(at) as? JsonObject ?: continue
            val fields = row.toMutableMap()
            // A trim-table row carries cells and every other row a value; the
            // patch sends whichever the row already had, so which key it
            // belongs under follows from the value's own shape.
            if (value is JsonArray) fields["cells"] = value else fields["value"] = value
            rows[at] = JsonObject(fields)
        }
        return JsonArray(rows)
    }

    /** An empty snapshot, for a client that has nothing to merge onto yet. */
    fun empty(): JsonObject = buildJsonObject { }

    private fun JsonElement.asContent(): String? = (this as? JsonPrimitive)?.content
}

/** Read as a snapshot object, or an empty one for anything that is not one. */
internal fun JsonElement.asSnapshotObject(): JsonObject =
    runCatching { jsonObject }.getOrElse { SnapshotMerge.empty() }
