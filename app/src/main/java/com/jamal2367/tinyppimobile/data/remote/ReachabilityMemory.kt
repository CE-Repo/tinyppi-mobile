package com.jamal2367.tinyppimobile.data.remote

/**
 * A note of which address last answered, kept across launches.
 *
 * Without it every cold start outside the flat begins by asking the local
 * address, which is not on this network and cannot say so: the request sits
 * there until the connect timeout runs out, and nothing is on screen until it
 * does. The note turns that into the one address that is going to answer.
 *
 * Read from an OkHttp thread on the way out of the first request, which is why
 * it is synchronous - a value that arrives after the request it was meant to
 * steer is no value at all.
 */
interface ReachabilityMemory {

    /** The address that last answered, or null when there is no usable note. */
    fun lastReachable(): String?

    /** Note that this address answered, now. */
    fun remember(baseUrl: String)

    /** Drop the note - the addresses it was about are no longer the ones configured. */
    fun forget()

    companion object {
        /** For a router built without one, and for the tests. */
        val None: ReachabilityMemory = object : ReachabilityMemory {
            override fun lastReachable(): String? = null
            override fun remember(baseUrl: String) = Unit
            override fun forget() = Unit
        }
    }
}
