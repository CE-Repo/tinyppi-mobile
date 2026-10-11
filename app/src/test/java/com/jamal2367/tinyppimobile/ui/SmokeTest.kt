package com.jamal2367.tinyppimobile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun title() {
        rule.setContent { ScreenTitle("Live") }
        rule.onNodeWithText("Live").assertIsDisplayed()
    }
}
