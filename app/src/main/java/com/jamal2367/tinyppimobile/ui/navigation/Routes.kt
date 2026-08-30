package com.jamal2367.tinyppimobile.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.jamal2367.tinyppimobile.R

object Routes {
    const val LIVE = "live"
    const val DETAILS = "details"
    const val METADATA = "metadata"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * The five places the bar switches between.
 *
 * They follow the add-on's own dashboard rather than inventing a shape of
 * their own: what is playing and what can be done to it; the printed readings
 * the overlay groups into cards; the Dolby Vision metadata view; the chart and
 * the event list; and the settings. Anyone who has used the page on a browser
 * knows where everything is.
 *
 * [labelRes] names the screen; [tabLabelRes] is what fits under an icon when
 * five of them share the width of a phone.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    @StringRes val tabLabelRes: Int = labelRes,
) {
    LIVE(Routes.LIVE, R.string.nav_live, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    DETAILS(Routes.DETAILS, R.string.nav_details, Icons.Filled.Assessment, Icons.Outlined.Assessment),
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
