package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Award
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardScreen
import com.jamal2367.tinyppimobile.ui.components.SectionHeading
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.util.Formatters
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The overview: a card that is its heading and nothing else, and a key - a
 * tap opens the playing title's details page (see [DetailsDialog]), where
 * what it is about, who is in it and how it is rated stand.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it moves and comes off the screen (see CardLayout): a long
 * press on it opens the arranging, as one on a heading does. Drawn for a title
 * the library holds (see [PlayingItem.opensDetails]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlotCard(title: String, onOpen: () -> Unit) {
    val screen = LocalCardScreen.current
    val layout = LocalCardLayout.current
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.medium
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.title_details),
                onClick = withKeyTick(onOpen),
                onLongClickLabel = stringResource(R.string.cards_edit),
                onLongClick = screen?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        layout.edit(it)
                    }
                },
            ),
    ) {
        // The heading a card wears, with an arrow where a fold's chevron
        // would be; its own presses are the card's, so it is drawn as on no
        // screen that could be arranged.
        Box(modifier = Modifier.padding(16.dp)) {
            CompositionLocalProvider(LocalCardScreen provides null) {
                SectionHeading(
                    title = title,
                    expanded = false,
                    onToggle = null,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
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
internal fun RatingRow(media: MediaInfo) {
    val tall = badgeHeight()
    val colours = MaterialTheme.colorScheme
    FlowRow(
        // Every line starts at the same left edge as its heading.
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(7.dp),
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
        media.awards.forEach { award -> AwardShield(award, tall) }
    }
}

/** Separate ratings and awards; sections without shields are omitted. */
@Composable
internal fun RatingGroups(media: MediaInfo) {
    val hasRatings = media.ratings.isNotEmpty() || media.mpaa.isNotBlank() || media.userRating > 0 || media.top250 > 0
    val awards = media.awards.filter { it.award in AWARD_MARKS }
    if (!hasRatings && awards.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (hasRatings) RatingGroup(stringResource(R.string.details_ratings), media.copy(awards = emptyList()))
        if (awards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RatingGroup(stringResource(R.string.details_awards), MediaInfo(awards = awards))
                if (awards.any { it.won.isEmpty() }) Text(
                    text = stringResource(R.string.details_award_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingGroup(title: String, media: MediaInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp), fontWeight = FontWeight.Bold)
        RatingRow(media)
    }
}

/**
 * One of the big awards: a drawing of what it hands out on its own ground,
 * like a house's mark, and beside it how many it won - or, where it won
 * none, how many it was nominated for, with the mark dimmed. The coloured
 * edge stays visible for both wins and nominations. A tap shows what it won it for. One there is
 * no drawing of is left out.
 */
@Composable
private fun AwardShield(award: Award, tall: Dp) {
    val mark = AWARD_MARKS[award.award] ?: return
    val name = AWARD_NAMES.getValue(award.award)
    val won = award.won.size
    val more = if (award.nominated > 0) stringResource(R.string.live_award_nominated, award.nominated) else ""
    RatingShield(
        spoken = "$name ${if (won > 0) stringResource(R.string.live_award_won, won) else more}",
        named = if (won > 0) {
            listOf("$name: ${award.won.joinToString(", ")}", more).filter { it.isNotEmpty() }.joinToString(" · ")
        } else {
            "$name · $more"
        },
        value = (if (won > 0) won else award.nominated).toString(),
        tall = tall,
        ground = if (won > 0) mark.ground else mark.ground.copy(alpha = NOMINEE_ALPHA),
        hue = mark.hue,
    ) { DrawnMark(mark.drawing, alpha = if (won > 0) 1f else NOMINEE_ALPHA) }
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
    "tvdb" to HouseMark(R.drawable.rating_tvdb, Color(0xFF555555), Color(0xFF6CD591)),
    "letterboxd" to HouseMark(R.drawable.rating_letterboxd, Color(0xFF202830), Color(0xFF00E054)),
    "metacritic" to HouseMark(R.drawable.rating_metacritic, Color(0xFF66CC33)),
    "metacriticuser" to HouseMark(R.drawable.rating_metacriticuser, Color(0xFF66CC33)),
    "rottentomatoes" to HouseMark(R.drawable.rating_rottentomatoes, Color(0xFFF9D320)),
    "popcorn" to HouseMark(R.drawable.rating_popcorn, Color(0xFF1E88E5)),
    "rogerebert" to HouseMark(R.drawable.rating_rogerebert, Color(0xFFC8102E)),
    "myanimelist" to HouseMark(R.drawable.rating_myanimelist, Color(0xFF2E51A2), Color(0xFF7298F0)),
    "mdblist" to HouseMark(R.drawable.rating_mdblist, Color(0xFF4284CA)),
)

private val TOP250 = HouseMark(R.drawable.rating_top250, Color(0xFFF5C518))

/**
 * The big awards' drawings, by the name the add-on gives them, each on its
 * own ground; darker plates use a brighter gold for the shield's wash and
 * edge so the border remains visible.
 */
private val AWARD_MARKS = mapOf(
    "oscar" to HouseMark(R.drawable.rating_award_oscar, Color(0xFFE2B33C)),
    "globe" to HouseMark(R.drawable.rating_award_globe, Color(0xFF9C6B1E), Color(0xFFE2B33C)),
    "bafta" to HouseMark(R.drawable.rating_award_bafta, Color(0xFF141414), Color(0xFFE2B33C)),
    "emmy" to HouseMark(R.drawable.rating_award_emmy, Color(0xFF3B2A6B), Color(0xFFE2B33C)),
)

/** What the awards are called, untranslated. */
private val AWARD_NAMES = mapOf("oscar" to "Oscar", "globe" to "Golden Globe", "bafta" to "BAFTA", "emmy" to "Emmy")

/** How far the mark of an award it was only nominated for is dimmed. */
private const val NOMINEE_ALPHA = 0.6f

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
    "fsk0" to HouseMark(R.drawable.rating_fsk0, Color(0xFFFFFFFF), Color(0xFF8A8A8A)),
    "fsk6" to HouseMark(R.drawable.rating_fsk6, Color(0xFFFFD500)),
    "fsk12" to HouseMark(R.drawable.rating_fsk12, Color(0xFF00A651)),
    "fsk16" to HouseMark(R.drawable.rating_fsk16, Color(0xFF00A0E3)),
    "fsk18" to HouseMark(R.drawable.rating_fsk18, Color(0xFFE30613)),
    "mpa_g" to HouseMark(R.drawable.rating_mpa_g, Color(0xFF00A651)),
    "mpa_pg" to HouseMark(R.drawable.rating_mpa_pg, Color(0xFFFFD500)),
    "mpa_pg13" to HouseMark(R.drawable.rating_mpa_pg13, Color(0xFFF7941D)),
    "mpa_r" to HouseMark(R.drawable.rating_mpa_r, Color(0xFFE30613)),
    "mpa_nc17" to HouseMark(R.drawable.rating_mpa_nc17, Color(0xFF2E3192), Color(0xFF8A8FEC)),
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
    "bbfc_r18" to HouseMark(R.drawable.rating_bbfc_r18, Color(0xFF1D1D6B), Color(0xFF8A8FEC)),
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
    inset: Dp = 3.5.dp,
    side: (@Composable () -> Unit)? = null,
) {
    // Kept up by hand rather than for the library's moment and a half, so
    // a tap leaves time to read whose it is.
    val told = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(named) } },
        state = told,
    ) {
        Shield(spoken, value, tall, ground, hue, inset, side) {
            scope.launch {
                withTimeoutOrNull(TIP_TIME) { told.show() }
                told.dismiss()
            }
        }
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
                modifier = Modifier.padding(start = if (side != null) 4.5.dp else 8.5.dp, end = 8.5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
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
 * the dashboard draws, from `tools/gen_rating_marks.py`; [alpha] dims it.
 */
@Composable
private fun DrawnMark(drawing: Int, sealed: Boolean = false, alpha: Float = 1f) {
    val painter = painterResource(drawing)
    Box {
        Image(
            painter = painter,
            contentDescription = null,
            alpha = alpha,
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
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 4.5.dp)
            .widthIn(min = 13.dp),
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
            .padding(horizontal = 4.5.dp)
            .size(12.dp),
    )
}

/** How tall a mark stands on its plate, and the corners of a shield and of its plate. */
private val MARK = 17.5.dp
private val SEAL = 10.dp
private val SHIELD = RoundedCornerShape(7.5.dp)

/** How narrow a shield may be, so one with a short mark and figure is not a sliver. */
private val SHIELD_WIDTH = 35.dp
private val PLATE = RoundedCornerShape(5.5.dp)

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

private val BADGE = 26.dp

/** How long a shield's words stay over it after a tap, in milliseconds. */
private const val TIP_TIME = 4000L

/** The overview card, as a key. */
@PreviewLightDark
@Composable
internal fun PlotCardPreview() {
    TinyPpiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) { PlotCard(stringResource(R.string.live_plot)) {} }
        }
    }
}
