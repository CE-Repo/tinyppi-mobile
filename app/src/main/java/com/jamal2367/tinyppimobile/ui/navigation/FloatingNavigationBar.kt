@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset

/**
 * How much of the foot of the screen the floating bar sits over.
 *
 * The bar is drawn over the screens rather than beside them, so the lists run
 * on underneath it and show through. What they have to do in return is leave
 * this much room after their last item, or the last card could never be
 * scrolled out from under the bar. Nothing where the rail is showing instead.
 *
 * It follows the bar out and back: while the bar is away the lists keep only
 * the room the system's gesture bar needs, so the end of a shelf is not left
 * standing over an empty strip where the bar used to be.
 */
val LocalBottomBarSpace = staticCompositionLocalOf<State<Dp>> { NoBarSpace }

private val NoBarSpace: State<Dp> = mutableStateOf(0.dp)

/**
 * A list's content padding with the bar's room added to its foot.
 *
 * The room is read when the list is measured, not when it is composed. It
 * animates every time the bar comes or goes - which is every tab change - and
 * read in composition that was every screen recomposing on every frame of it,
 * the whole of the app at once, in the middle of a swipe.
 */
@Composable
fun barAwarePadding(horizontal: Dp, top: Dp = 0.dp, bottom: Dp): PaddingValues =
    BarAwarePadding(horizontal, top, bottom, LocalBottomBarSpace.current)

@Stable
private class BarAwarePadding(
    private val horizontal: Dp,
    private val top: Dp,
    private val bottom: Dp,
    private val bar: State<Dp>,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = horizontal
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = horizontal
    override fun calculateTopPadding(): Dp = top
    override fun calculateBottomPadding(): Dp = bottom + bar.value

    override fun equals(other: Any?): Boolean = other is BarAwarePadding &&
        other.horizontal == horizontal && other.top == top && other.bottom == bottom && other.bar === bar

    override fun hashCode(): Int = ((horizontal.hashCode() * 31 + top.hashCode()) * 31 + bottom.hashCode()) * 31 +
        System.identityHashCode(bar)
}

/**
 * Lifts what it is put on - a snackbar - clear of the bar.
 *
 * In layout rather than as a padding for the reason [barAwarePadding] gives:
 * the room moves on every frame of a tab change, and only this should move
 * with it.
 */
@Composable
fun Modifier.aboveBottomBar(): Modifier {
    val space = LocalBottomBarSpace.current
    return layout { measurable, constraints ->
        val lift = space.value.roundToPx().coerceAtLeast(0)
        val placeable = measurable.measure(constraints.offset(vertical = -lift))
        layout(placeable.width, placeable.height + lift) { placeable.place(0, 0) }
    }
}

/**
 * Whether the bar is showing, and the ear it listens to the lists with.
 *
 * Handed to the shell's `nestedScroll`, which every list on every screen
 * reports its scrolling through. A list moving on towards its end is somebody
 * reading, and the bar steps out of the way; moving back towards its top is
 * somebody going back, and the bar comes back with them.
 *
 * What was actually scrolled is what counts, not what was asked for: a finger
 * dragging at the end of a list that has nowhere left to go moves nothing, and
 * should not flick the bar in and out.
 */
class BarVisibility : NestedScrollConnection {
    var visible by mutableStateOf(true)

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        when {
            consumed.y < -SCROLL_SLOP -> visible = false
            consumed.y > SCROLL_SLOP -> visible = true
        }
        return Offset.Zero
    }
}

@Composable
fun rememberBarVisibility(): BarVisibility = remember { BarVisibility() }

/**
 * The bar the tabs are switched with: a pill floating over the foot of the
 * screen instead of a strip across it.
 *
 * The screens run on underneath it and show through, blurred - the posters of
 * a shelf are still there under the bar, just out of focus - so the bar costs
 * the screen a pill's worth of picture rather than a whole strip of it.
 *
 * Icons only. The open tab is a wider pill in the accent, which says where
 * the reader is without a line of type; the names are there for a screen
 * reader, not for the eye.
 */
@Composable
fun FloatingNavigationBar(
    destinations: List<TopLevelDestination>,
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    hazeState: HazeState,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // Moved off the foot of the screen rather than taken out of it. A bar that
    // leaves the composition takes its glass with it half-way through being
    // drawn, and the blur behind it is laid out from where the bar is - so it
    // is slid by its layout position, which the blur follows, and never
    // removed.
    val hidden by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bar-hidden",
    )
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = BAR_MARGIN, end = BAR_MARGIN, bottom = BAR_BOTTOM)
            .offset {
                val away = (BAR_HEIGHT + BAR_BOTTOM + navigationBar + BAR_SHADOW_ROOM).roundToPx()
                IntOffset(0, (away * hidden).roundToInt())
            },
    ) {
        BoxWithConstraints(
            // The full width, so the pill is centred across the screen rather
            // than set down at its left edge.
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // The icons share whatever the open tab's pill leaves over, up to
            // the width of a finger each: on a narrow phone with every tab
            // showing they close up rather than push the bar off the screen.
            val others = (destinations.size - 1).coerceAtLeast(1)
            val itemWidth = ((maxWidth - BAR_PADDING * 2 - SELECTED_WIDTH) / others)
                .coerceIn(ITEM_MIN, ITEM_MAX)

            val colors = MaterialTheme.colorScheme
            val glass = remember(colors.surface, colors.surfaceContainerHigh) {
                HazeBlurStyle {
                    blurRadius(BLUR_RADIUS)
                    backgroundColor(colors.surface)
                    colorEffects(listOf(HazeColorEffect.tint(colors.surfaceContainerHigh.copy(alpha = GLASS_TINT))))
                    // Below Android 12 there is nothing to blur with, and the
                    // bar has to hold the icons up on its own.
                    fallbackColorEffect(HazeColorEffect.tint(colors.surfaceContainerHigh.copy(alpha = FALLBACK_TINT)))
                    noiseFactor(GLASS_NOISE)
                }
            }

            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .hazeBlur(input = HazeInput.Sources(hazeState), style = glass)
                    .border(1.dp, colors.primary.copy(alpha = EDGE_ALPHA), PillShape)
                    .height(BAR_HEIGHT)
                    .padding(horizontal = BAR_PADDING)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    BarItem(
                        destination = destination,
                        selected = destination == selected,
                        width = if (destination == selected) SELECTED_WIDTH else itemWidth,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

/**
 * One tab: an icon, and where it is the open one, a wider pill in the accent
 * behind it.
 */
@Composable
private fun BarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(destination.tabLabelRes)
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tab-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tab-content",
    )

    // Animated rather than set, so the pill slides wide from one tab to the
    // next instead of jumping. Without the spring's overshoot: a width that
    // bounced below nothing would be a crash.
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tab-width",
    )

    Box(
        modifier = Modifier
            .height(ITEM_HEIGHT)
            .width(animatedWidth.coerceAtLeast(0.dp))
            .clip(PillShape)
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            // Nothing is written on the bar, so every tab is read out by name.
            contentDescription = label,
            tint = content,
        )
    }
}

/** How far a list has to have moved before the bar answers it, in pixels. */
private const val SCROLL_SLOP = 4f

val BAR_HEIGHT = 64.dp
private val BAR_MARGIN = 12.dp

/** How far the bar floats above the system's own gesture bar. */
val BAR_BOTTOM = 12.dp

/** A little further than the bar is tall, so nothing of its edge is left showing. */
private val BAR_SHADOW_ROOM = 8.dp
private val BAR_PADDING = 8.dp
private val ITEM_HEIGHT = 48.dp
private val ITEM_GAP = 2.dp
private val ITEM_MIN = 40.dp
private val ITEM_MAX = 52.dp
/** How wide the open tab's pill is drawn. */
private val SELECTED_WIDTH = 64.dp

private val BLUR_RADIUS = 24.dp
private const val GLASS_TINT = 0.62f
private const val FALLBACK_TINT = 0.94f
private const val GLASS_NOISE = 0.05f

/** Enough of the accent round the edge to lift the pill off a dark screen. */
private const val EDGE_ALPHA = 0.18f
