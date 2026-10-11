package com.jamal2367.tinyppimobile.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The live view model as the screens get it: out of the application's graph. */
@RunWith(RobolectricTestRunner::class)
class LiveViewModelFactoryTest {

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun `the factory builds it on the app's graph and words its failures`() {
        Robo.clearSettings(app)
        val extras = MutableCreationExtras().apply { this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = app }
        val vm = LiveViewModel.Factory.create(LiveViewModel::class.java, extras)
        vm.reconnect()
        vm.playPause()
        Robo.until { vm.message.value != null }
        assertEquals(app.getString(R.string.error_not_configured), vm.message.value)
        vm.consumeMessage()
    }
}
