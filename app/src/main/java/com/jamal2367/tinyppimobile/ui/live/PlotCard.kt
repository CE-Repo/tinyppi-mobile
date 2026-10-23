package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme

/**
 * What the title is about: its slogan and its plot, as the box's library has
 * them.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it folds, comes off the screen and goes back on (see
 * CardLayout). Left out altogether where the library has neither - a file it
 * does not know - and each half on its own where it has only that one, which
 * for most episodes is a plot without a slogan.
 *
 * The slogan in italics and in the text's full colour, as the line a poster
 * carries; the plot under it in the quieter colour the year and genre are set
 * in, since it is the one paragraph on the screen and the readings below it
 * should not have to shout over it.
 */
@Composable
internal fun PlotCard(media: MediaInfo) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.tagline.isNotBlank()) {
            Text(
                text = media.tagline.trim(),
                style = MaterialTheme.typography.titleSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (media.plot.isNotBlank()) {
            Text(
                text = media.plot.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A film's slogan and plot, as the card draws them. */
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
                    ),
                )
            }
        }
    }
}
