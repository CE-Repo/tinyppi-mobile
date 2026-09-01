package com.jamal2367.tinyppimobile.util

import com.jamal2367.tinyppimobile.data.model.Frame
import com.jamal2367.tinyppimobile.data.model.InfoGroup
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
     * How the sound is described, a badge apiece.
     *
     * Read out of the Audio card rather than off the name of a picture. The
     * add-on prints the codec, the channel layout and whatever rides on top of
     * them as one row it has already parsed - `TrueHD 7.1` with `(Atmos)`
     * beside it - and that row is the same row whether or not the box allows
     * control, whether or not a conversion is running, and whatever the reader
     * has Kodi set to speak.
     *
     * The row is found by its id and not by what it is called. The labels come
     * out of Kodi's own string table and are translated; `audio.32045` is the
     * number of the string rather than the string, and does not move. The
     * card's first row answers where a box numbers them differently, which is
     * the codec row on every box seen so far.
     *
     * Ordered codec, then what rides on it, then how wide it is. The layout is
     * the one figure here rather than a name, and a number between two names
     * breaks the reading.
     */
    fun soundBadges(snapshot: Snapshot): List<String> {
        val audio = snapshot.groups.firstOrNull { it.id == AUDIO_GROUP } ?: return emptyList()
        val row = audio.rows.firstOrNull { it.id == AUDIO_CODEC_ROW }
            ?: audio.rows.firstOrNull()
            ?: return emptyList()

        val words = row.value.trim().split(' ').filter { it.isNotBlank() }
        val layout = words.lastOrNull()?.takeIf { it.matches(CHANNEL_LAYOUT) }
        val codec = words.dropLast(if (layout != null) 1 else 0)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        return (listOfNotNull(codec) + marksIn(audio) + listOfNotNull(layout)).distinct()
    }

    /**
     * What the picture carries beyond how it is graded - `IMAX` and its like.
     *
     * Every card except the sound's, so a mark is found wherever the add-on
     * prints it: the Video card and the processing card are two panels of the
     * same answer, and which of them names a release is the box's business
     * rather than this app's.
     *
     * Read out of the cards rather than off the name of a format graphic, and
     * that is the whole of why it survives a conversion. The graphic is named
     * for what is being displayed - put a Dolby Vision title through VS10 and
     * it becomes the SDR one, taking IMAX with it - while the cards describe
     * the file, and a film does not stop being the IMAX cut because the
     * picture is being converted on the way out.
     */
    fun pictureMarks(snapshot: Snapshot): List<String> = snapshot.groups
        .filterNot { it.id == AUDIO_GROUP }
        .flatMap { group -> marksIn(group) }
        .distinct()

    /**
     * The names worth a badge that appear anywhere in a card's readings.
     *
     * Looked for in what the rows say rather than in what they are called: a
     * label is translated and `IMAX` is not. Longest first, so `IMAX Enhanced`
     * is found whole rather than as an `IMAX` with a spare word after it.
     */
    private fun marksIn(group: InfoGroup): List<String> {
        val text = group.rows.joinToString(" ") { "${it.value} ${it.detail}" }

        val found = mutableListOf<String>()
        var rest = text
        MARKS.forEach { mark ->
            if (rest.contains(mark, ignoreCase = true)) {
                found += mark.spelled()
                // Taken out of the running text so a longer name already found
                // does not hand its own words to a shorter one after it.
                rest = rest.replace(mark, " ", ignoreCase = true)
            }
        }
        return found
    }

    /**
     * What the coded frame is called - `UHD`, `FHD`, `SD`.
     *
     * The frame the box reports, not the mode the television is running. Those
     * are different questions and the readings answer both: a 1080p film on a
     * 4K set displays at 3840x2160, and a badge on this card that said 4K
     * because of the television would be describing the wrong end of the wire.
     *
     * Classified by width. A scope film is stored short - a UHD release runs
     * 3840 by 1608 rather than by 2160 - so height is the figure that varies
     * with the aspect ratio and width is the one that names the format.
     *
     * Named from the same family throughout: `HD`, `FHD`, `QHD`, `UHD`. What
     * a television plays is 3840 across and that is UHD; `4K` is the cinema
     * standard at 4096 and is spelled out as such where one turns up. Calling
     * the consumer format 4K is the industry's own shorthand, and this app is
     * read by people who can see the difference from the readings below it.
     *
     * Null where the box sent no frame. It comes with the Dolby Vision L5
     * offsets, so an SDR title may carry none, and a badge is better absent
     * than guessed from something that was measuring the display.
     */
    fun resolution(frame: Frame?): String? {
        val width = frame?.takeIf { it.isUsable }?.w ?: return null
        return RESOLUTIONS.firstOrNull { (from, _) -> width >= from }?.second ?: "SD"
    }

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

/** How a channel layout is written, wherever a format graphic names one: `7.1`, `5.1`. */
private val CHANNEL_LAYOUT = Regex("""\d+\.\d+""")

/**
 * Which card the sound is described in, and which row of it.
 *
 * Ids and not titles. The titles are Kodi's own strings and come back
 * translated - the card reads Audio here and Ton on a German box - while the
 * ids are the numbers those strings are filed under and do not move.
 */
private const val AUDIO_GROUP = "audio"
private const val AUDIO_CODEC_ROW = "audio.32045"

/**
 * The names worth a badge of their own where a card mentions them.
 *
 * Longest first: `IMAX Enhanced` is one certification, and looking for `IMAX`
 * before it would find half of it and leave the rest as loose text.
 *
 * Brand names throughout, which is what makes finding them in the readings
 * safe: these are not translated, so a German box prints them the same way.
 */
private val MARKS = listOf(
    "IMAX Enhanced",
    "IMAX",
    "Dolby Atmos",
    "Atmos",
    "DTS:X",
    "DTS-X",
    "DTSX",
)

/**
 * How a name is written on a badge, where a card wrote it another way.
 *
 * A colon is awkward in a file name and in a good many string tables, so DTS:X
 * turns up spelled around it. Put back here: the badge is read by someone who
 * knows the format, and `DTSX` looks like a typo of it.
 */
private val SPELLINGS = mapOf(
    "DTSX" to "DTS:X",
    "DTS-X" to "DTS:X",
    "DOLBY ATMOS" to "Atmos",
)

private fun String.spelled(): String = SPELLINGS[uppercase()] ?: this

/**
 * The names a coded width goes by, widest first.
 *
 * The thresholds are the standard widths themselves rather than midpoints
 * between them: a release is authored at one of these, and anything wider than
 * a standard is that standard - DCI's 4096 is 4K, not something above it.
 */
private val RESOLUTIONS = listOf(
    7680 to "8K",
    // The cinema standard, which is what 4K actually names: 4096 across. Kept
    // apart from UHD rather than folded into it, because the whole reason to
    // write UHD on the badge below is that 3840 is not this.
    4096 to "DCI 4K",
    3840 to "UHD",
    2560 to "QHD",
    1920 to "FHD",
    1280 to "HD",
)
