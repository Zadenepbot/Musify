/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.models

import androidx.compose.runtime.Immutable
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Suppress("UNUSED")
fun SongItem.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = this.id,
    title = this.title,
    artists = this.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
    duration = this.duration ?: -1,
    thumbnailUrl = this.thumbnail,
    album = this.album?.let { MediaMetadata.Album(id = it.id, title = it.name, artworkUrl = null) },
    setVideoId = this.setVideoId,
    musicVideoType = this.musicVideoType,
    explicit = this.explicit,
    liked = false, // Not available in SongItem
    likedDate = null,
    inLibrary = null,
    libraryAddToken = this.libraryAddToken,
    libraryRemoveToken = this.libraryRemoveToken,
    suggestedBy = null,
    squareThumbnailUrl = this.squareThumbnail,
    artworkUrl = null, // Not directly in SongItem
    videoId = null, // Not directly in SongItem
    isEpisode = this.isEpisode
)


@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val musicVideoType: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val suggestedBy: String? = null,
    val squareThumbnailUrl: String? = null,
    val artworkUrl: String? = null,
    val videoId: String? = null,
    val isEpisode: Boolean = false,
) : Serializable {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    val resolvedArtworkUrl: String?
        get() = artworkUrl ?: thumbnailUrl
    val resolvedVideoId: String?
        get() = videoId ?: setVideoId

    data class Artist(
        val id: String?,
        val name: String,
    ) : Serializable

    data class Album(
        val id: String,
        val title: String,
        val artworkUrl: String? = null,
    ) : Serializable

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
            libraryAddToken = libraryAddToken,
            libraryRemoveToken = libraryRemoveToken,
            isVideo = isVideoSong,
            squareThumbnailUrl = squareThumbnailUrl,
            isEpisode = isEpisode,
            videoId = videoId ?: setVideoId
        )
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists = artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl?.resize(544, 544),
        album = album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
                artworkUrl = it.artworkUrl
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName ?: "",
                artworkUrl = null
            )
        },
        explicit = song.explicit,
        liked = song.liked,
        likedDate = song.likedDate,
        inLibrary = song.inLibrary,
        libraryAddToken = song.libraryAddToken,
        libraryRemoveToken = song.libraryRemoveToken,
        suggestedBy = null,
        setVideoId = song.videoId,
        musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else MUSIC_VIDEO_TYPE_ATV,
        squareThumbnailUrl = song.squareThumbnailUrl?.resize(544, 544),
        artworkUrl = song.artworkUrl,
        videoId = song.videoId,
        isEpisode = song.isEpisode,

    )


fun EpisodeItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists = listOfNotNull(author).map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = duration ?: -1,
        thumbnailUrl = thumbnail.resize(544, 544),
        album = podcast?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        explicit = explicit,
        suggestedBy = null,
        isEpisode = true,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
    )
