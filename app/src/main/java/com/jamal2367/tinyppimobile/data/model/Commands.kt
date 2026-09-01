package com.jamal2367.tinyppimobile.data.model

import kotlinx.serialization.Serializable

/**
 * What the transport row may ask the player for.
 *
 * A fixed set, checked on the far side before anything reaches Kodi: the
 * request names an action, never a JSON-RPC method, so a client can only ever
 * do these ten things (see `_COMMANDS` in the add-on's `snapshot.py`).
 */
enum class PlayerAction(val id: String) {
    PLAY_PAUSE("playpause"),
    STOP("stop"),

    /**
     * Step to the chapter before this one, or the one after it.
     *
     * Carries no value, and is refused on a file with no chapters: Kodi has no
     * JSON-RPC method for a chapter, only the action a keymap sends, and that
     * action seeks a long way instead when there is no chapter to land on. The
     * add-on checks the count rather than let the key mean two things, so the
     * app can take a refusal at face value.
     */
    CHAPTER_PREVIOUS("chapter_previous"),
    CHAPTER_NEXT("chapter_next"),

    /** Jump by a number of seconds, forwards or back. */
    SEEK("seek"),

    /** Jump to a percentage of the running time. */
    SEEK_PERCENT("seek_percent"),

    /** Set the volume, 0 to 100. */
    VOLUME("volume"),

    /** Toggle mute; carries no value. */
    MUTE("mute"),

    /** Pick an audio stream by its index. */
    AUDIO("audio"),

    /**
     * Pick a subtitle stream by its index, or [SUBTITLES_OFF] to switch them
     * off. One control on the screen, so one action here.
     */
    SUBTITLE("subtitle");

    companion object {
        /** The value that turns subtitles off rather than picking a track. */
        const val SUBTITLES_OFF = -1.0

        /** How far a seek may jump, in seconds - the add-on refuses anything wider. */
        const val SEEK_LIMIT_SECONDS = 3600.0
    }
}

@Serializable
data class CommandBody(val action: String, val value: Double? = null)

@Serializable
data class ModeBody(val mode: String)
