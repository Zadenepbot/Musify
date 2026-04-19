/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GoogleTranslateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun translate(
        text: String,
        targetLanguage: String,
        maxRetries: Int = 3
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Input text is empty"))
        }

        val lines = text.lines()
        val translated = lines.map { line ->
            if (line.isBlank()) {
                ""
            } else {
                translateLineWithRetry(line, targetLanguage, maxRetries)
            }
        }
        Result.success(translated)
    }

    private suspend fun translateLineWithRetry(
        line: String,
        targetLanguage: String,
        maxRetries: Int
    ): String {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < maxRetries) {
            try {
                return translateLine(line, targetLanguage)
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    delay(500L * attempt)
                }
            }
        }

        Timber.w(lastError, "Google translation failed after retries, returning original line")
        return line
    }

    private fun translateLine(
        line: String,
        targetLanguage: String
    ): String {
        val encoded = URLEncoder.encode(line, Charsets.UTF_8.name())
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dt=t&q=$encoded"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return line

            val root = JSONArray(body)
            val segments = root.optJSONArray(0) ?: return line
            val builder = StringBuilder()

            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i) ?: continue
                builder.append(segment.optString(0, ""))
            }

            return builder.toString().ifBlank { line }
        }
    }
}
