package com.metrolist.netease

import android.content.Context
import com.metrolist.music.constants.EnableNeteaseCloudMusicKey
import com.metrolist.music.constants.NeteaseCloudMusicApiUrlKey
import com.metrolist.music.utils.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@OptIn(ExperimentalSerializationApi::class)
private val client = HttpClient {
    expectSuccess = false // Netease API may return non-200 status

    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        json(json)
        json(json, ContentType.Text.Html)
        json(json, ContentType.Text.Plain)
    }

    install(ContentEncoding) {
        gzip()
        deflate()
    }
}

/**
 * Netease Cloud Music Lyrics Provider
 * Uses the NeteaseCloudMusicApiEnhanced backend
 */
object NeteaseCloudMusicLyricsProvider : LyricsProvider {
    override val name = "NeteaseCloudMusic"

    // Configuration: Set this to your Netease API endpoint
    private const val DEFAULT_API_BASE_URL = "http://localhost:3000"

    // If you have a deployed Netease API, set this via preference
    private var apiBaseUrl: String = DEFAULT_API_BASE_URL
    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            runBlocking {
                val settings = context.dataStore.data.first()
                apiBaseUrl = settings[NeteaseCloudMusicApiUrlKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_API_BASE_URL
            }
            initialized = true
        }
    }

    override fun isEnabled(context: Context): Boolean {
        ensureInitialized(context)
        return context.dataStore[EnableNeteaseCloudMusicKey] ?: true
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val songId = findSongId(title, artist, duration) ?: throw IllegalStateException("No matching song found on Netease")
        fetchLyrics(songId)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        try {
            val songId = findSongId(title, artist, duration)
            if (songId != null) {
                fetchLyrics(songId).let(callback)
            }
        } catch (e: Exception) {
            // Ignore and continue
        }
    }

    private suspend fun findSongId(title: String, artist: String, duration: Int): String? {
        // Build search query: "title artist"
        val searchQuery = buildString {
            append(title)
            append(" ")
            append(artist)
        }

        return try {
            val response = client.get("${apiBaseUrl}/api/cloudsearch/pc") {
                parameter("keywords", searchQuery)
                parameter("type", 1) // Search for songs only
                parameter("limit", 30)
                parameter("offset", 0)
            }.body<NeteaseSearchResponse>()

            if (response.code == 200 && response.body.songs.isNotEmpty()) {
                // Find the best match based on duration tolerance
                val bestMatch = response.body.songs
                    .filter { abs(it.duration - duration) <= DURATION_TOLERANCE }
                    .minByOrNull { abs(it.duration - duration) }

                // If no duration match or duration is -1, take the first result
                bestMatch ?: response.body.songs.first()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchLyrics(songId: String): String {
        val response = client.get("${apiBaseUrl}/api/song/lyric/v1") {
            parameter("id", songId)
            parameter("cp", false)
            parameter("tv", 0)
            parameter("lv", 0)
            parameter("rv", 0)
            parameter("kv", 0)
            parameter("yv", 0)
            parameter("ytv", 0)
            parameter("yrv", 0)
        }.body<NeteaseLyricResponse>()

        return when (response.body.code) {
            200 -> {
                response.body.lyric ?: throw IllegalStateException("No lyric content")
            }
            else -> throw IllegalStateException("Failed to fetch lyrics: code ${response.body.code}")
        }
    }

    private companion object {
        const val DURATION_TOLERANCE = 8000 // 8 seconds tolerance in milliseconds
    }
}

@Serializable
private data class NeteaseSearchResponse(
    val code: Int,
    val result: NeteaseSearchResult,
)

@Serializable
private data class NeteaseSearchResult(
    val songs: List<NeteaseSong>,
)

@Serializable
private data class NeteaseSong(
    val id: String,
    val name: String,
    val artists: List<NeteaseArtist>,
    val album: NeteaseAlbum?,
    @SerialName("duration")
    val durationMs: Int,
) {
    val duration: Int get() = durationMs
}

@Serializable
private data class NeteaseArtist(
    val name: String,
)

@Serializable
private data class NeteaseAlbum(
    val name: String?,
)

@Serializable
private data class NeteaseLyricResponse(
    val status: Int,
    val body: NeteaseLyricBody,
)

@Serializable
private data class NeteaseLyricBody(
    val code: Int,
    val lyric: String?,
    @SerialName("klyric")
    val kLyric: String? = null,
    @SerialName("tlyric")
    val tLyric: String? = null,
)
