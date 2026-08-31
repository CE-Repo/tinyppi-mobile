package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which folds the reader has moved, and how to move one.
 *
 * Held as the set of folds that are *not* where they start rather than the set
 * that are shut, because not every fold starts open: a card the app grows later
 * should arrive open - nobody has folded away a card they have never seen - and
 * the live screen's transport should arrive away, which is what it was asked to
 * do. Storing the difference from the default lets both be true, and lets a
 * default be changed later without reading every stored name backwards.
 *
 * Reached through a composition local rather than passed down, because a fold
 * is drawn on five screens with four view models between them and none of them
 * has anything else to say about folding.
 */
@Immutable
class CardFolds(
    private val moved: Set<String> = emptySet(),
    private val onChange: (id: String, moved: Boolean) -> Unit = { _, _ -> },
) {
    fun isExpanded(id: String, openByDefault: Boolean = true): Boolean =
        if (id in moved) !openByDefault else openByDefault

    fun setExpanded(id: String, expanded: Boolean, openByDefault: Boolean = true) =
        onChange(id, expanded != openByDefault)
}

/**
 * The folds in force here.
 *
 * Everything open and nothing remembered by default, which is what a preview
 * and a test want and what the app replaces at its root.
 */
val LocalCardFolds = staticCompositionLocalOf { CardFolds() }
