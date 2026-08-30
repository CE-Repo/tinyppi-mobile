package com.jamal2367.tinyppimobile.util

import android.content.Context
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.remote.ApiFailure

/**
 * What to put on screen for a failed call.
 *
 * This API answers a failure with a status and a sentence, and no code beside
 * them - so the handful a reader can actually act on are told apart by status
 * and given a sentence that says what to do about it. Anything else falls back
 * to the add-on's own wording, which is written for a person already.
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is ApiFailure.NotConfigured -> context.getString(R.string.error_not_configured)

    is ApiFailure.Unreachable -> {
        val where = serverLabel?.takeIf { it.isNotBlank() }
        when {
            // Named before the address is: without this permission nothing on
            // the local network answers, and every other wording would send a
            // reader off to check a box that is not the problem.
            !LocalNetworkAccess.isGranted(context) ->
                context.getString(R.string.error_local_network_denied)

            where == null -> context.getString(R.string.error_unreachable)
            timedOut -> context.getString(R.string.api_error_timeout, where)
            else -> context.getString(R.string.api_error_network, where)
        }
    }

    is ApiFailure.Api -> when {
        isUnauthorized -> context.getString(R.string.api_error_unauthorized)
        isControlDisabled -> context.getString(R.string.api_error_control_disabled)
        isBusy -> context.getString(R.string.api_error_busy)
        status == 404 -> context.getString(R.string.api_error_not_found)
        status == 400 -> serverMessage ?: context.getString(R.string.api_error_bad_request)
        else -> serverMessage ?: context.getString(R.string.error_title)
    }

    is ApiFailure.Malformed -> context.getString(R.string.api_error_parse)

    else -> message ?: context.getString(R.string.error_title)
}
