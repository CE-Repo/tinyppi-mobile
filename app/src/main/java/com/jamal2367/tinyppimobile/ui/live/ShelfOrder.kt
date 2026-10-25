package com.jamal2367.tinyppimobile.ui.live

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.PillShape

/**
 * The orders a shelf can be put in. Title is the box's own order - Kodi's
 * sort title, articles ignored - so it is the list as it came; the others
 * run from the newest, the best and the most recently added, and the running
 * time from the shortest. A title without the value goes last either way.
 */
enum class ShelfSort(@param:StringRes val label: Int) {
    TITLE(R.string.sort_title),
    YEAR(R.string.sort_year),
    RATING(R.string.sort_rating),
    ADDED(R.string.sort_added),
    RUNTIME(R.string.sort_runtime),
}

/**
 * What a shelf is narrowed to: a genre, and for films the file's grade -
 * or one Dolby Vision profile, as `dv/8.1` - resolution and sound (see
 * [FormatNames]). Empty is everything.
 */
data class ShelfFilter(
    val genre: String = "",
    val video: String = "",
    val resolution: String = "",
    val audio: String = "",
)

/**
 * What the formats the box reads off a file are called, in the order a
 * filter offers them: the grades from the most to the least, the resolutions
 * from the largest, the sound from the lossless down. Trade names, so the
 * same in every language.
 */
object FormatNames {
    val VIDEO = linkedMapOf(
        "dv" to "Dolby Vision", "hdr10plus" to "HDR10+", "hdr10" to "HDR10",
        "hlg" to "HLG", "sdr" to "SDR",
    )
    val RESOLUTION = linkedMapOf("2160p" to "4K", "1080p" to "1080p", "720p" to "720p", "sd" to "SD")
    val AUDIO = linkedMapOf(
        "truehd" to "Dolby TrueHD", "dtshd_ma" to "DTS-HD MA", "pcm" to "PCM", "flac" to "FLAC",
        "dtshd_hra" to "DTS-HD HRA", "eac3" to "Dolby Digital Plus", "dts" to "DTS",
        "ac3" to "Dolby Digital", "aac" to "AAC", "opus" to "Opus", "vorbis" to "Vorbis",
        "mp3" to "MP3",
    )

    /** The name a format goes by, or the box's own word for one not listed. */
    fun of(names: Map<String, String>, value: String): String = names[value] ?: value

    /**
     * What a picture filter's value is called: a grade, or one Dolby Vision
     * profile under it ("dv/8.1"), written the way the playing card writes
     * a profile.
     */
    fun video(value: String): String =
        if (value.startsWith(PROFILE)) "Dolby Vision P${value.removePrefix(PROFILE)}" else of(VIDEO, value)

    /** The picture filter's value for one Dolby Vision profile. */
    const val PROFILE = "dv/"
}

/** The films that pass [filter], in [sort]'s order. */
internal fun List<LibraryFilm>.arranged(sort: ShelfSort, filter: ShelfFilter): List<LibraryFilm> =
    filter { film ->
        (filter.genre.isEmpty() || filter.genre in film.genres) &&
            (filter.video.isEmpty() || film.video == filter.video ||
                (film.video == "dv" && filter.video == FormatNames.PROFILE + film.dvProfile)) &&
            (filter.resolution.isEmpty() || film.resolution == filter.resolution) &&
            (filter.audio.isEmpty() || film.audio == filter.audio)
    }.ordered(sort, { it.year }, { it.rating }, { it.added }, { it.duration })

/** The shows of [genre] (all for none), in [sort]'s order; they have no running time. */
internal fun List<LibraryShow>.arranged(sort: ShelfSort, genre: String): List<LibraryShow> =
    filter { genre.isEmpty() || genre in it.genres }
        .ordered(sort, { it.year }, { it.rating }, { it.added }, { 0 })

private fun <T> List<T>.ordered(
    sort: ShelfSort,
    year: (T) -> Int,
    rating: (T) -> Double,
    added: (T) -> String,
    duration: (T) -> Int,
): List<T> = when (sort) {
    ShelfSort.TITLE -> this
    ShelfSort.YEAR -> sortedWith(lastWithout({ year(it) > 0 }, compareByDescending(year)))
    ShelfSort.RATING -> sortedWith(lastWithout({ rating(it) > 0 }, compareByDescending(rating)))
    ShelfSort.ADDED -> sortedWith(lastWithout({ added(it).isNotEmpty() }, compareByDescending(added)))
    ShelfSort.RUNTIME -> sortedWith(lastWithout({ duration(it) > 0 }, compareBy(duration)))
}

/** [order] among those that [has] the value, and every one that has not after them. */
private fun <T> lastWithout(has: (T) -> Boolean, order: Comparator<T>): Comparator<T> =
    compareBy<T> { !has(it) }.then { first, second ->
        if (has(first)) order.compare(first, second) else 0
    }

/** Every genre on the shelf, once, in alphabetical order. */
internal fun genresOf(genres: List<List<String>>): List<String> =
    genres.flatten().distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

/**
 * The grades on the shelf, in [FormatNames.VIDEO]'s order, and under Dolby
 * Vision its profiles - where there is more than one, as one profile alone is
 * the grade under another name: 5, 7, 7 FEL, 7 MEL, 8.1, 8.4.
 */
internal fun videoChoices(films: List<LibraryFilm>): List<String> {
    val grades = formatsOf(films.map { it.video }, FormatNames.VIDEO)
    val profiles = films.filter { it.video == "dv" && it.dvProfile.isNotEmpty() }
        .map { it.dvProfile }
        .distinct()
        .sortedWith(compareBy<String> { it.substringBefore(' ').toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
    if (profiles.size < 2) return grades
    val at = grades.indexOf("dv") + 1
    return grades.take(at) + profiles.map { FormatNames.PROFILE + it } + grades.drop(at)
}

/** The formats [names] lists that some title on the shelf has, in [names]' order. */
internal fun formatsOf(values: List<String>, names: Map<String, String>): List<String> {
    val found = values.toSet()
    return names.keys.filter { it in found }
}

/**
 * The order and the filters, as one row of chips under the search box that
 * scrolls sideways rather than piling up in lines: a shelf is for looking at
 * posters, and three lines of controls over it were a third of a phone's
 * screen spent before the first one.
 *
 * Each chip carries an icon and a short name - Genre, Picture, Resolution,
 * Sound - and takes the colour of a chosen filter once one is set, naming
 * what it is set to; the order says which it is. A filter with fewer than two
 * to choose between has nothing to narrow and is not drawn, which is also
 * what an add-on too old to send them gets. While any filter is set, a last
 * chip puts them all back.
 */
internal fun LazyListScope.shelfTools(
    key: String,
    sorts: List<ShelfSort>,
    sort: ShelfSort,
    onSort: (ShelfSort) -> Unit,
    filters: List<ShelfChoice>,
    onReset: () -> Unit,
) {
    item(key = "$key-tools") {
        val shown = filters.filter { it.values.size > 1 }
        // A card of their own, the shelf's other cards' shape and colour: the
        // controls read as one block under the search box rather than as
        // loose pills over the posters.
        Box(modifier = Modifier.padding(bottom = CardGap)) {
            CardSegment(CardPart.WHOLE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        // Out to the card's edges, so the row is seen to go on under
                        // them rather than stop short at its padding.
                        .layout { measurable, constraints ->
                            val edge = CARD_PADDING.roundToPx()
                            val wide = measurable.measure(
                                constraints.copy(minWidth = constraints.maxWidth + 2 * edge, maxWidth = constraints.maxWidth + 2 * edge),
                            )
                            layout(constraints.maxWidth, wide.height) { wide.place(-edge, 0) }
                        }
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = CARD_PADDING, vertical = 12.dp),
                ) {
                    val sortNames = sorts.map { stringResource(it.label) }
                    ChoiceChip(
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        label = stringResource(sort.label),
                        description = stringResource(R.string.shelf_sort, stringResource(sort.label)),
                        selected = false,
                        options = sortNames,
                        chosen = sorts.indexOf(sort),
                        onPick = { onSort(sorts[it]) },
                    )
                    for (filter in shown) {
                        val all = stringResource(filter.kind.all)
                        val label = if (filter.current.isEmpty()) stringResource(filter.kind.label) else filter.name(filter.current)
                        ChoiceChip(
                            icon = filter.kind.icon,
                            label = label,
                            description = if (filter.current.isEmpty()) all else label,
                            selected = filter.current.isNotEmpty(),
                            options = listOf(all) + filter.values.map(filter.name),
                            chosen = filter.values.indexOf(filter.current) + 1,
                            onPick = { filter.onPick(if (it == 0) "" else filter.values[it - 1]) },
                        )
                    }
                    if (shown.any { it.current.isNotEmpty() }) {
                        AssistChip(
                            onClick = withKeyTick(onReset),
                            label = { Text(stringResource(R.string.filter_reset), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Rounded.FilterAltOff, contentDescription = null, Modifier.size(18.dp)) },
                            shape = PillShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.height(CHIP_HEIGHT),
                        )
                    }
                }
            }
        }
    }
}

/** What a shelf can be filtered by: the chip's short name, its "all" choice and its icon. */
enum class ShelfFilterKind(
    @param:StringRes val label: Int,
    @param:StringRes val all: Int,
    val icon: ImageVector,
) {
    GENRE(R.string.filter_genre, R.string.filter_genre_all, Icons.Rounded.TheaterComedy),
    VIDEO(R.string.filter_video, R.string.filter_video_all, Icons.Rounded.HdrOn),
    RESOLUTION(R.string.filter_resolution, R.string.filter_resolution_all, Icons.Rounded.AspectRatio),
    AUDIO(R.string.filter_audio, R.string.filter_audio_all, Icons.Rounded.GraphicEq),
}

/**
 * One filter: which it is, the values the shelf offers, the one chosen (""
 * for all), what each value is called, and what picking one does.
 */
internal class ShelfChoice(
    val kind: ShelfFilterKind,
    val values: List<String>,
    val current: String,
    val name: (String) -> String = { it },
    val onPick: (String) -> Unit,
)

/**
 * One chip: an icon and what it is set to, a step up from the card it stands
 * on and with the card's hairline; a chosen filter takes the tonal colour
 * instead. A press opens the choices, the one in force
 * ticked.
 */
@Composable
private fun ChoiceChip(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    options: List<String>,
    chosen: Int,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val pick = withKeyTick(onPick)
    Box {
        FilterChip(
            selected = selected,
            onClick = withKeyTick { open = true },
            label = { Text(label, maxLines = 1) },
            leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
            shape = PillShape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier
                .height(CHIP_HEIGHT)
                .semantics { contentDescription = description },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    trailingIcon = if (index == chosen) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        pick(index)
                    },
                )
            }
        }
    }
}

/** As tall as a finger needs, and no taller: the row is a head, not a keypad. */
private val CHIP_HEIGHT = 40.dp
