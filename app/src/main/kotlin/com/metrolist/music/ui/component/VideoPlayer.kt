/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Video player component that displays video content from the current ExoPlayer instance. This
 * component only shows the video surface when video tracks are actually available.
 *
 * @param player The ExoPlayer instance that is playing the current media
 * @param modifier Modifier to be applied to the video player
 */
@Composable
fun VideoPlayer(
    player: Player,
    modifier: Modifier = Modifier,
    onHasVideoChanged: (Boolean) -> Unit = {},
    onRenderedFirstFrame: () -> Unit = {},
) {
    var hasVideo by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    super.onTracksChanged(tracks)
                    val newHasVideo =
                        tracks.groups.any { trackGroup ->
                            trackGroup.type == C.TRACK_TYPE_VIDEO && trackGroup.isSupported
                        }
                    hasVideo = newHasVideo
                    onHasVideoChanged(newHasVideo)
                }

                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    onRenderedFirstFrame()
                }
            }
        player.addListener(listener)

        val initialHasVideo =
            player.currentTracks.groups.any { trackGroup ->
                trackGroup.type == C.TRACK_TYPE_VIDEO && trackGroup.isSupported
            }
        hasVideo = initialHasVideo
        onHasVideoChanged(initialHasVideo)

        onDispose { player.removeListener(listener) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasVideo) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setUseArtwork(false)
                        setBackgroundColor(0) // Transparent background
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
