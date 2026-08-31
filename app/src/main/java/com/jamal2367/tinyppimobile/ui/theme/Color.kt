package com.jamal2367.tinyppimobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * What a heading, a title or a figure written in the accent is coloured.
 *
 * The tonal buttons' own text colour rather than the accent at full strength.
 * The accent is mixed to be a ground for something to be drawn on - it fills
 * the play button and the sliders - and the same colour set as type on a card
 * comes out heavier and darker than the words beside it can carry. What a
 * tonal button writes on itself is the tone that was picked to be read, so
 * every heading in the app is written in it and the VS10 buttons, the stop
 * button and the headings above them all say the same colour.
 */
val ColorScheme.accentText: Color get() = onSecondaryContainer

/**
 * The palette the app falls back to when the wallpaper cannot supply one.
 *
 * Built around the light blue the add-on's own dashboard accents with
 * (`--accent: #4fc3f7`) over the near-black it paints on, so the phone and the
 * page a browser opens on the same box read as one thing.
 */

// --- light ---
val LightPrimary = Color(0xFF00658F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFC7E7FF)
val LightOnPrimaryContainer = Color(0xFF001E2E)

val LightSecondary = Color(0xFF4E616D)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD1E5F4)
val LightOnSecondaryContainer = Color(0xFF091E28)

val LightTertiary = Color(0xFF615A7C)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE7DEFF)
val LightOnTertiaryContainer = Color(0xFF1D1736)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFAFCFE)
val LightOnBackground = Color(0xFF181C20)
val LightSurface = Color(0xFFFAFCFE)
val LightOnSurface = Color(0xFF181C20)
val LightSurfaceVariant = Color(0xFFDCE3E9)
val LightOnSurfaceVariant = Color(0xFF40484D)
val LightOutline = Color(0xFF70787D)
val LightOutlineVariant = Color(0xFFC0C7CD)

// --- dark ---
val DarkPrimary = Color(0xFF87CEFF)
val DarkOnPrimary = Color(0xFF00344C)
val DarkPrimaryContainer = Color(0xFF004C6D)
val DarkOnPrimaryContainer = Color(0xFFC7E7FF)

val DarkSecondary = Color(0xFFB5C9D7)
val DarkOnSecondary = Color(0xFF20333E)
val DarkSecondaryContainer = Color(0xFF364955)
val DarkOnSecondaryContainer = Color(0xFFD1E5F4)

val DarkTertiary = Color(0xFFCBC1E9)
val DarkOnTertiary = Color(0xFF322C4C)
val DarkTertiaryContainer = Color(0xFF494263)
val DarkOnTertiaryContainer = Color(0xFFE7DEFF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0F1418)
val DarkOnBackground = Color(0xFFDFE3E7)
val DarkSurface = Color(0xFF0F1418)
val DarkOnSurface = Color(0xFFDFE3E7)
val DarkSurfaceVariant = Color(0xFF40484D)
val DarkOnSurfaceVariant = Color(0xFFC0C7CD)
val DarkOutline = Color(0xFF8A9297)
val DarkOutlineVariant = Color(0xFF40484D)

/**
 * The accents the format badges use.
 *
 * A colour per grade, so a glance at the top of the screen says what is playing
 * without reading a word of it: violet for Dolby Vision, red for HDR10+, blue
 * for HDR10, amber for HLG, and a grey that stays out of the way for SDR.
 *
 * Each is a deep ground under a light face rather than a pair drawn from the
 * theme, because a badge sits over a poster: whatever the wallpaper or the time
 * of day has done to the rest of the screen, it has to hold up over a bright
 * frame and a dark one alike.
 */
val BadgeDolbyVision = Color(0xFF1B1035) to Color(0xFFD9C7FF)
val BadgeHdr10Plus = Color(0xFF3A0808) to Color(0xFFFFB3AC)
val BadgeHdr10 = Color(0xFF07203D) to Color(0xFFA8CDFF)
val BadgeHlg = Color(0xFF2E1B00) to Color(0xFFFFC98A)
val BadgeSdr = Color(0xFF1E2226) to Color(0xFFC5CBD2)

/**
 * The accent a conversion wears.
 *
 * Green, and used nowhere else: a picture that is being converted on the way
 * out is the one thing on this screen that is not simply a reading of the file,
 * and it should not have to be read to be noticed.
 */
val BadgeConverted = Color(0xFF07271A) to Color(0xFF8FEEBC)

/**
 * What the connection line is painted in.
 *
 * The three states the add-on's own dashboard paints, in the same order:
 * live, waiting, gone.
 */
val StatusLive = Color(0xFF34D399)
val StatusWaiting = Color(0xFFFBBF24)
val StatusDown = Color(0xFFF87171)

/**
 * The two lines of the luminance chart.
 *
 * The peak in the accent the whole app is built on, the average in a quieter
 * violet behind it - a pair that stays apart for the colour-blind as well,
 * since one is far lighter than the other rather than merely a different hue.
 */
val ChartPeak = Color(0xFF4FC3F7)
val ChartAverage = Color(0xFF9B8AFB)

/** What a warning event is marked with, against the switches that are not one. */
val EventWarning = Color(0xFFFBBF24)
val EventSwitch = Color(0xFF4FC3F7)
