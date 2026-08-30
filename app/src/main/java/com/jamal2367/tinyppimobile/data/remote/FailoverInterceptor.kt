package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends every request to whichever configured box answers.
 *
 * Retrofit is built against a placeholder host; the real scheme, host, port and
 * token are put in here, per attempt. That keeps the two addresses out of every
 * call site: a screen asks for the library, and whether that came from the flat
 * or from outside is not its problem.
 *
 * Only a transport failure moves on to the next address. An answer is an
 * answer - a 401 from the local server means the token is wrong, and asking the
 * remote one with the same wrong token would only turn a clear error into a
 * confusing one.
 *
 * Every address but the last gets a short leash. A box on the same
 * network answers a connection in a few milliseconds; one that is not on this
 * network answers nothing at all, and the full connect timeout spent finding
 * that out is time the screen stays empty for. The last address keeps the whole
 * timeout, because behind it there is nothing left to fall back to.
 */
class FailoverInterceptor(private val router: ServerRouter) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val servers = router.candidates()
        if (servers.isEmpty()) throw NoServerConfiguredException()

        var lastFailure: IOException? = null
        for ((index, server) in servers.withIndex()) {
            val attempt = if (index == servers.lastIndex) {
                chain
            } else {
                chain.withConnectTimeout(FIRST_ATTEMPT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            try {
                val response = attempt.proceed(chain.request().retargetTo(server))
                router.markReachable(server)
                return response
            } catch (failure: IOException) {
                if (chain.call().isCanceled()) throw failure
                lastFailure = failure
                if (index == servers.lastIndex) {
                    router.markUnreachable()
                    throw ServerUnreachableException(servers, failure)
                }
            }
        }

        // Not reachable in practice: the loop above either returns or throws.
        router.markUnreachable()
        throw ServerUnreachableException(servers, lastFailure ?: IOException("No server answered"))
    }

    private fun Request.retargetTo(server: ServerConfig): Request {
        val url = url.newBuilder()
            .scheme(if (server.useHttps) "https" else "http")
            .host(server.host.trim())
            .port(server.port)
            .build()

        val builder = newBuilder().url(url)
        if (server.token.isNotBlank()) {
            builder.header(TOKEN_HEADER, server.token.trim())
        } else {
            builder.removeHeader(TOKEN_HEADER)
        }
        return builder.build()
    }

    companion object {
        const val TOKEN_HEADER = "X-TinyPPI-Token"

        /**
         * How long an address with another one behind it gets to connect.
         *
         * Generous for a server in the same flat and far short of the full
         * timeout, which is what a phone that has left it would otherwise wait
         * before the address that works is even tried.
         */
        const val FIRST_ATTEMPT_CONNECT_TIMEOUT_MS = 2_000
    }
}

/** Every configured address was tried and none of them answered. */
class ServerUnreachableException(
    val attempted: List<ServerConfig>,
    override val cause: IOException,
) : IOException(cause.message, cause)
