package com.example.tracker

import android.media.session.PlaybackState

enum class PlaybackCommand {
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
}

data class PlaybackControls(
    val canPlayPause: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
) {
    fun supports(command: PlaybackCommand): Boolean =
        when (command) {
            PlaybackCommand.PLAY_PAUSE -> canPlayPause
            PlaybackCommand.PREVIOUS -> canGoPrevious
            PlaybackCommand.NEXT -> canGoNext
        }

    companion object {
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
            )
        }
    }
}
