package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

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
 * On a dark chip whatever the theme is doing, because these are the add-on's
 * own skin graphics: white-on-transparent artwork drawn for an overlay over a
 * film, which on a light background is a logo nobody can see. The chip is what
 * makes the same file work on both themes without a second set of graphics.
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

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(LogoChipBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier.height(18.dp),
        )
    }
}

/** The ground the format logos are drawn on - the near-black the overlay uses. */
private val LogoChipBackground = Color(0xFF151A21)
