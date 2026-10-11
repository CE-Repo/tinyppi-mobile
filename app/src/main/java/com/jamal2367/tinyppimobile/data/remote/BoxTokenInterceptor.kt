package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Puts the token on a request that already names its box.
 *
 * For the pictures: their addresses are absolute and go past the failover
 * interceptor, so nothing else would add the header. The add-on reads the token
 * from `X-TinyPPI-Token` only - an address ends up in logs and caches, so a
 * `?token=` on it is ignored - and a picture asked for without it is refused
 * wherever the box wants the token for reading.
 *
 * The token is the one of whichever configured box the address names, matched
 * on scheme, host and port: the two boxes usually have tokens of their own, and
 * a picture of the one must never carry the token of the other. An address
 * that names neither is sent as it is.
 */
class BoxTokenInterceptor(private val router: ServerRouter) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val settings = router.settings
        val token = listOf(settings.primary, settings.secondary)
            .firstOrNull { it.names(request.url) }
            ?.token?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(FailoverInterceptor.TOKEN_HEADER, token).build()
        )
    }

    private fun ServerConfig.names(url: HttpUrl): Boolean =
        host.isNotBlank() &&
            url.scheme == (if (useHttps) "https" else "http") &&
            url.host.equals(host.trim().trimEnd('/'), ignoreCase = true) &&
            url.port == port
}
