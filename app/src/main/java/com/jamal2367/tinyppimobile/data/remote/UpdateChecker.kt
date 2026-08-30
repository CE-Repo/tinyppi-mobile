package com.jamal2367.tinyppimobile.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** What GitHub says about a release - only the four fields worth reading here. */
@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val draft: Boolean = false,
)

/** A published release that is ahead of the one running. */
data class AvailableUpdate(
    /** What to call it on screen: the release's own title. */
    val name: String,
    /** Where the release, and its APK, can be had. */
    val url: String,
)

/**
 * Which release a build is, as far as telling two of them apart goes.
 *
 * Two numbers rather than one, because this project publishes a release per
 * commit: the version name stands still for dozens of builds, and the build
 * number the workflow tags them with is the only thing that separates
 * `build-29` from `build-31`. The version still comes first - it is the one
 * that changes when something actually changed.
 */
data class ReleaseId(val version: List<Int>, val build: Int) {

    /**
     * Whether [other] is a release this one should be replaced by.
     *
     * A build number of zero means "built by hand, from a checkout" - there is
     * no run behind it to compare, so a build that does not know its own number
     * is never told it is behind by one. A newer *version* still reaches it.
     */
    fun isBehind(other: ReleaseId): Boolean {
        val byVersion = compareVersions(other.version, version)
        if (byVersion != 0) return byVersion > 0
        if (build <= 0 || other.build <= 0) return false
        return other.build > build
    }

    companion object {
        /** `1.0.0` out of `TinyPPI 1.0.0 - build 31`, and out of `1.0.0-debug`. */
        private val VERSION = Regex("""\d+\.\d+(?:\.\d+)*""")

        /** `31` out of `build-31`, and out of `TinyPPI 1.0.0 - build 31`. */
        private val BUILD = Regex("""build[-\s]?(\d+)""", RegexOption.IGNORE_CASE)

        /**
         * Read a release's identity off whatever text names it.
         *
         * Given several strings - a tag and a title, say - each is searched in
         * turn, so a version in the title and a build number in the tag are
         * both found without either having to carry the other.
         */
        fun of(vararg text: String?): ReleaseId {
            val parts = text.filterNotNull()
            val version = parts.firstNotNullOfOrNull { VERSION.find(it)?.value }
                ?.split('.')
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
            val build = parts.firstNotNullOfOrNull { BUILD.find(it)?.groupValues?.get(1) }
                ?.toIntOrNull()
                ?: 0
            return ReleaseId(version, build)
        }

        /**
         * `1.2.0` against `1.1.9`, part by part.
         *
         * A version nobody could read out of the text is not a comparison that
         * failed one way or the other - it is no answer at all, so it counts as
         * equal and lets the build number decide.
         */
        fun compareVersions(left: List<Int>, right: List<Int>): Int {
            if (left.isEmpty() || right.isEmpty()) return 0
            for (index in 0 until maxOf(left.size, right.size)) {
                // A missing part is a zero: `1.2` and `1.2.0` are one release.
                val difference = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
                if (difference != 0) return if (difference > 0) 1 else -1
            }
            return 0
        }
    }
}

/**
 * Asks GitHub whether there is a newer release than the one running.
 *
 * On the plain client rather than the failover one: github.com is an address
 * of its own and neither of the two boxes, so it must not be retargeted at
 * one - and a phone with no box in reach can still be told its app is out of
 * date.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /**
     * The newest release when it is ahead of this build, or null.
     *
     * Null for every other outcome as well - no network, a rate-limited API, an
     * answer that does not parse. An update check is not something anyone asked
     * for, so it never reports its own failure.
     */
    suspend fun findNewerRelease(
        apiUrl: String,
        current: ReleaseId,
        fallbackUrl: String,
    ): AvailableUpdate? = withContext(Dispatchers.IO) {
        val release = try {
            val request = Request.Builder()
                .url(apiUrl)
                // Asking for the versioned media type keeps the shape of the
                // answer fixed to what the fields above were written against.
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                json.decodeFromString<GithubRelease>(response.body.string())
            }
        } catch (failure: Exception) {
            Log.w(TAG, "Update check failed", failure)
            return@withContext null
        }

        if (release.draft) return@withContext null

        // The title carries the version, the tag carries the build number, and
        // this project fills both - so both are offered and each is taken from
        // wherever it turns up first.
        val latest = ReleaseId.of(release.name, release.tagName)
        if (!current.isBehind(latest)) return@withContext null

        AvailableUpdate(
            name = release.name?.takeIf { it.isNotBlank() }
                ?: release.tagName?.takeIf { it.isNotBlank() }
                ?: return@withContext null,
            url = release.htmlUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl,
        )
    }

    private companion object {
        const val TAG = "UpdateChecker"
    }
}
