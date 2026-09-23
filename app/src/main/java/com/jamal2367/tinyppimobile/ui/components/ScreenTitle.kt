package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

/**
 * How a list with a [ScreenTitle] at its head is laid out: the head at the
 * top, and the cards under it centred in whatever room is left - the way the
 * live screen holds its cards, which has no title to carry down with them.
 *
 * Only while everything fits. A list longer than the screen runs from the top
 * as any list does, [space] apart, and centring has nothing to do.
 *
 * [head] is how many items the head is: the title, and on the shelves the
 * search field under it.
 */
fun centredBelowTitle(space: Dp, head: Int = 1): Arrangement.Vertical = CentredBelowHead(space, head)

private data class CentredBelowHead(override val spacing: Dp, private val head: Int) : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val gap = spacing.roundToPx()
        var y = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = y
            y += size + gap
        }
        if (sizes.size <= head) return

        // What is left under the head, and the cards set in the middle of it.
        val used = if (sizes.isEmpty()) 0 else y - gap
        val free = totalSize - used
        if (free <= 0) return
        for (index in head until sizes.size) outPositions[index] += free / 2
    }
}
