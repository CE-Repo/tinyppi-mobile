package com.jamal2367.tinyppimobile.data.prefs

/**
 * One box running the TinyPPI add-on that the app may talk to.
 *
 * Two of these are stored - the address inside the network and the one that
 * reaches the same box from outside - because those differ in every part that
 * matters: scheme, host, port, and often the token, when the way in from
 * outside is a reverse proxy of its own.
 */
data class ServerConfig(
    val enabled: Boolean = false,
    val useHttps: Boolean = false,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val token: String = "",
) {
    /** Whether this one is filled in far enough to be worth a request. */
    val isUsable: Boolean
        get() = enabled && host.isNotBlank() && port in 1..65535

    /** The box's root, without a trailing slash: `http://192.168.1.10:8099`. */
    val baseUrl: String
        get() = "${if (useHttps) "https" else "http"}://${host.trim().trimEnd('/')}:$port"

    /** What to call this box in a message to the user. */
    val label: String
        get() = if (host.isBlank()) "" else "${host.trim()}:$port"

    companion object {
        /**
         * The port the add-on serves on until someone changes it in Kodi.
         *
         * The add-on refuses anything below 1024 as well, since it runs
         * unprivileged (see `configured_port`).
         */
        const val DEFAULT_PORT = 8099

        /** The range the add-on itself accepts. */
        val PORT_RANGE = 1024..65535

        /**
         * How long a token is, and out of what.
         *
         * The add-on mints it from an alphabet with no I, O, 0 or 1 in it,
         * because a token is read off a television and typed on a phone.
         */
        const val TOKEN_LENGTH = 8
    }
}

/** Which of the two boxes the app is allowed to reach for. */
enum class ConnectionMode {
    /** The local one first; the remote one whenever it cannot be reached. */
    AUTO,

    /** Only ever the local address. */
    PRIMARY_ONLY,

    /** Only ever the remote address. */
    SECONDARY_ONLY,
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How far back the luminance chart looks.
 *
 * The same three the add-on's own dashboard offers, so a reader who knows one
 * knows the other.
 */
enum class ChartRange(val seconds: Int?) {
    ONE_MINUTE(60),
    TEN_MINUTES(600),

    /** Everything the box still holds - up to an hour. */
    ALL(null),
}

/** Everything the app remembers between launches. */
data class AppSettings(
    val primary: ServerConfig = ServerConfig(enabled = true),
    val secondary: ServerConfig = ServerConfig(),
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /**
     * Whether the app holds the event stream open.
     *
     * On by default: the whole point of this app is a reading that moves with
     * the picture, and the stream is what makes that cost one connection
     * instead of five requests a second. Switched off, the screens fall back
     * to asking for `/api/state` on a slow timer.
     */
    val liveUpdates: Boolean = true,
    /**
     * How often the app asks when it is not streaming.
     *
     * Only ever read in that case: with the stream open the box decides the
     * cadence, and it is five times a second.
     */
    val pollIntervalSeconds: Int = DEFAULT_POLL_SECONDS,
    val chartRange: ChartRange = ChartRange.TEN_MINUTES,
    /**
     * Whether the screen is kept awake while something is playing.
     *
     * Off by default - a phone propped against the television is one use of
     * this app and a phone in a pocket is the other, and only the first one
     * wants its battery spent on a lit screen.
     */
    val keepScreenOn: Boolean = false,
    /** Whether the poster of what is playing is fetched at all. */
    val showArtwork: Boolean = true,
    /**
     * Whether the app paints itself in the colour of what is playing.
     *
     * On by default, and read off the poster - so it follows [showArtwork]:
     * with posters switched off there is no picture to take a colour from, and
     * fetching one purely to average it is the thing that switch turns off.
     */
    val adaptiveColor: Boolean = true,
    /**
     * Whether the live card is folded open on the transport and the tracks.
     *
     * Shut by default: what is playing is what the screen is opened for, and
     * the buttons are wanted a good deal less often than they take up room.
     * Kept here rather than in the screen because a fold reopened on every
     * launch is one the reader has to close again on every launch.
     */
    val controlsExpanded: Boolean = false,
) {
    /** True once at least one box is filled in far enough to try. */
    val isConfigured: Boolean
        get() = servers().isNotEmpty()

    /**
     * The boxes to try, in the order they should be tried.
     *
     * The mode decides the list rather than a flag read at the call site, so
     * every caller - a request, a poster, the event stream - fails over the
     * same way.
     */
    fun servers(): List<ServerConfig> = when (connectionMode) {
        ConnectionMode.AUTO -> listOf(primary, secondary).filter { it.isUsable }
        ConnectionMode.PRIMARY_ONLY -> listOf(primary).filter { it.isUsable }
        ConnectionMode.SECONDARY_ONLY -> listOf(secondary).filter { it.isUsable }
    }

    companion object {
        const val DEFAULT_POLL_SECONDS = 2

        /** What the settings screen offers, in seconds. */
        val POLL_INTERVALS = listOf(1, 2, 5, 10)
    }
}
