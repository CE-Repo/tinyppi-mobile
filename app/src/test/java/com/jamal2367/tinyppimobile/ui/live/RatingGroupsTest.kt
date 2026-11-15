package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Award
import com.jamal2367.tinyppimobile.data.model.AwardsTotal
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.testing.Screens.show
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp")
class RatingGroupsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `ratings and awards form separate groups and empty sections disappear`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val ratings = context.getString(R.string.details_ratings)
        val awards = context.getString(R.string.details_awards)
        val hint = context.getString(R.string.details_award_hint)
        var media by mutableStateOf(MediaInfo())
        rule.show { RatingGroups(media, AwardsTotal()) }
        rule.onNodeWithText(ratings).assertDoesNotExist()
        rule.onNodeWithText(awards).assertDoesNotExist()
        for (onlyRating in listOf(
            MediaInfo(ratings = listOf(SourceRating("imdb", 8.5))),
            MediaInfo(mpaa = "FSK 12"),
            MediaInfo(userRating = 9),
            MediaInfo(top250 = 27),
        )) {
            media = onlyRating
            rule.waitForIdle()
            rule.onNodeWithText(ratings).assertExists()
            rule.onNodeWithText(awards).assertDoesNotExist()
        }
        media = MediaInfo(awards = listOf(Award("unknown", listOf("Film"), 0)))
        rule.waitForIdle()
        rule.onNodeWithText(ratings).assertDoesNotExist()
        rule.onNodeWithText(awards).assertDoesNotExist()
        val oscar = Award("oscar", listOf("Film"), 0)
        media = MediaInfo(awards = listOf(oscar))
        rule.waitForIdle()
        rule.onNodeWithText(ratings).assertDoesNotExist()
        rule.onNodeWithText(awards).assertExists()
        rule.onNodeWithText(hint).assertDoesNotExist()
        media = MediaInfo(
            ratings = listOf(SourceRating("imdb", 8.5)),
            awards = listOf(oscar, Award("globe", emptyList(), 2)),
        )
        rule.waitForIdle()
        rule.onNodeWithText(ratings).assertExists()
        rule.onNodeWithText(awards).assertExists()
        rule.onNodeWithText(hint).assertExists()
        val ratingBottom = rule.onNodeWithContentDescription("IMDb 8.5").fetchSemanticsNode().boundsInRoot.bottom
        val awardTop = rule.onNodeWithText(awards).fetchSemanticsNode().boundsInRoot.top
        assertTrue("awards follow ratings", awardTop > ratingBottom)
        val ratingLeft = rule.onNodeWithContentDescription("IMDb 8.5").fetchSemanticsNode().boundsInRoot.left
        val ratingHeadingLeft = rule.onNodeWithText(ratings).fetchSemanticsNode().boundsInRoot.left
        assertEquals("ratings align with heading", ratingHeadingLeft, ratingLeft, 0.5f)
        val oscarLabel = "Oscar " + context.getString(R.string.live_award_won, 1)
        val awardLeft = rule.onNodeWithContentDescription(oscarLabel).fetchSemanticsNode().boundsInRoot.left
        val awardHeadingLeft = rule.onNodeWithText(awards).fetchSemanticsNode().boundsInRoot.left
        assertEquals("awards align with heading", awardHeadingLeft, awardLeft, 0.5f)
        media = MediaInfo()
        rule.waitForIdle()
        rule.onNodeWithText(ratings).assertDoesNotExist()
        rule.onNodeWithText(awards).assertDoesNotExist()
        rule.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun `every award counted stands under the shields or alone under the heading`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val awards = context.getString(R.string.details_awards)
        val hint = context.getString(R.string.details_award_hint)
        fun said(won: Int, nominated: Int): String = context.getString(
            R.string.details_awards_total,
            listOfNotNull(
                if (won > 0) context.resources.getQuantityString(R.plurals.details_awards_wins, won, won) else null,
                if (nominated > 0) {
                    context.resources.getQuantityString(R.plurals.details_awards_nominations, nominated, nominated)
                } else {
                    null
                },
            ).joinToString(" & "),
        )
        var media by mutableStateOf(MediaInfo(awards = listOf(Award("globe", emptyList(), 2))))
        var total by mutableStateOf(AwardsTotal(45, 80))
        rule.show { RatingGroups(media, total) }
        assertEquals("45 wins & 80 nominations in total", said(45, 80))
        // Between the shields and the hint.
        val globe = "Golden Globe " + context.getString(R.string.live_award_nominated, 2)
        val shield = rule.onNodeWithContentDescription(globe).fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithText(said(45, 80)).fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithText(hint).fetchSemanticsNode().boundsInRoot
        assertTrue("the count follows the shields", line.top >= shield.bottom)
        assertTrue("the hint follows the count", note.top >= line.bottom)
        // No big award among them: the count alone, under the heading.
        media = MediaInfo()
        total = AwardsTotal(4, 7)
        rule.waitForIdle()
        rule.onNodeWithText(awards).assertExists()
        rule.onNodeWithText(said(4, 7)).assertExists()
        rule.onNodeWithText(hint).assertDoesNotExist()
        for ((won, nominated, words) in listOf(
            Triple(1, 1, "1 win & 1 nomination in total"),
            Triple(3, 0, "3 wins in total"),
            Triple(0, 2, "2 nominations in total"),
        )) {
            total = AwardsTotal(won, nominated)
            rule.waitForIdle()
            assertEquals(words, said(won, nominated))
            rule.onNodeWithText(words).assertExists()
        }
        total = AwardsTotal()
        rule.waitForIdle()
        rule.onNodeWithText(awards).assertDoesNotExist()
    }
}
