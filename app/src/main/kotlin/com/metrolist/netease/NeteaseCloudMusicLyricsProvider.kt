package com.metrolist.netease

import android.content.Context
import com.metrolist.music.constants.EnableNeteaseCloudMusicKey
import com.metrolist.music.constants.NeteaseCloudMusicApiUrlKey
import com.metrolist.music.lyrics.LyricsProvider
import com.metrolist.music.utils.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
}

/**
 * Netease Cloud Music Lyrics Provider (Direct API)
 * Uses eapi encryption to call Netease Cloud Music API directly
 * No separate backend service required
 */
object NeteaseCloudMusicLyricsProvider : LyricsProvider {
    override val name = "NeteaseCloudMusic"

    private const val DEFAULT_API_BASE_URL = "https://interface.music.163.com"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_MAGIC = "36cd479b6b5"
    private const val DURATION_TOLERANCE = 8000

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
            val json = eapiRequest(
                path = "/api/cloudsearch/pc",
                data = mapOf(
                    "s" to searchQuery,
                    "type" to "1",
                    "limit" to "30",
                    "offset" to "0"
                )
            )

            val jsonObj = json.jsonObject
            val code = jsonObj["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (code == 200) {
                val result = jsonObj["result"]?.jsonObject ?: return null
                val songs = result["songs"]?.jsonArray ?: return null

                val songList = songs.mapNotNull { songObj ->
                    val song = songObj.jsonObject
                    val id = song["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = song["name"]?.jsonPrimitive?.content ?: ""
                    val artists = song["artists"]?.jsonArray?.map { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: emptyList()
                    val album = song["album"]?.jsonObject
                    val albumName = album?.get("name")?.jsonPrimitive?.content
                    val durationMs = song["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                    NeteaseSong(id, name, artists, albumName, durationMs)
                }

                val bestMatch = songList
                    .filter { abs(it.duration - duration) <= DURATION_TOLERANCE }
                    .minByOrNull { abs(it.duration - duration) }

                bestMatch?.id ?: songList.firstOrNull()?.id
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchLyrics(songId: String): String {
        val json = eapiRequest(
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

        val jsonObj = json.jsonObject
        val status = jsonObj["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val body = jsonObj["body"]?.jsonObject ?: throw IllegalStateException("No body in response")

        val code = body["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        if (code == 200) {
            return body["lyric"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("No lyric content")
        } else {
            throw IllegalStateException("Failed to fetch lyrics: code $code")
        }
    }

    private suspend fun eapiRequest(path: String, data: Map<String, Any>): JsonElement {
        val url = "$apiBaseUrl/eapi$path"

        // Build eapi encrypted params
        val jsonData = Json.encodeToString(data)
        val message = "nobody$path$EAPI_MAGIC$jsonData$EAPI_MAGIC"
        val digest = md5(message)
        val eapiData = "$path-$EAPI_MAGIC-$jsonData-$EAPI_MAGIC-$digest"
        val encryptedParams = aesEncrypt(eapiData, EAPI_KEY)

        // Send request as raw body
        val response = client.post(url) {
            userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152")
            setBody(encryptedParams)
        }

        val responseBody = response.body<String>()
        return Json.parseToJsonElement(responseBody)
    }

    private fun aesEncrypt(input: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(encrypted)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String {
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

// Data class for song parsing
private data class NeteaseSong(
    val id: String,
    val name: String,
    val artists: List<String>,
    val album: String?,
    val duration: Int,
)
