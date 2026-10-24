package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.util.Formatters
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * What the title is about, what it has been given and who is in it: its
 * slogan, its plot, its ratings and its cast, as the box knows them.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it folds, comes off the screen and goes back on (see
 * CardLayout). Drawn for a title with a plot, ratings or a cast (see
 * [MediaInfo.showsPlotCard]), and left out for one with none of them - a
 * file the library does not know. The slogan goes only with a plot: on its
 * own it says too little, and above a plot it is left out where there is
 * none, which is most episodes.
 *
 * Everything in it centred. The slogan first, right over the plot so the
 * two read as one text, set like a credit's heading, and the plot in the
 * quieter colour the year and genre are set in, since it is the one
 * paragraph on the screen. Under the plot, the director and the cast as
 * plain lines, folded away until a tap on the plot lets them out. And last,
 * past a thin rule and never folded away, the ratings as a row of their
 * own: short facts about the film, taken in at a glance, and needing no
 * heading, since the houses' marks say what they are.
 */
@Composable
internal fun PlotCard(media: MediaInfo, title: String) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.hasPlot) {
            // The card starts folded: the plot cut to a few lines, and the
            // credits hanging on its end hidden under it. A tap on it lets
            // all of it out, and another folds it again. It folds wherever
            // there is more than those lines to show - a plot that runs past
            // them, or credits under it - and a new plot starts folded again.
            // To a screen reader it is a button that says what a tap does.
            //
            // Folded again for every new title - [title] says which this is -
            // and every new plot. What was let out is kept with them: a card
            // that comes back after the playback ended is handed its last
            // state whatever plays now, and only the same title may find it
            // open. The plot goes in as its hash, not as the whole text.
            val shown = "$title\n${media.plot.hashCode()}"
            var openFor by rememberSaveable { mutableStateOf<String?>(null) }
            val open = openFor == shown
            // Whether the plot runs past its lines is kept as long as what
            // was let out: it is only measured folded, so a card brought
            // back open - turned, or scrolled away and back - would
            // otherwise forget that it folds and could not be folded again.
            var cut by rememberSaveable(title, media.plot) { mutableStateOf(false) }
            val folds = cut || media.hasCredits
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Only one with more to show is a button at all: a
                        // plot that fits with nothing under it reads as text.
                        if (folds) {
                            Modifier
                                .clip(FOLD_SHAPE)
                                .clickable(
                                    onClickLabel = stringResource(
                                        if (open) R.string.live_plot_less else R.string.live_plot_more,
                                    ),
                                    role = Role.Button,
                                ) { openFor = if (open) null else shown }
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The slogan right over the plot, so the two read as one
                // text: upright, set like a credit's heading, and as far
                // over it as a credit's heading is over its names.
                if (media.tagline.isNotBlank()) {
                    Text(
                        text = media.tagline.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = SLOGAN_GAP),
                    )
                }
                Text(
                    text = media.plot.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = if (open) Int.MAX_VALUE else PLOT_LINES,
                    overflow = TextOverflow.Ellipsis,
                    // Measured folded: an open plot no longer runs past its
                    // lines, but is still one that folds.
                    onTextLayout = { if (!open) cut = it.hasVisualOverflow },
                )
                if (open && media.hasCredits) Credits(media, Modifier.padding(top = 12.dp))
            }
        } else if (media.hasCredits) {
            // Without a plot, the credits on their own.
            Credits(media, Modifier.align(Alignment.CenterHorizontally))
        }
        if (media.hasRatings) {
            // A rule between them and whatever stands over them; alone in
            // the card, they need none.
            if (media.hasPlot || media.hasCredits) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            RatingRow(media)
        }
    }
}

/**
 * Who directed it and who is in it - every director over the cast's first
 * twenty - centred like everything over them. What kind of film
 * it is and who made it stand in the card above.
 */
@Composable
private fun Credits(media: MediaInfo, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        // A clear gap between the two credits, so each reads as its own.
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (media.directors.isNotEmpty()) {
            CreditText(stringResource(R.string.live_credit_director), Formatters.names(media.directors, false))
        }
        if (media.cast.isNotEmpty()) {
            // The leads alone, however many an older add-on sends.
            CreditText(
                stringResource(R.string.live_credit_cast),
                Formatters.names(media.cast.take(CAST_LIMIT), media.castMore || media.cast.size > CAST_LIMIT),
            )
        }
    }
}

@Composable
private fun CreditText(label: String, names: String) {
    Text(
        text = creditLine(label, names, MaterialTheme.colorScheme),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/**
 * A credit: what it is in bold and the text's full colour, on a line of its
 * own over the names in the quieter one the plot is set in. Heard as it
 * reads, "Cast, Timothée Chalamet, Zendaya". Built outside the composition,
 * like [heardAs]: a few lines of text have nothing to remember.
 */
private fun creditLine(label: String, names: String, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) { append(label) }
    append("\n")
    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(names) }
}

/**
 * Every rating the library holds: the age rating first - who the film is for
 * is asked before how good it is - then a score for each source, in the
 * order the add-on sends them, the reader's own and the place in the IMDb
 * Top 250.
 *
 * Each is a small shield wearing its house's mark on a plate of its house's
 * colour rather than its name, the whole shield washed and edged in it - the same marks the add-on's dashboard
 * serves: the houses' own logos where there is one, the rest drawn by
 * `tools/gen_rating_marks.py` in script.tinyppi - and
 * says in words what it shows, since a screen reader can say nothing of a
 * drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo) {
    val tall = badgeHeight()
    val colours = MaterialTheme.colorScheme
    FlowRow(
        // Every line of them centred, the last one too.
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (media.mpaa.isNotBlank()) {
            // One the add-on could place - FSK, MPA, TV Parental Guidelines,
            // BBFC - is a shield like the scores': the board's name on the
            // colour of the level, edge to edge on the left, and the level
            // beside it ("FSK" | "12"). Any other is written as the scraper
            // wrote it.
            val said = media.mpaa.trim()
            val plate = AGE_MARKS[media.age]
            if (plate != null) {
                RatingShield(
                    spoken = said,
                    named = boardOf(media.age),
                    value = media.ageLevel.trim(),
                    tall = tall,
                    ground = plate.ground,
                    hue = plate.hue,
                    inset = 0.dp,
                ) { BoardPlate(plate.drawing) }
            } else {
                RatingShield(
                    spoken = said,
                    named = said,
                    value = said,
                    tall = tall,
                    ground = colours.surfaceContainerHigh,
                    hue = colours.surfaceContainerHigh,
                )
            }
        }
        media.ratings.forEach { entry ->
            val name = sourceName(entry)
            val value = Formatters.rating(entry.rating)
            val mark = RATING_MARKS[entry.source]
            // Certified Fresh or Verified Hot: said, and a seal on the mark.
            val certificate = CERTIFICATES[entry.source]?.takeIf { entry.certified }
            val named = if (certificate != null) "$name · $certificate" else name
            RatingShield(
                spoken = "$named $value".trim(),
                named = named,
                value = value,
                tall = tall,
                ground = mark?.ground ?: colours.surfaceContainerHigh,
                hue = mark?.hue ?: colours.surfaceContainerHigh,
            ) {
                if (mark != null) DrawnMark(mark.drawing, sealed = certificate != null) else LetterMark(name.take(1))
            }
        }
        if (media.userRating > 0) {
            val value = media.userRating.toString()
            // The reader's own wears a heart on the accent, which is what
            // says it is theirs and not a house's.
            RatingShield(
                spoken = "${stringResource(R.string.live_rating_mine)} $value",
                named = stringResource(R.string.live_rating_mine),
                value = value,
                tall = tall,
                ground = colours.primary,
                hue = colours.primary,
            ) { HeartMark() }
        }
        if (media.top250 > 0) {
            RatingShield(
                spoken = stringResource(R.string.live_rating_top250, media.top250),
                named = "IMDb Top 250",
                value = "#${media.top250}",
                tall = tall,
                ground = TOP250.ground,
                hue = TOP250.hue,
            ) { DrawnMark(TOP250.drawing) }
        }
    }
}

/**
 * A house's mark, the colour of the ground it is drawn on, and the one its
 * shield is washed and edged in: the ground's, or for a house whose ground is
 * all but black - which would not show on a dark card - its mark's.
 */
private class HouseMark(val drawing: Int, val ground: Color, val hue: Color = ground)

/** The houses there is a drawing of, by the name the add-on gives them. */
private val RATING_MARKS = mapOf(
    "imdb" to HouseMark(R.drawable.rating_imdb, Color(0xFF121212), Color(0xFFF5C518)),
    "tmdb" to HouseMark(R.drawable.rating_tmdb, Color(0xFF0D253F), Color(0xFF01B4E4)),
    "trakt" to HouseMark(R.drawable.rating_trakt, Color(0xFF9F42C6)),
    "tvdb" to HouseMark(R.drawable.rating_tvdb, Color(0xFF555555)),
    "letterboxd" to HouseMark(R.drawable.rating_letterboxd, Color(0xFF202830), Color(0xFF00E054)),
    "metacritic" to HouseMark(R.drawable.rating_metacritic, Color(0xFF66CC33)),
    "metacriticuser" to HouseMark(R.drawable.rating_metacriticuser, Color(0xFF66CC33)),
    "rottentomatoes" to HouseMark(R.drawable.rating_rottentomatoes, Color(0xFFF9D320)),
    "popcorn" to HouseMark(R.drawable.rating_popcorn, Color(0xFF1E88E5)),
    "rogerebert" to HouseMark(R.drawable.rating_rogerebert, Color(0xFFC8102E)),
    "myanimelist" to HouseMark(R.drawable.rating_myanimelist, Color(0xFF2E51A2)),
    "mdblist" to HouseMark(R.drawable.rating_mdblist, Color(0xFF4284CA)),
)

private val TOP250 = HouseMark(R.drawable.rating_top250, Color(0xFFF5C518))

/** What Rotten Tomatoes calls a rating it certified: its critics' and its audience's. Untranslated. */
private val CERTIFICATES = mapOf("rottentomatoes" to "Certified Fresh", "popcorn" to "Verified Hot")

/** The board an age rating's mark is from, by its name ("fsk12" is the FSK's). */
private fun boardOf(age: String): String = when {
    age.startsWith("fsk") -> "FSK"
    age.startsWith("mpa_") -> "MPA"
    age.startsWith("tv_") -> "TV Parental Guidelines"
    else -> "BBFC"
}

/**
 * The age ratings there is a drawing of, by the name the add-on gives the
 * mark (see `age_rating` in its `web/ratings.py`): the plate of the FSK's,
 * the MPA's and the TV Parental Guidelines' - both in the FSK's colours - and
 * the BBFC's levels, each the board's name on the level's colour, which is
 * the colour the shield is washed in.
 */
private val AGE_MARKS = mapOf(
    "fsk0" to HouseMark(R.drawable.rating_fsk0, Color(0xFFFFFFFF)),
    "fsk6" to HouseMark(R.drawable.rating_fsk6, Color(0xFFFFD500)),
    "fsk12" to HouseMark(R.drawable.rating_fsk12, Color(0xFF00A651)),
    "fsk16" to HouseMark(R.drawable.rating_fsk16, Color(0xFF00A0E3)),
    "fsk18" to HouseMark(R.drawable.rating_fsk18, Color(0xFFE30613)),
    "mpa_g" to HouseMark(R.drawable.rating_mpa_g, Color(0xFF00A651)),
    "mpa_pg" to HouseMark(R.drawable.rating_mpa_pg, Color(0xFFFFD500)),
    "mpa_pg13" to HouseMark(R.drawable.rating_mpa_pg13, Color(0xFFF7941D)),
    "mpa_r" to HouseMark(R.drawable.rating_mpa_r, Color(0xFFE30613)),
    "mpa_nc17" to HouseMark(R.drawable.rating_mpa_nc17, Color(0xFF2E3192)),
    "tv_y" to HouseMark(R.drawable.rating_tv_y, Color(0xFF00A651)),
    "tv_y7" to HouseMark(R.drawable.rating_tv_y7, Color(0xFF00A651)),
    "tv_g" to HouseMark(R.drawable.rating_tv_g, Color(0xFF00A651)),
    "tv_pg" to HouseMark(R.drawable.rating_tv_pg, Color(0xFFFFD500)),
    "tv_14" to HouseMark(R.drawable.rating_tv_14, Color(0xFFF7941D)),
    "tv_ma" to HouseMark(R.drawable.rating_tv_ma, Color(0xFFE30613)),
    "bbfc_u" to HouseMark(R.drawable.rating_bbfc_u, Color(0xFF00A651)),
    "bbfc_pg" to HouseMark(R.drawable.rating_bbfc_pg, Color(0xFFFBB800)),
    "bbfc_12a" to HouseMark(R.drawable.rating_bbfc_12a, Color(0xFFF58220)),
    "bbfc_12" to HouseMark(R.drawable.rating_bbfc_12, Color(0xFFF58220)),
    "bbfc_15" to HouseMark(R.drawable.rating_bbfc_15, Color(0xFFE5007E)),
    "bbfc_18" to HouseMark(R.drawable.rating_bbfc_18, Color(0xFFE30613)),
    "bbfc_r18" to HouseMark(R.drawable.rating_bbfc_r18, Color(0xFF1D1D6B)),
)

/**
 * The house a score is from, for the sentence a screen reader hears: its
 * brand name, or - for a source no scraper names, which an NFO may carry -
 * the name the library has, capitalised.
 */
private fun sourceName(entry: SourceRating): String =
    RATING_NAMES[entry.source] ?: entry.source.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * One rating as a shield: [side] on the left, on a plate of [ground] - a
 * house's own colour, so its mark runs into it - [inset] from the plate's
 * edges, and on the right the figure in bold. The whole shield is washed in
 * [hue] - the ground's colour unless the house says otherwise - and edged in it, so a row of them reads as the houses' colours
 * before a word of it is; one with no colour of its own is washed in the
 * card's line colour. Squarish like a plaque rather than round like a button. Without a
 * figure - an age rating from an add-on too old to send its level - the
 * side stands alone.
 *
 * Heard as [spoken] and nothing else - "IMDb 8.5" rather than a bare "8.5"
 * with nothing to say what it is. A tap - or a long press - shows over it
 * for a moment whose it is, [named], for a mark the reader does not know.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingShield(
    spoken: String,
    named: String,
    value: String,
    tall: Dp,
    ground: Color,
    hue: Color,
    inset: Dp = 3.dp,
    side: (@Composable () -> Unit)? = null,
) {
    val told = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(named) } },
        state = told,
    ) {
        Shield(spoken, value, tall, ground, hue, inset, side) { scope.launch { told.show() } }
    }
}

/** The shield itself: [side] and the figure, a tap away from its words. */
@Composable
private fun Shield(
    spoken: String,
    value: String,
    tall: Dp,
    ground: Color,
    hue: Color,
    inset: Dp,
    side: (@Composable () -> Unit)?,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heardAs(spoken)
            .clickable(onClick = onTap)
            .height(tall)
            .widthIn(min = SHIELD_WIDTH)
            .clip(SHIELD)
            .border(1.dp, hue.copy(alpha = EDGE), SHIELD)
            .background(hue.copy(alpha = WASH).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (side != null) {
            // The plate set a little in from the shield's edge, so the wash
            // shows round it.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(PLATE)
                    .background(ground)
                    .padding(horizontal = inset),
                contentAlignment = Alignment.Center,
            ) { side() }
        }
        // On one line, centred in the height the shield is given: a long age
        // rating written out is cut at the edge rather than growing a second.
        if (value.isNotEmpty()) {
            Text(
                text = value,
                modifier = Modifier.padding(start = if (side != null) 4.dp else 8.dp, end = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A badge heard as [spoken] and as nothing it holds - on its own, not as
 * part of the plot it folds with. Built outside the composition, where a
 * lambda is just a lambda and not one more thing for the Compose compiler to
 * remember against its argument.
 */
private fun Modifier.heardAs(spoken: String): Modifier =
    semantics(mergeDescendants = true) {}.clearAndSetSemantics { contentDescription = spoken }

/**
 * A house's drawing, as tall as a line of the shield and as wide as it is
 * drawn; [sealed], with Rotten Tomatoes' seal on its corner - the same one
 * the dashboard draws, from `tools/gen_rating_marks.py`.
 */
@Composable
private fun DrawnMark(drawing: Int, sealed: Boolean = false) {
    val painter = painterResource(drawing)
    Box {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .height(MARK)
                .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
        )
        if (sealed) {
            Image(
                painter = painterResource(R.drawable.rating_certified),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-1).dp)
                    .size(SEAL),
            )
        }
    }
}

/**
 * An age rating board's plate: its name on the level's colour, the whole
 * height of the shield and as wide as it is drawn, so the colour runs to the
 * shield's edges as a house's ground does.
 */
@Composable
private fun BoardPlate(drawing: Int) {
    val painter = painterResource(drawing)
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
    )
}

/** A source there is no drawing of: its first letter on the plate. */
@Composable
private fun LetterMark(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .widthIn(min = 12.dp),
    )
}

/** The heart the reader's own rating wears, on the accent it is drawn on. */
@Composable
private fun HeartMark() {
    Icon(
        imageVector = Icons.Rounded.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(11.dp),
    )
}

/** How many of the cast are named, as the add-on cuts them; "…" says there are more. */
private const val CAST_LIMIT = 20

/** The air between the slogan and the plot under it. */
private val SLOGAN_GAP = 4.dp

/** The corners of the ripple a tap on the plot draws. */
private val FOLD_SHAPE = RoundedCornerShape(8.dp)

/** How many lines of a plot are shown until the reader asks for the rest. */
private const val PLOT_LINES = 5

/** How tall a mark stands on its plate, and the corners of a shield and of its plate. */
private val MARK = 16.dp
private val SEAL = 9.dp
private val SHIELD = RoundedCornerShape(7.dp)

/** How narrow a shield may be, so one with a short mark and figure is not a sliver. */
private val SHIELD_WIDTH = 32.dp
private val PLATE = RoundedCornerShape(5.dp)

/** How much of its house's colour a shield is washed in, and edged in. */
private const val WASH = 0.2f
private const val EDGE = 0.55f

/**
 * How tall every badge stands - a shield, a heart, an age rating's - so a
 * row of them is one height whatever it holds. It grows with
 * the reader's font size, and all of them with it: a shield's figures are set
 * in text and must not be cut, and a mark beside it must not then stand short.
 */
@Composable
private fun badgeHeight(): Dp = BADGE * LocalDensity.current.fontScale.coerceAtLeast(1f)

private val BADGE = 24.dp

/** A film's slogan, plot, ratings and cast, as the card draws them. */
@PreviewLightDark
@Composable
internal fun PlotCardPreview() {
    TinyPpiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                PlotCard(
                    MediaInfo(
                        tagline = "Long live the fighters.",
                        plot = "Paul joins the Fremen and walks the desert as one of them.",
                        ratings = listOf(SourceRating("imdb", 8.5, 612_345), SourceRating("tmdb", 8.2, 6_120)),
                        mpaa = "FSK 12",
                        age = "fsk12",
                        ageLevel = "12",
                        userRating = 9,
                        top250 = 27,
                        directors = listOf("Denis Villeneuve"),
                        cast = listOf("Timothée Chalamet", "Zendaya", "Rebecca Ferguson", "Javier Bardem", "Josh Brolin"),
                        castMore = true,
                    ),
                    title = "Dune: Part Two",
                )
            }
        }
    }
}
