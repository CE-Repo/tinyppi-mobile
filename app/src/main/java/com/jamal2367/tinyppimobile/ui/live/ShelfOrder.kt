package com.jamal2367.tinyppimobile.ui.live

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.ui.theme.CardGap

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
 * What a shelf is narrowed to: a genre, and for films the file's grade,
 * resolution and sound (see [FormatNames]). Empty is everything.
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
}

/** The films that pass [filter], in [sort]'s order. */
internal fun List<LibraryFilm>.arranged(sort: ShelfSort, filter: ShelfFilter): List<LibraryFilm> =
    filter { film ->
        (filter.genre.isEmpty() || filter.genre in film.genres) &&
            (filter.video.isEmpty() || film.video == filter.video) &&
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

/** The formats [names] lists that some title on the shelf has, in [names]' order. */
internal fun formatsOf(values: List<String>, names: Map<String, String>): List<String> {
    val found = values.toSet()
    return names.keys.filter { it in found }
}

/**
 * The order and the filters, as a row of chips under the search box: each
 * names what it is set to and opens the choices under a press. A filter with
 * fewer than two to choose between has nothing to narrow and is not drawn,
 * which is also what an add-on too old to send them gets.
 */
internal fun LazyListScope.shelfTools(
    key: String,
    sorts: List<ShelfSort>,
    sort: ShelfSort,
    onSort: (ShelfSort) -> Unit,
    filters: List<ShelfChoice>,
) {
    item(key = "$key-tools") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = CardGap),
        ) {
            ChoiceChip(
                label = stringResource(R.string.shelf_sort, stringResource(sort.label)),
                selected = false,
                options = sorts.map { stringResource(it.label) },
                onPick = { onSort(sorts[it]) },
            )
            for (filter in filters) {
                if (filter.values.size < 2) continue
                val all = stringResource(filter.all)
                ChoiceChip(
                    label = if (filter.current.isEmpty()) all else filter.name(filter.current),
                    selected = filter.current.isNotEmpty(),
                    options = listOf(all) + filter.values.map(filter.name),
                    onPick = { filter.onPick(if (it == 0) "" else filter.values[it - 1]) },
                )
            }
        }
    }
}

/**
 * One filter: the values the shelf offers, the one chosen ("" for all), what
 * the "all" choice is called, what each value is called, and what picking one
 * does.
 */
internal class ShelfChoice(
    val values: List<String>,
    val current: String,
    @param:StringRes val all: Int,
    val name: (String) -> String = { it },
    val onPick: (String) -> Unit,
)

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val pick = withKeyTick(onPick)
    Box {
        FilterChip(
            selected = selected,
            onClick = withKeyTick { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        open = false
                        pick(index)
                    },
                )
            }
        }
    }
}
