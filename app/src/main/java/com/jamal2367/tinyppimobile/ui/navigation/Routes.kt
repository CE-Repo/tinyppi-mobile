package com.jamal2367.tinyppimobile.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.jamal2367.tinyppimobile.R

object Routes {
    const val LIVE = "live"
    const val METADATA = "metadata"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * The four places the bar switches between.
 *
 * They follow the add-on's own dashboard rather than inventing a shape of
 * their own: what is playing and what can be done to it, with the overlay's
 * printed readings at the foot of the same screen; the Dolby Vision metadata
 * view; the chart and the event list; and the settings. Anyone who has used
 * the page on a browser knows where everything is.
 *
 * The readings had a tab of their own until they were folded into the live
 * screen. They are the same readings about the same film as the card at the
 * top of it, and a bar entry between the two was a journey across the app to
 * answer a question the poster had just raised.
 *
 * [labelRes] names the screen; [tabLabelRes] is what fits under an icon when
 * four of them share the width of a phone.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    @StringRes val tabLabelRes: Int = labelRes,
) {
    LIVE(Routes.LIVE, R.string.nav_live, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    METADATA(
        Routes.METADATA,
        R.string.nav_metadata,
        Icons.Filled.Tune,
        Icons.Outlined.Tune,
        // "Metadaten" under an icon a fifth of a phone wide wraps onto a
        // second line and takes the whole bar with it.
        tabLabelRes = R.string.nav_metadata_tab,
    ),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Filled.Timeline, Icons.Outlined.Timeline),
    SETTINGS(
        Routes.SETTINGS,
        R.string.nav_settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        tabLabelRes = R.string.nav_settings_tab,
    ),
}
