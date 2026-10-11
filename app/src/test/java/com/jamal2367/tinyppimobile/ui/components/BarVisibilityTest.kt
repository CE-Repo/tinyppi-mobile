package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.jamal2367.tinyppimobile.ui.navigation.BarVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the floating bar puts itself away, and what brings it back. */
@OptIn(ExperimentalCoroutinesApi::class)
class BarVisibilityTest {

    @Test
    fun `the bar hides when idle, not while held, and wakes on a real scroll`() = runTest {
        val bar = BarVisibility()
        backgroundScope.launch { bar.hideWhenIdle() }
        runCurrent()
        advanceTimeBy(3_100)
        assertFalse(bar.visible)

        bar.onPostScroll(Offset(0f, 2f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse("a nudge is not a scroll", bar.visible)
        bar.onPostScroll(Offset(10f, 0f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(bar.visible)
        bar.onPostScroll(Offset(0f, 10f), Offset.Zero, NestedScrollSource.UserInput)

        bar.touched(pressed = true)
        advanceTimeBy(3_100)
        assertTrue("a resting finger keeps it", bar.visible)
        bar.touched(pressed = false)
        advanceTimeBy(3_100)
        assertFalse(bar.visible)
    }
}
