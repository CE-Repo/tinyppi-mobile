package com.jamal2367.tinyppimobile.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.jamal2367.tinyppimobile.BuildConfig
import com.jamal2367.tinyppimobile.data.prefs.SettingsRepository
import com.jamal2367.tinyppimobile.data.prefs.StoredReachability
import com.jamal2367.tinyppimobile.data.remote.ConnectionTester
import com.jamal2367.tinyppimobile.data.remote.FailoverInterceptor
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import com.jamal2367.tinyppimobile.data.remote.SnapshotStream
import com.jamal2367.tinyppimobile.data.remote.TinyPpiApi
import com.jamal2367.tinyppimobile.data.remote.UpdateChecker
import com.jamal2367.tinyppimobile.data.repository.LiveSession
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    /**
     * The logger goes on after whatever rewrites the request, never before it:
     * ahead of the failover interceptor every line would read
     * `http://localhost/...` - the placeholder Retrofit was built against -
     * instead of the address the attempt actually went to, which is the one
     * thing a log of a failing connection is read for.
     */
    private fun OkHttpClient.Builder.withDebugLogging(): OkHttpClient.Builder = apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
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

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val session: LiveSession by lazy {
        LiveSession(api, snapshotStream, router, json)
    }

    /**
     * The one live reading the whole app shares.
     *
     * Every screen collects this instead of connecting for itself: the add-on
     * caps concurrent streams at six and fans the same snapshot out to each of
     * them, so four screens from one phone would spend four of those six slots
     * showing the same thing. The connection is dropped a few seconds after
     * the last screen stops looking, so a backgrounded app holds none.
     */
    /** Drop the open connection and build a new one - what the refresh asks for. */
    fun reconnect() = session.restart()

    val liveState: StateFlow<LiveState> by lazy {
        session.states().stateIn(
            scope = containerScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = LiveState(),
        )
    }

    /**
     * Posters and format logos are addressed absolutely - the URL already names
     * the box and carries the token as `?token=`, which is the one place this
     * API documents that for - so they skip the failover interceptor and use
     * the plain client.
     *
     * Kept on disk because a cached picture never changes under its name: the
     * add-on hangs the artwork's own tag on the address, so the next film asks
     * a different one rather than the same one twice.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { directClient }))
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
