package com.jamal2367.tinyppimobile.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether this app may talk to an address on the network it is on.
 *
 * Android 17 made that a permission of its own: a server in the same flat is no
 * longer simply "the internet", and an app that was never granted it has its
 * connections dropped rather than refused. That arrives as a connect timeout
 * six seconds later and looks exactly like a server that is switched off -
 * which is the whole reason this is worth naming in the interface instead of
 * leaving it to be diagnosed from a log.
 *
 * Every address this app knows is a local one, so the permission is asked for
 * at startup rather than at the first request.
 */
object LocalNetworkAccess {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** The API level that introduced it; below this there is nothing to ask. */
    private const val FIRST_SDK = 37

    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= FIRST_SDK

    fun isGranted(context: Context): Boolean = !isRequired ||
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
