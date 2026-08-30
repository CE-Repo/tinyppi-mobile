package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jamal2367.tinyppimobile.ui.theme.BadgeConverted
import com.jamal2367.tinyppimobile.ui.theme.BadgeDolbyVision
import com.jamal2367.tinyppimobile.ui.theme.BadgeHdr10
import com.jamal2367.tinyppimobile.ui.theme.BadgeHdr10Plus
import com.jamal2367.tinyppimobile.ui.theme.BadgeHlg
import com.jamal2367.tinyppimobile.ui.theme.BadgeSdr
import com.jamal2367.tinyppimobile.ui.theme.PillShape

/**
 * How a picture is graded, as one word in the colour of its grade.
 *
 * The tokens are the add-on's own - `dolbyvision`, `hdr10`, `hdr10plus`,
 * `hlg`, and an empty string for SDR - and both the source and the output side
 * of a snapshot are written in them, which is what lets one badge draw either.
 */
@Composable
fun FormatBadge(
    token: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    val grade = HdrGrade.of(token)
    val (container, content) = grade.colors

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (prefix != null) {
            Text(
                text = prefix,
                color = content.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
            )
        }
        Text(
            text = grade.label,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

/**
 * The badge that says a picture is not leaving the box as it arrived.
 *
 * Its own colour and used nowhere else: a conversion is the one thing on the
 * screen that is a decision rather than a reading, and it should not have to be
 * read to be noticed.
 */
@Composable
fun ConversionBadge(text: String, modifier: Modifier = Modifier) {
    val (container, content) = BadgeConverted
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
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
enum class HdrGrade(val label: String, val colors: Pair<Color, Color>) {
    DOLBY_VISION("Dolby Vision", BadgeDolbyVision),
    HDR10_PLUS("HDR10+", BadgeHdr10Plus),
    HDR10("HDR10", BadgeHdr10),
    HLG("HLG", BadgeHlg),
    SDR("SDR", BadgeSdr);

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
