/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
    private val onVolumeMultiplierChanged: (Float) -> Unit = {},
) : Player.Listener {
    companion object {
        private const val TIMER_TICK_MS = 1000L

        const val TIME = 0
        const val TIME_FINISH = 1
        const val SONGS = 2
    }

    private var sleepTimerJob: Job? = null
    var triggerTime by mutableLongStateOf(-1L)
        private set

    var remainingSongs by mutableIntStateOf(0)
        private set
    var type by mutableIntStateOf(TIME)
        private set
    // fadeOut is disabled for now
    var fadeOutEnabled by mutableStateOf(false)
        private set
    val isActive: Boolean
        get() = triggerTime != -1L || remainingSongs > 0

    fun start(
        value: Int,
        timerType: Int,
    ) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        updateVolumeMultiplier(1f)
        type = timerType

        if (type == SONGS) {
            triggerTime = -1L
            remainingSongs = value
        } else {

            triggerTime = System.currentTimeMillis() + value.minutes.inWholeMilliseconds
            sleepTimerJob = scope.launch {
                while (this@SleepTimer.isActive) {
                    if (triggerTime != -1L) {
                        val remainingMs = triggerTime - System.currentTimeMillis()
                        if (remainingMs <= 0L) {
                            triggerTime = -1L
                            if (type == TIME_FINISH) {
                                remainingSongs = 1
                                type = SONGS
                            } else {
                                completeTimerAndPause()
                            }
                            break
                        }
                        delay(TIMER_TICK_MS)
                    }
                }
            }
        }
    }

    /**
     * Notify the sleep timer that a song transition has occurred outside of normal
     * player callbacks (e.g. during crossfade player swap). If "end of song" mode
     * is active, this will pause the player and deactivate the timer.
     */
    fun notifySongTransition() {
        if (remainingSongs > 0) {
            if (--remainingSongs == 0){
                completeTimerAndPause()
            }
        }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        fadeOutEnabled = false
        remainingSongs = 0
        triggerTime = -1L
        updateVolumeMultiplier(1f)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (remainingSongs > 0) {
            if (--remainingSongs == 0){
                completeTimerAndPause()
            }
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        // this only triggers when the playlist ended, not between songs
        if (playbackState == Player.STATE_ENDED && remainingSongs > 0) {
            completeTimerAndPause()
        }
    }

    private fun completeTimerAndPause() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        fadeOutEnabled = false
        remainingSongs = 0
        triggerTime = -1L
        updateVolumeMultiplier(1f)
        player.pause()
    }

    private fun updateVolumeMultiplier(multiplier: Float) {
        onVolumeMultiplierChanged(multiplier)
    }
}
