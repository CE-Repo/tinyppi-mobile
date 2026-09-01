package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jamal2367.tinyppimobile.ui.theme.PillShape

/**
 * How a picture is graded, as one short line.
 *
 * The ground carries the colour and the words do not. The pill is tinted from
 * the poster like everything else on this card, which is what stops a badge
 * about a red film reading as an unrelated violet chip - but a word set in the
 * accent on a ground already made of it is a word competing with what it is
 * printed on.
 *
 * The quieter of the two greys, the one the year and the genre are set in.
 * These are what a film is rather than what it is called: the title is the
 * line that gets the full-strength ink, and a row of format names underneath
 * it in the same weight reads as five more titles.
 *
 * The [text] is the caller's, not a grade looked up here - a Dolby Vision
 * source says which profile it is and whether a second layer came with it, and
 * that is more than a grade knows about itself.
 */
@Composable
fun FormatBadge(
    text: String,
    modifier: Modifier = Modifier,
    arrowSuffix: String? = null,
) {
    val content = MaterialTheme.colorScheme.onSurfaceVariant

    // Cut to the line the words are set on rather than to a figure of its own.
    // The arrow appears only once a conversion starts, and at Material's 16dp
    // it stood taller than this app's 14sp label line - so the badge grew by
    // two points at the moment a reader was watching it to see what changed.
    val arrow = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
        if (arrowSuffix != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(arrow),
            )
            Text(
                text = arrowSuffix,
                color = content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}

/** A coloured dot, for a status line or in front of an event. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(PillShape)
            .background(color),
    )
}

/**
 * The grades the add-on names, and what each is called on screen.
 *
 * The labels are not translated: they are the names of formats, and Dolby
 * Vision is Dolby Vision in every language the overlay speaks. The add-on
 * leaves its own mode labels untranslated for the same reason.
 */
enum class HdrGrade(val label: String) {
    DOLBY_VISION("Dolby Vision"),
    HDR10_PLUS("HDR10+"),
    HDR10("HDR10"),
    HLG("HLG"),
    SDR("SDR");

    companion object {
        /**
         * Read a grade off whatever the add-on called it.
         *
         * HDR10+ is checked before HDR10 and spelled both ways: the Home
         * window carries `hdr10plus` - Kodi's boolean parser reads a `+` as an
         * AND - while the logo maps spell it with the plus. Either arrives
         * here, and both mean the same grade.
         */
        fun of(token: String): HdrGrade {
            val key = token.trim().lowercase()
            return when {
                key.isEmpty() || key == "sdr" -> SDR
                key.contains("dolby") || key.contains("dv") -> DOLBY_VISION
                key.contains("hdr10plus") || key.contains("hdr10+") -> HDR10_PLUS
                key.contains("hlg") -> HLG
                key.contains("hdr") -> HDR10
                else -> SDR
            }
        }
    }
}
