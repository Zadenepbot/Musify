/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

private val sharedOkHttpClient: OkHttpClient = OkHttpClient()

/**
 * Handles Matrix Rich Presence (RPC) updates by setting the user's presence status
 * to reflect the currently playing song.
 *
 * @property homeserver The Matrix homeserver URL.
 * @property userId The Matrix user ID (e.g., @user:example.com).
 * @property accessToken The Matrix access token for authentication.
 * @property listeningPrefix The localized prefix used when a song is playing (e.g., "Listening to:").
 * @property pausedPrefix The localized prefix used when a song is paused (e.g., "Paused:").
 * @property client The [OkHttpClient] used for network requests.
 */
class MatrixRPC(
    private val homeserver: String,
    private val userId: String,
    private val accessToken: String,
    private val listeningPrefix: String,
    private val pausedPrefix: String,
    private val client: OkHttpClient = sharedOkHttpClient
) {
    private var lastStatusMsg: String? = null
    private var lastPresence: String? = null

    /**
     * Updates the user's Matrix status message with the current song information.
     *
     * @param song The [Song] being played, or null to clear the status.
     * @param currentPositionMs The current playback position in milliseconds.
     * @param statusFormat The format string for the status message (e.g., "{song_name} by {artist_name}").
     * @param presence The Matrix presence state (e.g., "online", "unavailable", "offline").
     * @return A [Result] indicating success or failure of the update operation.
     */
    suspend fun updateSong(
        song: Song?,
        currentPositionMs: Long = 0,
        statusFormat: String = "",
        presence: String = "online"
    ) = runCatching {
        Timber.tag("MatrixRPC").d("updateSong: user=$userId, presence=$presence, hasSong=${song != null}")

        if (homeserver.isEmpty() || userId.isEmpty() || accessToken.isEmpty()) {
            Timber.tag("MatrixRPC").w("Missing credentials for $userId")
            return@runCatching
        }

        val resolvedText = if (song != null) {
            val resolved = resolveVariables(
                statusFormat,
                song,
                currentPositionMs
            )
            if (presence == "unavailable") {
                if (resolved.startsWith(listeningPrefix, ignoreCase = true)) {
                    pausedPrefix + resolved.substring(listeningPrefix.length)
                } else {
                    "$pausedPrefix $resolved"
                }
            } else {
                resolved
            }
        } else {
            ""
        }

        if (resolvedText == lastStatusMsg && presence == lastPresence) {
            return@runCatching
        }

        val jsonBody = JSONObject().apply {
            put("presence", presence)
            put("status_msg", resolvedText)
        }.toString()

        Timber.tag("MatrixRPC").d("Updating status for $userId (len=${resolvedText.length}): $jsonBody")

        val url = homeserver.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("_matrix")
            ?.addPathSegment("client")
            ?.addPathSegment("v3")
            ?.addPathSegment("presence")
            ?.addPathSegment(userId)
            ?.addPathSegment("status")
            ?.build() ?: return@runCatching

        if (!url.isHttps) {
            Timber.tag("MatrixRPC").e("Refusing to send Matrix access token over non-HTTPS: $url")
            return@runCatching
        }

        val urlString = url.toString()

        val request = Request.Builder()
            .url(urlString)
            .addHeader("Authorization", "Bearer $accessToken")
            .put(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Timber.tag("MatrixRPC")
                        .e("Failed to update status: ${response.code} ${response.message} - Body: $errorBody")
                } else {
                    Timber.tag("MatrixRPC").d("Successfully updated status")
                    lastStatusMsg = resolvedText
                    lastPresence = presence
                }
            }
        }
    }.onFailure { throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        Timber.tag("MatrixRPC").e(throwable, "Failed to update Matrix presence via updateSong")
    }

    /**
     * Clears the Matrix status by setting it to empty and presence to offline.
     */
    suspend fun clearStatus() {
        updateSong(song = null, statusFormat = "", presence = "offline")
    }

    companion object {
        /**
         * Resolves template variables within a string using the current song's metadata.
         * Supported variables: {song_name}, {artist_name}, {album_name}, {current_time}, {total_time}.
         *
         * @param text The template string to process.
         * @param song The [Song] metadata to use for resolution.
         * @param currentPositionMs The current position to use for {current_time}.
         * @return The resolved status string.
         */
        fun resolveVariables(text: String, song: Song, currentPositionMs: Long = 0): String {
            val resolved = text
                .replace("{song_name}", song.song.title)
                .replace("{artist_name}", song.artists.joinToString { it.name })
                .replace("{album_name}", song.album?.title ?: "")
                .replace("{current_time}", makeTimeString(currentPositionMs))
                .replace("{total_time}", makeTimeString(song.song.duration * 1000L))

            Timber.tag("MatrixRPC").d("resolveVariables: input='$text', output='$resolved'")
            return resolved
        }
    }
}
