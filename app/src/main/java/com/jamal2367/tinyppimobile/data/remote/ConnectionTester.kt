package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.model.ApiErrorBody
import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What the button in the settings found out about one address. */
sealed interface ConnectionTestResult {

    /**
     * The box answered, and this is what it said about itself.
     *
     * [tokenAccepted] is the interesting half. `/api/hello` is deliberately
     * unauthenticated, so it answers whether the token is right or wrong -
     * which is exactly what makes it useful here: a box that answers hello and
     * then refuses `/api/state` is one whose token is wrong, and that is a
     * different sentence from "nothing there".
     */
    data class Reachable(
        val hello: Hello,
        val tokenAccepted: Boolean,
    ) : ConnectionTestResult

    data class Refused(val failure: ApiFailure) : ConnectionTestResult
}

/**
 * Tries one address on its own.
 *
 * Deliberately not routed through the failover interceptor: the point of the
 * test is to find out whether *this* address works, and an answer that came
 * from the other one would be worse than useless.
 *
 * Two calls rather than one, because the two questions a reader has are
 * separate on this API. `/api/hello` says whether a TinyPPI is there at all
 * and what it will allow; `/api/state` is the first thing behind the token, so
 * it is what says whether the token was typed correctly - and on a box whose
 * read side is left open, it simply succeeds and the test says so.
 */
class ConnectionTester(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun test(server: ServerConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val hello = try {
            client.newCall(server.request("/api/hello")).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult.Refused(
                        ApiFailure.Api(response.code, body.errorMessage() ?: response.message)
                    )
                }
                runCatching { json.decodeFromString(Hello.serializer(), body) }.getOrNull()
                    ?: return@withContext ConnectionTestResult.Refused(ApiFailure.Malformed(null))
            }
        } catch (io: IOException) {
            return@withContext ConnectionTestResult.Refused(io.toUnreachable(server))
        }

        // The read side may or may not be behind the token, and the add-on
        // says which in the answer above. Where it is not, there is nothing
        // for the token to be wrong about and the test stops here.
        if (!hello.authRead) {
            return@withContext ConnectionTestResult.Reachable(hello, tokenAccepted = true)
        }

        val accepted = try {
            client.newCall(server.request("/api/state")).execute().use { it.isSuccessful }
        } catch (io: IOException) {
            return@withContext ConnectionTestResult.Refused(io.toUnreachable(server))
        }

        ConnectionTestResult.Reachable(hello, tokenAccepted = accepted)
    }

    private fun ServerConfig.request(path: String): Request = Request.Builder()
        .url("$baseUrl$path")
        .apply {
            if (token.isNotBlank()) {
                header(FailoverInterceptor.TOKEN_HEADER, token.trim())
            }
        }
        .build()

    private fun IOException.toUnreachable(server: ServerConfig) = ApiFailure.Unreachable(
        serverLabel = server.label,
        timedOut = this is java.net.SocketTimeoutException,
        cause = this,
    )

    private fun String?.errorMessage(): String? = this?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
        ?.error
        ?.takeIf { it.isNotBlank() }
}
