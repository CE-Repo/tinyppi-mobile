@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

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
 * Only the tab that is open carries its name. Six names under six icons is a
 * row of small type that has to be read; one name in the accent is where the
 * reader is, and the icons either side are where they could go.
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
            val itemWidth = ((maxWidth - BAR_PADDING * 2 - SELECTED_ALLOWANCE) / others)
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
                        width = itemWidth,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

/**
 * One tab: an icon, and where it is the open one, a pill in the accent with
 * its name beside the icon.
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

    Row(
        modifier = Modifier
            .height(ITEM_HEIGHT)
            .widthIn(min = width)
            .clip(PillShape)
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .padding(horizontal = if (selected) SELECTED_PADDING else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            // The open tab's name is written beside it; the others have only
            // the icon to be read out by.
            contentDescription = if (selected) null else label,
            tint = content,
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LABEL_MAX),
            )
        }
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
private val SELECTED_PADDING = 16.dp
private val LABEL_MAX = 96.dp

/** What the open tab's pill is budgeted at when the icons share out the rest. */
private val SELECTED_ALLOWANCE = 128.dp

private val BLUR_RADIUS = 24.dp
private const val GLASS_TINT = 0.62f
private const val FALLBACK_TINT = 0.94f
private const val GLASS_NOISE = 0.05f

/** Enough of the accent round the edge to lift the pill off a dark screen. */
private const val EDGE_ALPHA = 0.18f
