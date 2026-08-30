package com.jamal2367.tinyppimobile.data.remote

import java.io.IOException

/**
 * What went wrong with a call, in the shape the screens can act on.
 *
 * The add-on answers every failure as `{"error": "..."}` with the status that
 * goes with it, so what a screen has to work from is the status and a sentence
 * the add-on wrote. The few that a reader can actually do something about -
 * a wrong token, a box with every stream slot taken - are told apart by status
 * and message rather than by a code, because this API sends none.
 */
sealed class ApiFailure : Exception() {

    /** No box is filled in far enough to be worth a request. */
    data object NotConfigured : ApiFailure() {
        private fun readResolve(): Any = NotConfigured
        override val message: String get() = "No server configured"
    }

    /** None of the configured boxes answered. */
    data class Unreachable(
        val serverLabel: String?,
        val timedOut: Boolean,
        override val cause: IOException?,
    ) : ApiFailure() {
        override val message: String
            get() = cause?.message ?: "Server unreachable"
    }

    /** The box answered, and said no. */
    data class Api(
        val status: Int,
        val serverMessage: String?,
    ) : ApiFailure() {
        override val message: String
            get() = serverMessage ?: "HTTP $status"

        /** The token is missing or wrong. */
        val isUnauthorized: Boolean get() = status == 401

        /** Control is switched off in the add-on's own settings. */
        val isControlDisabled: Boolean get() = status == 403

        /**
         * Every stream slot is taken, or the box is going down with Kodi.
         *
         * The add-on caps concurrent streams at six and answers a seventh with
         * this rather than holding a thread open for it.
         */
        val isBusy: Boolean get() = status == 503
    }

    /** The answer arrived but could not be read as this API's JSON. */
    data class Malformed(override val cause: Throwable?) : ApiFailure() {
        override val message: String
            get() = cause?.message ?: "Malformed response"
    }
}

/** Thrown by the interceptor when there is nothing to send the request to. */
class NoServerConfiguredException : IOException("No server configured")
