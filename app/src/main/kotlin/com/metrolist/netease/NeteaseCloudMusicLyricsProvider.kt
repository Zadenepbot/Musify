package com.metrolist.netease

import com.metrolist.music.constants.EnableNeteaseCloudMusicKey
import com.metrolist.music.utils.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalSerializationApi::class)
private val client = HttpClient {
    expectSuccess = false

    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }
        json(json)
    }

    install(ContentEncoding) {
        gzip()
        deflate()
    }
}

/**
 * Netease Cloud Music Lyrics Provider (Direct API)
 * Uses eapi encryption to call Netease Cloud Music API directly
 * No separate backend service required
 *
 * Based on NeteaseCloudMusicApiEnhanced eapi implementation
 */
object NeteaseCloudMusicLyricsProvider : LyricsProvider {
    override val name = "NeteaseCloudMusic"

    private const val DEFAULT_API_BASE_URL = "https://interface.music.163.com"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_MAGIC = "36cd479b6b5"

    private var initialized = false
    private lateinit var apiBaseUrl: String

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
        val searchQuery = buildString {
            append(title)
            append(" ")
            append(artist)
        }

        return try {
            val response = eapiRequest<NeteaseSearchResponse>(
                path = "/api/cloudsearch/pc",
                data = mapOf(
                    "s" to searchQuery,
                    "type" to "1",
                    "limit" to "30",
                    "offset" to "0"
                )
            )

            if (response.code == 200 && response.result.songs.isNotEmpty()) {
                val bestMatch = response.result.songs
                    .filter { abs(it.duration - duration) <= DURATION_TOLERANCE }
                    .minByOrNull { abs(it.duration - duration) }

                bestMatch ?: response.result.songs.first()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchLyrics(songId: String): String {
        val response = eapiRequest<NeteaseLyricResponse>(
            path = "/api/song/lyric/v1",
            data = mapOf(
                "id" to songId,
                "cp" to "false",
                "tv" to "0",
                "lv" to "0",
                "rv" to "0",
                "kv" to "0",
                "yv" to "0",
                "ytv" to "0",
                "yrv" to "0"
            )
        )

        return when (response.body.code) {
            200 -> {
                response.body.lyric ?: throw IllegalStateException("No lyric content")
            }
            else -> throw IllegalStateException("Failed to fetch lyrics: code ${response.body.code}")
        }
    }

    private suspend inline fun <reified T> eapiRequest(path: String, data: Map<String, Any>): EapiResponse<T> {
        val url = "$apiBaseUrl/eapi$path"

        // Build eapi encrypted params
        val jsonData = Json.encodeToString(data)
        val message = "nobody$path$EAPI_MAGIC$jsonData$EAPI_MAGIC"
        val digest = md5(message)
        val eapiData = "$path-$EAPI_MAGIC-$jsonData-$EAPI_MAGIC-$digest"
        val encryptedParams = aesEncrypt(eapiData, EAPI_KEY)

        // Send request as application/x-www-form-urlencoded
        val response = client.post(url) {
            userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(encryptedParams)
        }

        val responseBody = response.body<String>()
        return try {
            val baseResponse = Json.decodeFromString<NetBaseResponse<T>>(responseBody)
            EapiResponse(baseResponse.code, baseResponse.body)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse response: ${e.message}")
        }
    }

    private companion object {
        const val DURATION_TOLERANCE = 8000 // 8 seconds

        fun aesEncrypt(input: String, key: String): String {
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
            return bytesToHex(encrypted)
        }

        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytesToHex(digest)
        }

        fun bytesToHex(bytes: ByteArray): String {
            val hexArray = "0123456789abcdef".toCharArray()
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }
    }
}

// Response wrappers
private data class EapiResponse<T>(
    val code: Int,
    val body: T,
)

private data class NetBaseResponse<T>(
    val code: Int,
    val body: T,
)

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
