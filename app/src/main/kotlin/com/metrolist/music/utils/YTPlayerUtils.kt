/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.metrolist.music.utils.potoken.PoTokenGenerator
import com.metrolist.music.utils.potoken.PoTokenResult
import com.metrolist.music.utils.sabr.EjsNTransformSolver
import okhttp3.OkHttpClient
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder().proxy(YouTube.proxy).build()

    private val poTokenGenerator = PoTokenGenerator()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
            arrayOf(
                    TVHTML5_SIMPLY_EMBEDDED_PLAYER, // Try embedded player first for age-restricted
                    // content
                    TVHTML5,
                    ANDROID_VR_1_43_32,
                    ANDROID_VR_1_61_48,
                    ANDROID_CREATOR,
                    IPADOS,
                    ANDROID_VR_NO_AUTH,
                    MOBILE,
                    IOS,
                    WEB,
                    WEB_CREATOR
            )

    data class PlaybackData(
            val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
            val videoDetails: PlayerResponse.VideoDetails?,
            val playbackTracking: PlayerResponse.PlaybackTracking?,
            val format: PlayerResponse.StreamingData.Format,
            val streamUrl: String,
            val streamExpiresInSeconds: Int,
            val isVideoFormat: Boolean = false,
    )

    /**
     * Custom player response intended to use for playback. Metadata like audioConfig and
     * videoDetails are from [MAIN_CLIENT]. Format & stream can be from [MAIN_CLIENT] or
     * [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
            videoId: String,
            playlistId: String? = null,
            audioQuality: AudioQuality,
            connectivityManager: ConnectivityManager,
            enableVideo: Boolean = true,
    ): Result<PlaybackData> =
            runCatching {
                Timber.tag(logTag)
                        .d(
                                "Fetching player response for videoId: $videoId, playlistId: $playlistId"
                        )
                val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

                val isLoggedIn = YouTube.cookie != null
                Timber.tag(logTag)
                        .d(
                                "Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}"
                        )

                // Get signature timestamp (same as before for normal content)
                val signatureTimestamp = getSignatureTimestampOrNull(videoId)
                Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")

                // Generate PoToken
                var poToken: PoTokenResult? = null
                val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
                if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
                    Timber.tag(logTag).d("Generating PoToken for WEB_REMIX with sessionId")
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                        if (poToken != null) {
                            Timber.tag(logTag).d("PoToken generated successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
                    }
                }

                // Try WEB_REMIX with signature timestamp and poToken (same as before)
                Timber.tag(logTag)
                        .d(
                                "Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}"
                        )
                var mainPlayerResponse =
                        YouTube.player(
                                        videoId,
                                        playlistId,
                                        MAIN_CLIENT,
                                        signatureTimestamp.timestamp,
                                        poToken?.playerRequestPoToken
                                )
                                .getOrThrow()

                // Replace Art Track with Official Video if video is enabled
                if (enableVideo && mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_ATV") {
                    Timber.tag(logTag).d("Art track detected, searching for official video replacement")
                    val tempVideoDetails = mainPlayerResponse.videoDetails!!
                    val query = "${tempVideoDetails.title} ${tempVideoDetails.author} official video"
                    YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.let { searchResult ->
                        val replacementItem = searchResult.items.firstOrNull {
                            it is com.metrolist.innertube.models.SongItem
                        }
                        val replacementId = when (replacementItem) {
                            is com.metrolist.innertube.models.SongItem -> replacementItem.id
                            else -> null
                        }

                        if (replacementId != null && replacementId != videoId) {
                            Timber.tag(logTag).d("Found replacement video: $replacementId")
                            return playerResponseForPlayback(
                                videoId = replacementId,
                                playlistId = playlistId,
                                audioQuality = audioQuality,
                                connectivityManager = connectivityManager,
                                enableVideo = true
                            )
                        }
                    }
                }

                var usedAgeRestrictedClient: YouTubeClient? = null
                val wasOriginallyAgeRestricted: Boolean

                // Check if WEB_REMIX response indicates age-restricted
                val mainStatus = mainPlayerResponse.playabilityStatus.status
                val isAgeRestrictedFromResponse =
                        mainStatus in
                                listOf(
                                        "AGE_CHECK_REQUIRED",
                                        "AGE_VERIFICATION_REQUIRED",
                                        "LOGIN_REQUIRED",
                                        "CONTENT_CHECK_REQUIRED"
                                )
                wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

                if (isAgeRestrictedFromResponse && isLoggedIn) {
                    Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
                    Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
                    val creatorResponse =
                            YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
                    if (creatorResponse?.playabilityStatus?.status == "OK") {
                        Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                        mainPlayerResponse = creatorResponse
                        usedAgeRestrictedClient = WEB_CREATOR
                    }
                }

                if (mainPlayerResponse == null) {
                    throw Exception("Failed to get player response")
                }

                val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
                val videoDetails = mainPlayerResponse.videoDetails
                val playbackTracking = mainPlayerResponse.playbackTracking
                var format: PlayerResponse.StreamingData.Format? = null
                var streamUrl: String? = null
                var streamExpiresInSeconds: Int? = null
                var streamPlayerResponse: PlayerResponse? = null
                var retryMainPlayerResponse: PlayerResponse? =
                        if (usedAgeRestrictedClient != null) mainPlayerResponse else null

                val currentStatus = mainPlayerResponse.playabilityStatus.status
                var isAgeRestricted =
                        currentStatus in
                                listOf(
                                        "AGE_CHECK_REQUIRED",
                                        "AGE_VERIFICATION_REQUIRED",
                                        "LOGIN_REQUIRED",
                                        "CONTENT_CHECK_REQUIRED"
                                )

                if (isAgeRestricted) {
                    Timber.tag(logTag)
                            .d(
                                    "Content is still age-restricted (status: $currentStatus), will try fallback clients"
                            )
                    Log.i(
                            TAG,
                            "Age-restricted content detected: videoId=$videoId, status=$currentStatus"
                    )
                }

                val isPrivateTrack =
                        mainPlayerResponse.videoDetails?.musicVideoType ==
                                "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                val startIndex =
                        when {
                            isPrivateTrack -> 1 // TVHTML5
                            isAgeRestricted -> 0
                            else -> -1
                        }

                for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
                    format = null
                    streamUrl = null
                    streamExpiresInSeconds = null

                    val client: YouTubeClient
                    if (clientIndex == -1) {
                        client = MAIN_CLIENT
                        streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                        Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
                    } else {
                        client = STREAM_FALLBACK_CLIENTS[clientIndex]
                        Timber.tag(logTag)
                                .d(
                                        "Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}"
                                )

                        if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                            Timber.tag(logTag)
                                    .d("Skipping client ${client.clientName} - requires login")
                            continue
                        }

                        Timber.tag(logTag)
                                .d(
                                        "Fetching player response for fallback client: ${client.clientName}"
                                )
                        val clientPoToken =
                                if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                        val clientSigTimestamp =
                                if (wasOriginallyAgeRestricted) null
                                else signatureTimestamp.timestamp
                        streamPlayerResponse =
                                YouTube.player(
                                                videoId,
                                                playlistId,
                                                client,
                                                clientSigTimestamp,
                                                clientPoToken
                                        )
                                        .getOrNull()
                    }

                    if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                        val clientName =
                                if (clientIndex == -1) MAIN_CLIENT.clientName
                                else STREAM_FALLBACK_CLIENTS[clientIndex].clientName
                        Timber.tag(logTag).d("Player response status OK for client: $clientName")

                        val responseToUse = streamPlayerResponse

                        format =
                                findFormat(
                                        responseToUse,
                                        audioQuality,
                                        connectivityManager,
                                        enableVideo
                                )

                        if (format == null) {
                            Timber.tag(logTag).d("No suitable format found for client: $clientName")
                            continue
                        }

                        Timber.tag(logTag)
                                .d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                        streamUrl =
                                findUrlOrNull(
                                        format,
                                        videoId,
                                        responseToUse,
                                        skipNewPipe = wasOriginallyAgeRestricted
                                )
                        if (streamUrl == null) {
                            Timber.tag(logTag).d("Stream URL not found for format")
                            continue
                        }

                        val currentClient =
                                if (clientIndex == -1) {
                                    usedAgeRestrictedClient ?: MAIN_CLIENT
                                } else {
                                    STREAM_FALLBACK_CLIENTS[clientIndex]
                                }

                        val isPrivatelyOwnedTrack =
                                streamPlayerResponse.videoDetails?.musicVideoType ==
                                        "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                        val needsNTransform =
                                currentClient.useWebPoTokens ||
                                        currentClient.clientName in
                                                listOf(
                                                        "WEB",
                                                        "WEB_REMIX",
                                                        "WEB_CREATOR",
                                                        "TVHTML5"
                                                ) ||
                                        isPrivatelyOwnedTrack

                        if (needsNTransform) {
                            try {
                                Timber.tag(logTag).d("Applying n-transform to stream URL")
                                val transformed =
                                        EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                                if (transformed != streamUrl) {
                                    streamUrl = transformed
                                }
                            } catch (e: Exception) {
                                Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                            }
                        }

                        if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                            Timber.tag(logTag).d("Appending pot parameter to stream URL")
                            val separator = if ("?" in streamUrl!!) "&" else "?"
                            streamUrl =
                                    "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                        }

                        streamExpiresInSeconds =
                                streamPlayerResponse.streamingData?.expiresInSeconds
                        if (streamExpiresInSeconds == null) {
                            Timber.tag(logTag).d("Stream expiration time not found")
                            continue
                        }

                        Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                        val isPrivatelyOwned =
                                streamPlayerResponse.videoDetails?.musicVideoType ==
                                        "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                        if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                            if (isPrivatelyOwned) {
                                Timber.tag(logTag)
                                        .d("Skipping validation for privately owned track")
                            } else {
                                Timber.tag(logTag)
                                        .d("Using last fallback client without validation")
                            }
                            Log.i(
                                    TAG,
                                    "Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned"
                            )
                            break
                        }

                        if (validateStatus(streamUrl!!)) {
                            Timber.tag(logTag)
                                    .d(
                                            "Stream validated successfully with client: ${currentClient.clientName}"
                                    )
                            Log.i(
                                    TAG,
                                    "Playback: client=${currentClient.clientName}, videoId=$videoId"
                            )
                            break
                        } else {
                            Timber.tag(logTag)
                                    .d(
                                            "Stream validation failed for client: ${currentClient.clientName}"
                                    )

                            if (currentClient.useWebPoTokens) {
                                var nTransformWorked = false
                                try {
                                    val nTransformed =
                                            CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                                    if (nTransformed != streamUrl && validateStatus(nTransformed)) {
                                        streamUrl = nTransformed
                                        nTransformWorked = true
                                        Log.i(
                                                TAG,
                                                "Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                                }
                                if (nTransformWorked) break
                            }
                        }
                    } else {
                        Timber.tag(logTag)
                                .d(
                                        "Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}"
                                )
                    }
                }

                if (format == null) {
                    Timber.tag(logTag).w("No muxed format found after trying all clients, falling back to audio-only")
                    for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
                        val client = if (clientIndex == -1) MAIN_CLIENT else STREAM_FALLBACK_CLIENTS[clientIndex]
                        val response = YouTube.player(videoId, playlistId, client, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrNull()
                        if (response?.playabilityStatus?.status == "OK") {
                            val audioFormat = selectBestAudio(response, audioQuality, connectivityManager.isActiveNetworkMetered)
                            if (audioFormat != null) {
                                val url = findUrlOrNull(audioFormat, videoId, response, skipNewPipe = wasOriginallyAgeRestricted)
                                if (url != null) {
                                    format = audioFormat
                                    streamUrl = url
                                    streamPlayerResponse = response
                                    streamExpiresInSeconds = response.streamingData?.expiresInSeconds
                                    Timber.tag(logTag).d("Fallback to audio successful with client: ${client.clientName}")
                                    break
                                }
                            }
                        }
                    }
                }

                if (streamPlayerResponse == null) {
                    Timber.tag(logTag).e("Bad stream player response - all clients failed")
                    if (isUploadedTrack) {
                        println(
                                "[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId"
                        )
                    }
                    throw Exception("Bad stream player response")
                }

                if (streamPlayerResponse.playabilityStatus.status != "OK") {
                    val errorReason = streamPlayerResponse.playabilityStatus.reason
                    Timber.tag(logTag).e("Playability status not OK: $errorReason")
                    if (isUploadedTrack) {
                        println(
                                "[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason"
                        )
                    }
                    throw PlaybackException(
                            errorReason,
                            null,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }

                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).e("Missing stream expire time")
                    throw Exception("Missing stream expire time")
                }

                if (format == null) {
                    Timber.tag(logTag).e("Could not find format")
                    throw Exception("Could not find format")
                }

                if (streamUrl == null) {
                    Timber.tag(logTag).e("Could not find stream url")
                    throw Exception("Could not find stream url")
                }

                Timber.tag(logTag)
                        .d(
                                "Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}"
                        )
                if (isUploadedTrack) {
                    println(
                            "[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${
                    streamUrl?.take(
                        100
                    )
                }..."
                    )
                }
                PlaybackData(
                        audioConfig,
                        videoDetails,
                        playbackTracking,
                        format,
                        streamUrl,
                        streamExpiresInSeconds,
                        isVideoFormat = format.width != null,
                )
            }
                    .onFailure { e ->
                        Timber.tag(logTag).e(e, "Exception during playback for videoId=$videoId")
                    }

    /**
     * Simple player response intended to use for metadata only. Stream URLs of this response might
     * not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
            videoId: String,
            playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag)
                .d(
                        "Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}"
                )
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
                .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
                .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
            playerResponse: PlayerResponse,
            audioQuality: AudioQuality,
            connectivityManager: ConnectivityManager,
            enableVideo: Boolean = true,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag)
                .d(
                        "Finding format: video=$enableVideo, quality=$audioQuality, metered=${connectivityManager.isActiveNetworkMetered}"
                )

        val isMetered = connectivityManager.isActiveNetworkMetered
        val isArtTrack = playerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_ATV"
        // Always try to get muxed format if available, to allow seamless switching
        val effectiveEnableVideo = true

        val format =
                if (effectiveEnableVideo) {
                    val muxedFormats =
                            playerResponse.streamingData?.formats
                                    ?.filter {
                                        it.width != null &&
                                                (it.height ?: 0) > 0 &&
                                                (it.width ?: 0) > 0
                                    }
                                    ?.filter { it.mimeType.contains("mp4") }

                    val selectedMuxed =
                            muxedFormats?.maxByOrNull { format ->
                                val height = format.height ?: 0
                                val qualityScore =
                                        when {
                                            isMetered && height <= 360 -> 4000
                                            isMetered && height <= 480 -> 3000
                                            isMetered && height <= 720 -> 1000
                                            isMetered -> 100
                                            height <= 360 -> 500
                                            height <= 480 -> 1000
                                            height <= 720 -> 3000
                                            height <= 1080 -> 2000
                                            else -> 500
                                        }
                                qualityScore + (format.bitrate / 1000)
                            }

                    if (selectedMuxed != null) {
                        Timber.tag(logTag)
                                .d(
                                        "Using muxed format: ${selectedMuxed.mimeType} ${selectedMuxed.width}x${selectedMuxed.height}"
                                )
                        selectedMuxed
                    } else {
                        Timber.tag(logTag)
                                .w("No muxed format available for this client")
                        null
                    }
                } else {
                    selectBestAudio(playerResponse, audioQuality, isMetered)
                }

        if (format != null) {
            Timber.tag(logTag).d("Selected: ${format.mimeType}, bitrate: ${format.bitrate}B/s")
        } else {
            Timber.tag(logTag).e("No format available")
        }

        return format
    }

    private fun selectBestAudio(
            playerResponse: PlayerResponse,
            audioQuality: AudioQuality,
            isMetered: Boolean
    ): PlayerResponse.StreamingData.Format? {
        return playerResponse.streamingData?.adaptiveFormats
                ?.filter { it.isAudio && it.isOriginal }
                ?.maxByOrNull { format ->
                    val bitrateScore =
                            format.bitrate *
                                    when (audioQuality) {
                                        AudioQuality.AUTO -> if (isMetered) -1 else 1
                                        AudioQuality.HIGH -> 1
                                        AudioQuality.LOW -> -1
                                    }
                    val codecBonus = if (format.mimeType.startsWith("audio/webm")) 10240 else 0
                    bitrateScore + codecBonus
                }
    }

    /**
     * Checks if the stream url returns a successful status. If this returns true the url is likely
     * to work. If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder =
                    okhttp3.Request.Builder()
                            .head()
                            .url(url)
                            .header("User-Agent", YouTubeClient.USER_AGENT_WEB)

            // Add authentication cookie for privately owned tracks
            YouTube.cookie?.let { cookie -> requestBuilder.addHeader("Cookie", cookie) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isSuccessful = response.isSuccessful
            Timber.tag(logTag)
                    .d(
                            "Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})"
                    )
            return isSuccessful
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }

    data class SignatureTimestampResult(val timestamp: Int?, val isAgeRestricted: Boolean)

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
                onSuccess = { timestamp ->
                    Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                    SignatureTimestampResult(timestamp, isAgeRestricted = false)
                },
                onFailure = { error ->
                    val isAgeRestricted =
                            error.message?.contains("age-restricted", ignoreCase = true) == true ||
                                    error.cause?.message?.contains(
                                            "age-restricted",
                                            ignoreCase = true
                                    ) == true
                    if (isAgeRestricted) {
                        Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                        Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                    } else {
                        Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                        reportException(error)
                    }
                    SignatureTimestampResult(null, isAgeRestricted)
                }
        )
    }

    private suspend fun findUrlOrNull(
            format: PlayerResponse.StreamingData.Format,
            videoId: String,
            playerResponse: PlayerResponse,
            skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag)
                .d(
                        "Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe"
                )

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl =
                    CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        // Skip NewPipe for age-restricted content
        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        // Try to get URL using NewPipeExtractor signature deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Fallback: try to get URL from StreamInfo
        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream =
                    streamUrls
                            .find { urlPair ->
                                playerResponse.streamingData?.adaptiveFormats?.any {
                                    it.itag == urlPair.first && it.isAudio
                                } == true
                            }
                            ?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
