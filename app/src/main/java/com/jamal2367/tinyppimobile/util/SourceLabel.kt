package com.jamal2367.tinyppimobile.util

import com.jamal2367.tinyppimobile.data.model.Snapshot

/**
 * What the source badge says beyond the name of the grade.
 *
 * Dolby Vision is not one thing: a profile 7 file carries a second video layer
 * that the box has to have handled, a profile 5 or 8 file does not, and which
 * of those is playing is the first question anyone with a Dolby Vision library
 * asks. The badge says it - `DV P7.6 EL` - so the answer is on the live screen
 * instead of three rows down a list on another tab.
 *
 * Read out of the printed readings rather than off a field of its own, because
 * the snapshot has no field of its own for it: the profile arrives as one of
 * the rows the overlay prints, under whatever name the box gives it.
 */
object SourceLabel {

    /**
     * The profile and layer of a Dolby Vision source - `P7.6 FEL`, `P8.1` - or
     * null where the box printed nothing to read it from.
     *
     * The profile is looked for by name across both lists of readings, and the
     * first number in that row's value is taken. Nothing is invented where it
     * is not found: the badge then says `DV` and no more, which is what the app
     * knew before it went looking.
     *
     * The layer is named the way the box names it. A profile 7 file carries
     * either a full enhancement layer or a minimal one, and which of the two is
     * the difference between a second video stream's worth of picture data and
     * a placeholder - so where the box has said `FEL` or `MEL`, the badge says
     * that word rather than flattening both to `EL`.
     */
    fun dolbyVisionSuffix(snapshot: Snapshot): String? {
        val readings = snapshot.groups.flatMap { group -> group.rows }
            .map { row -> row.label to row.value } +
            snapshot.metadata.map { row -> row.name to row.value }

        val profile = readings.firstNotNullOfOrNull { (name, value) ->
            if (name.contains(PROFILE, ignoreCase = true)) numberIn(value) else null
        }

        val layer = readings.firstNotNullOfOrNull { (name, value) -> layerIn("$name $value") }
            ?: genericLayer(readings, profile)

        return when {
            profile != null && layer != null -> "P$profile $layer"
            profile != null -> "P$profile"
            layer != null -> layer
            else -> null
        }
    }

    /**
     * `FEL` or `MEL` wherever the box wrote it, as it wrote it.
     *
     * Matched as a word rather than as letters, so a row that happens to end in
     * those three does not turn into a layer.
     */
    private fun layerIn(text: String): String? =
        LAYER.find(text)?.value?.uppercase()

    /**
     * `EL`, for a file that has one without the box saying which kind.
     *
     * Either something said so in as many words, or the profile did: a profile
     * 7 file is two layers by definition, and a profile 4 file was as well.
     */
    private fun genericLayer(readings: List<Pair<String, String>>, profile: String?): String? {
        val enhanced = readings.any { (name, value) -> saysEnhancementLayer(name, value) } ||
            profile?.firstOrNull() in DUAL_LAYER_PROFILES

        return if (enhanced) "EL" else null
    }

    /**
     * Whether a reading says a second video layer came with the file.
     *
     * Two shapes, because two are in use: a composition written out as
     * `BL+EL+RPU`, and a row that asks the question by name and answers it yes.
     */
    private fun saysEnhancementLayer(name: String, value: String): Boolean {
        if (value.replace(" ", "").contains("+EL", ignoreCase = true)) return true

        val asks = name.contains(ENHANCEMENT, ignoreCase = true) ||
            name.split(' ', '.', '(', ')').any { it.equals("el", ignoreCase = true) }

        return asks && value.trim().lowercase() in AFFIRMATIVE
    }

    /** The first number in a reading: `Profile 7.6` and `7.6` both give `7.6`. */
    private fun numberIn(value: String): String? =
        NUMBER.find(value)?.value?.takeIf { it.isNotBlank() }

    private val NUMBER = Regex("""\d+(\.\d+)?""")

    /** The two kinds of enhancement layer, as words rather than as letters. */
    private val LAYER = Regex("""\b[FM]EL\b""", RegexOption.IGNORE_CASE)

    private const val PROFILE = "profile"
    private const val ENHANCEMENT = "enhancement"

    private val DUAL_LAYER_PROFILES = setOf('4', '7')

    private val AFFIRMATIVE = setOf("yes", "true", "1", "present", "ja", "on")
}
