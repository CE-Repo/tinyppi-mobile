package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jamal2367.tinyppimobile.ui.theme.accentText

/**
 * The poster of what is playing, or a stand-in where there is none.
 *
 * Most of what a box plays has no library entry and therefore no poster, so
 * the stand-in is the normal case rather than the failure case: it is drawn in
 * the theme's own colours and holds the same space, so a title that gains a
 * poster halfway through a series does not move the layout under it.
 */
@Composable
fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            return@Box
        }

        AsyncImage(
            model = ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * One of the format logos the overlay draws, beside the title.
 *
 * Repainted in the theme's own ink rather than drawn as it arrives. These are
 * the add-on's skin graphics - white-on-transparent artwork made for an overlay
 * over a film - and white on a white screen is a logo nobody can see. Tinting
 * the shape the file describes, instead of putting a dark chip behind it, gets
 * the same file working on both themes with nothing drawn around it.
 *
 * It relies on the artwork being one colour throughout, which these are: the
 * tint replaces every colour in the picture and keeps only its shape.
 *
 * Left out entirely where the box has no logo for a format rather than drawn as
 * a broken frame: the add-on serves a fixed allowlist of its own graphics, and
 * a codec it has no picture for is simply not one of the routes.
 */
@Composable
fun FormatLogo(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (url == null) return
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return

    AsyncImage(
        model = ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.accentText),
        onError = { failed = true },
        modifier = modifier.height(LOGO_HEIGHT),
    )
}

/** How tall a format logo is drawn - the height of a line of the title beside it. */
private val LOGO_HEIGHT = 24.dp
