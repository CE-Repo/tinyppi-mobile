package com.jamal2367.tinyppimobile.di

import okhttp3.OkHttpClient

/** Release builds log no requests; the debug build's copy of this adds a logger. */
internal fun OkHttpClient.Builder.withDebugLogging(): OkHttpClient.Builder = this
