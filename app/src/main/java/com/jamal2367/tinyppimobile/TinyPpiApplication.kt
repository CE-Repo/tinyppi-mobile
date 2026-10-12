package com.jamal2367.tinyppimobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.jamal2367.tinyppimobile.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

class TinyPpiApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob()) + Dispatchers.Default

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // The router is what every request, every poster and the event stream
        // read the current configuration from, so it is kept in step with the
        // stored settings here rather than in each screen that changes them.
        container.settingsRepository.settings
            .distinctUntilChanged()
            .onEach(container.router::update)
            .launchIn(applicationScope)
    }

    /**
     * Never called on a device - the process is simply killed - but the
     * Robolectric tests call it after every test. Without it every test's
     * application would keep listening to the settings store they all share,
     * and keep its live session reconnecting, for the rest of the run.
     */
    override fun onTerminate() {
        applicationScope.cancel()
        container.close()
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
