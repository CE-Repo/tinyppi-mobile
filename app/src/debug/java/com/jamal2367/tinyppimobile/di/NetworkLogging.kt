package com.jamal2367.tinyppimobile.di

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * A line in the log per request, in debug builds only - the release build has
 * its own copy of this function that adds nothing.
 *
 * The logger goes on after whatever rewrites the request, never before it:
 * ahead of the failover interceptor every line would read
 * `http://localhost/...` - the placeholder Retrofit was built against -
 * instead of the address the attempt actually went to, which is the one
 * thing a log of a failing connection is read for.
 */
internal fun OkHttpClient.Builder.withDebugLogging(): OkHttpClient.Builder = addInterceptor(
    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
)
