/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import com.metrolist.music.models.MediaMetadata
import java.util.ArrayDeque

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode =
        when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
}

fun Player.getQueueWindows(): List<Timeline.Window> =
    getQueueWindows(currentTimeline, currentMediaItemIndex, shuffleModeEnabled)

fun getQueueWindows(
    timeline: Timeline,
    currentMediaItemIndex: Int,
    shuffleModeEnabled: Boolean,
): List<Timeline.Window> {
    if (timeline.isEmpty) {
        return emptyList()
    }
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex =
                timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(
                firstMediaItemIndex,
                REPEAT_MODE_OFF,
                shuffleModeEnabled
            )
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}

fun Player.getCurrentQueueIndex(): Int =
    getCurrentQueueIndex(currentTimeline, currentMediaItemIndex, shuffleModeEnabled)

fun getCurrentQueueIndex(
    timeline: Timeline,
    currentMediaItemIndex: Int,
    shuffleModeEnabled: Boolean,
): Int {
    if (timeline.isEmpty) {
        return -1
    }
    var index = 0
    var currentIndex = currentMediaItemIndex
    while (currentIndex != C.INDEX_UNSET) {
        currentIndex = timeline.getPreviousWindowIndex(
            currentIndex,
            REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (currentIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

val Player.currentMetadata: MediaMetadata?
    get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
    get() =
        object : AbstractList<MediaItem>() {
            override val size: Int
                get() = mediaItemCount

            override fun get(index: Int): MediaItem = getMediaItemAt(index)
        }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}

fun Player.setOffloadEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences
                .Builder()
                .setAudioOffloadMode(
                    if (enabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
        ).build()
}
