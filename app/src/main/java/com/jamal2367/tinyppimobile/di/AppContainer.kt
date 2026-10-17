package com.jamal2367.tinyppimobile.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.prefs.SettingsRepository
import com.jamal2367.tinyppimobile.data.prefs.StoredReachability
import com.jamal2367.tinyppimobile.data.remote.BoxTokenInterceptor
import com.jamal2367.tinyppimobile.data.remote.ConnectionTester
import com.jamal2367.tinyppimobile.data.remote.FailoverInterceptor
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import com.jamal2367.tinyppimobile.data.remote.SnapshotStream
import com.jamal2367.tinyppimobile.data.remote.TinyPpiApi
import com.jamal2367.tinyppimobile.data.remote.UpdateChecker
import com.jamal2367.tinyppimobile.data.remote.UpdateEndpoints
import com.jamal2367.tinyppimobile.data.repository.LiveSession
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * The app's single graph, built by hand.
 *
 * Small enough that a framework would be more machinery than the whole network
 * layer: everything is created once, lazily, and the view models reach it
 * through the application object.
 */
class AppContainer(private val context: Context) {

    /**
     * Lenient on purpose. The add-on grows a reading at a time - every row the
     * overlay gains is in the snapshot the moment it is published - so a build
     * of this app is always one that will meet keys it has never heard of, and
     * none of those should turn into an empty screen.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /**
     * Built with the note of what last answered, so a start away from home
     * goes to the address that works rather than waiting out the one that does
     * not.
     */
    val router: ServerRouter by lazy { ServerRouter(StoredReachability(context)) }

    /**
     * Short connect timeout on purpose: in automatic mode a local address that
     * is not on this network has to fail quickly, because the fallback to the
     * remote one waits behind it. An address that still has another one behind
     * it is cut shorter again, in the failover interceptor.
     */
    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val apiClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(FailoverInterceptor(router))
            .withDebugLogging()
            .build()
    }

    /** For everything that addresses a box itself, without the failover. */
    private val directClient: OkHttpClient by lazy {
        baseClient.newBuilder().withDebugLogging().build()
    }

    /**
     * The pictures' client: direct like the one above, with the token put on
     * as a header for whichever box the address names - the add-on reads it
     * from nowhere else.
     */
    private val artClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(BoxTokenInterceptor(router))
            .withDebugLogging()
            .build()
    }

    /**
     * The event stream gets its own client: it addresses a box directly rather
     * than through the failover interceptor, and it must never time out while
     * reading - an idle stream sends nothing but a comment every fifteen
     * seconds, and a read timeout would cut a paused film off the air.
     */
    private val streamClient: OkHttpClient by lazy {
        directClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val api: TinyPpiApi by lazy {
        Retrofit.Builder()
            // Never contacted: every request is retargeted at a configured box
            // before it leaves. Retrofit only insists on having one.
            .baseUrl("http://localhost/")
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TinyPpiApi::class.java)
    }

    val repository: PlayerRepository by lazy { PlayerRepository(api, json) }

    private val snapshotStream: SnapshotStream by lazy { SnapshotStream(streamClient, json) }

    /** Checks one address in isolation, for the button in the settings. */
    val connectionTester: ConnectionTester by lazy { ConnectionTester(directClient, json) }

    /**
     * Asks GitHub for the newest release, once per launch.
     *
     * On the plain client, like everything that addresses a host of its own:
     * the failover interceptor exists to retarget requests at whichever box is
     * answering, and github.com is neither of them.
     */
    val updateChecker: UpdateChecker by lazy { UpdateChecker(directClient, json) }

    /**
     * Where the update check asks, and the page a reader is sent to when the
     * answer has no page of its own. Held here rather than read at the call
     * site so the tests can point the check at a box of their own.
     */
    internal var updateEndpoints = UpdateEndpoints(
        api = context.getString(R.string.update_check_url),
        releases = context.getString(R.string.update_releases_url),
    )

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Stop everything the container started: the shared live reading above all. */
    fun close() = containerScope.cancel()

    private val session: LiveSession by lazy {
        LiveSession(api, snapshotStream, router, json)
    }

    /**
     * The one live reading the whole app shares.
     *
     * Every screen collects this instead of connecting for itself: the add-on
     * caps concurrent streams at six and fans the same snapshot out to each of
     * them, so four screens from one phone would spend four of those six slots
     * showing the same thing. The connection is dropped the moment the app
     * leaves the screen (see [setVisible]), and a few seconds after the last
     * screen stops looking, so a backgrounded app holds none.
     */
    /** Drop the open connection and build a new one - what the refresh asks for. */
    fun reconnect() = session.restart()

    /**
     * Whether the app is on screen: let go of the box's stream the moment it
     * is not, and open it again the moment it is (see [LiveSession.setVisible]).
     */
    fun setVisible(visible: Boolean) = session.setVisible(visible)

    val liveState: StateFlow<LiveState> by lazy {
        session.states().stateIn(
            scope = containerScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = LiveState(),
        )
    }

    /**
     * Posters and format logos are addressed absolutely - the URL already names
     * the box - so they skip the failover interceptor and use the art client,
     * which adds that box's token as a header. The token is not part of the
     * address, so the disk cache outlives a new token.
     *
     * Kept on disk because a cached picture never changes under its name: the
     * add-on hangs the artwork's own tag on the address, so the next film asks
     * a different one rather than the same one twice.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { artClient }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("art_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
