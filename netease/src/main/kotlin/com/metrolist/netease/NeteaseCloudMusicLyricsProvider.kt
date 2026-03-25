package com.metrolist.netease

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlin.math.abs
import timber.log.Timber
import java.security.MessageDigest
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalSerializationApi::class, ExperimentalCoroutinesApi::class)
object NeteaseCloudMusicLyricsProvider {
    // 提供获取歌词的功能，不实现 LyricsProvider 接口
    // 由 app 模块的 wrapper 负责接口适配

    private const val OFFICIAL_API_BASE_URL = "https://interface.music.163.com"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_MAGIC = "36cd479b6b5"
    private const val DURATION_TOLERANCE = 8000

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

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        Timber.tag("NeteaseProvider").i("getLyrics called: title='$title', artist='$artist', duration=$duration, album=$album")
        val songId = findSongId(title, artist, duration) ?: throw IllegalStateException("No matching song found on Netease")
        Timber.tag("NeteaseProvider").i("Found songId: $songId")
        val result = fetchLyrics(songId)
        buildString {
            append(result.lyric)
            if (!result.tlyric.isNullOrBlank()) {
                append("\n\n[translate]\n")
                append(result.tlyric)
            }
        }.also { combined ->
            Timber.tag("NeteaseProvider").i("Returning lyrics: length=${combined.length}")
        }
    }

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Timber.tag("NeteaseProvider").i("getAllLyrics called: title='$title', artist='$artist', duration=$duration")
        try {
            val songId = findSongId(title, artist, duration)
            if (songId != null) {
                Timber.tag("NeteaseProvider").i("Found songId: $songId")
                val result = fetchLyrics(songId)
                val combined = buildString {
                    append(result.lyric)
                    if (!result.tlyric.isNullOrBlank()) {
                        append("\n\n[translate]\n")
                        append(result.tlyric)
                    }
                }
                Timber.tag("NeteaseProvider").i("Calling callback with lyrics: length=${combined.length}")
                callback(combined)
            } else {
                Timber.tag("NeteaseProvider").w("No songId found")
            }
        } catch (e: Exception) {
            Timber.tag("NeteaseProvider").e(e, "getAllLyrics failed")
            // Ignore and continue
        }
    }

    private data class NeteaseLyricsResult(
        val lyric: String,
        val tlyric: String? = null,
    )

    private data class NeteaseSong(
        val id: String,
        val name: String,
        val artists: List<String>,
        val album: String?,
        val duration: Int,
    )

    private suspend fun findSongId(title: String, artist: String, duration: Int): String? {
        val searchQuery = buildString {
            append(title)
            append(" ")
            append(artist)
        }
        Timber.tag("NeteaseProvider").w("FIND_SONG_ID: title='$title', artist='$artist', duration=$duration, query='$searchQuery'")

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
            val code = (jsonObj.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            Timber.tag("NeteaseProvider").d("Search response code: $code")
            
            if (code == 200) {
                val result = jsonObj.get("result")?.jsonObject ?: return null
                val songs = result.get("songs")?.jsonArray ?: return null

                val songList = songs.mapNotNull { songObj ->
                    val song = songObj.jsonObject
                    val id = (song.get("id") as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val name = (song.get("name") as? JsonPrimitive)?.content ?: ""
                    val artists = song.get("artists")?.jsonArray?.map { 
                        (it.jsonObject.get("name") as? JsonPrimitive)?.content ?: "" 
                    } ?: emptyList()
                    val album = song.get("album")?.jsonObject
                    val albumName = (album?.get("name") as? JsonPrimitive)?.content
                    val durationMs = (song.get("duration") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

                    NeteaseSong(id, name, artists, albumName, durationMs)
                }

                Timber.tag("NeteaseProvider").d("Found ${songList.size} songs")
                val bestMatch = songList
                    .filter { abs(it.duration - duration) <= DURATION_TOLERANCE }
                    .minByOrNull { abs(it.duration - duration) }

                val chosen = bestMatch ?: songList.firstOrNull()
                Timber.tag("NeteaseProvider").d("Selected songId: ${chosen?.id}, name='${chosen?.name}', duration=${chosen?.duration}")
                chosen?.id
            } else {
                Timber.tag("NeteaseProvider").w("Search failed with code: $code")
                null
            }
        } catch (e: Exception) {
            Timber.tag("NeteaseProvider").e(e, "Search exception")
            null
        }
    }

    private suspend fun fetchLyrics(songId: String): NeteaseLyricsResult {
        Timber.tag("NeteaseProvider").d("Fetching lyrics for songId: $songId")
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
        val status = (jsonObj.get("status") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        Timber.tag("NeteaseProvider").d("Lyrics response status: $status")
        val body = jsonObj.get("body")?.jsonObject ?: throw IllegalStateException("No body in response")

        val code = (body.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        if (code == 200) {
            val lyric = (body.get("lyric") as? JsonPrimitive)?.content
                ?: throw IllegalStateException("No lyric content")
            val tlyric = (body.get("tlyric") as? JsonPrimitive)?.content
            val lyricPreview = lyric.take(200).replace("\n", "\\n")
            Timber.tag("NeteaseProvider").d("Lyrics fetched successfully, length=${lyric.length}, tlyric=${tlyric?.length ?: 0}, preview: $lyricPreview")
            return NeteaseLyricsResult(lyric, tlyric)
        } else {
            Timber.tag("NeteaseProvider").w("Failed to fetch lyrics: code $code")
            throw IllegalStateException("Failed to fetch lyrics: code $code")
        }
    }

    private suspend fun eapiRequest(path: String, data: Map<String, Any>): JsonElement {
        val url = "$OFFICIAL_API_BASE_URL/eapi$path"
        Timber.tag("NeteaseProvider").d("EAPI Request URL: $url")

        // Build eapi encrypted params with header (matching api-enhanced)
        // Construct header as required by Netease eapi protocol
        val header = buildEapiHeader()
        Timber.tag("NeteaseProvider").d("Constructed header: $header")

        // Build JSON manually to avoid serialization issues with Map<String, Any>
        val jsonData = buildJsonObject {
            // Copy all data entries
            data.forEach { (key, value) ->
                when (value) {
                    is String -> add(key, JsonPrimitive(value))
                    is Number -> add(key, JsonPrimitive(value))
                    is Boolean -> add(key, JsonPrimitive(value))
                    else -> throw IllegalArgumentException("Unsupported value type: ${value::class} for key '$key'")
                }
            }
            // Add header object
            add("header", buildJsonObject {
                header.forEach { (key, value) ->
                    add(key, JsonPrimitive(value))
                }
            })
        }.toString()

        Timber.tag("NeteaseProvider").d("Data with header (JSON): $jsonData")

        val message = "nobody${path}use${jsonData}md5forencrypt"
        Timber.tag("NeteaseProvider").d("Sign message: $message")

        val digest = md5(message)
        Timber.tag("NeteaseProvider").d("MD5 digest: $digest")

        val eapiData = "$path-$EAPI_MAGIC-$jsonData-$EAPI_MAGIC-$digest"
        Timber.tag("NeteaseProvider").d("EAPI data before encryption: $eapiData")

        val encryptedParams = aesEncrypt(eapiData, EAPI_KEY)
        Timber.tag("NeteaseProvider").d("Encrypted params (hex, first 100 chars): ${encryptedParams.take(100)}...")

        // Send request as raw body
        val response = client.post(url) {
            userAgent("NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14; 23013RK75C Build/UKQ1.230804.001)")
            setBody(encryptedParams)
        }

        val responseBody = response.body<String>()
        Timber.tag("NeteaseProvider").d("Response body (first 200 chars): ${responseBody.take(200)}")

        return Json.parseToJsonElement(responseBody)
    }

    private fun buildEapiHeader(): Map<String, String> {
        val random = java.util.Random()
        val deviceId = ByteArray(32).apply { random.nextBytes(this) }.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val requestId = System.currentTimeMillis().toString() + random.nextInt(1000)
        val buildVer = (System.currentTimeMillis() / 1000).toString().substring(0, 10)

        val header = linkedMapOf(
            "osver" to "14",
            "deviceId" to deviceId,
            "os" to "android",
            "appver" to "8.20.20.231215173437",
            "versioncode" to "140",
            "mobilename" to "",
            "buildver" to buildVer,
            "resolution" to "1920x1080",
            "__csrf" to "",
            "channel" to "netease",
            "requestId" to requestId
        )
        Timber.tag("NeteaseProvider").d("Built eapi header: $header")
        return header
    }

    private fun aesEncrypt(input: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
        val result = bytesToHex(encrypted)
        Timber.tag("NeteaseProvider").d("AES-ECB encrypt: inputLen=${input.length}, outputLen=${result.length}")
        return result
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        val result = bytesToHex(digest)
        Timber.tag("NeteaseProvider").d("MD5: input='$input' -> $result")
        return result
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
