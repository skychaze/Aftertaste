package com.example.tracker

import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat

enum class PlaybackCommand {
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    REPEAT,
}

data class PlaybackControls(
    val canPlayPause: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val canSeek: Boolean = false,
    val canRepeat: Boolean = false,
) {
    fun supports(command: PlaybackCommand): Boolean =
        when (command) {
            PlaybackCommand.PLAY_PAUSE -> canPlayPause
            PlaybackCommand.PREVIOUS -> canGoPrevious
            PlaybackCommand.NEXT -> canGoNext
            PlaybackCommand.REPEAT -> canRepeat
        }

    companion object {
        const val YOUTUBE_MUSIC_REPEAT_ACTION = "loop_mode_action"

        fun from(state: PlaybackState?): PlaybackControls {
            val actions = state?.actions ?: 0L
            val toggleAction =
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    PlaybackState.ACTION_PAUSE
                } else PlaybackState.ACTION_PLAY
            return PlaybackControls(
                canPlayPause = actions and (toggleAction or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
                canGoPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                canGoNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
                canRepeat = actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L ||
                    state?.customActions?.any { it.action == YOUTUBE_MUSIC_REPEAT_ACTION } == true,
            )
        }
    }
}

enum class RepeatMode(val sessionValue: Int, val label: String) {
    OFF(PlaybackStateCompat.REPEAT_MODE_NONE, "Repeat off"),
    ALL(PlaybackStateCompat.REPEAT_MODE_ALL, "Repeat all"),
    ONE(PlaybackStateCompat.REPEAT_MODE_ONE, "Repeat one"),
    ;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun from(value: Int): RepeatMode = entries.firstOrNull { it.sessionValue == value } ?: OFF

        fun fromYouTubeMusicIcon(name: String?): RepeatMode? = when (name) {
            "repeat_off" -> OFF
            "repeat_all" -> ALL
            "repeat_one" -> ONE
            else -> null
        }
    }
}
