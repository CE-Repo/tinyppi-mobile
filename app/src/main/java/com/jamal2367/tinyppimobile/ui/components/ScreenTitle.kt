package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The name of a tab, written large at the head of its list.
 *
 * One style for every tab, so the screens a swipe moves between read as pages
 * of the same thing. It scrolls away with the list rather than standing over
 * it: once somebody is reading a screen they know which one it is, and the
 * room is worth more to what is under it.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 4.dp, top = 12.dp),
    )
}
