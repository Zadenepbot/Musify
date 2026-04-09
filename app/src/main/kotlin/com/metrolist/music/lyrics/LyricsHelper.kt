/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.PreferredLyricsProvider
import com.metrolist.music.constants.PreferredLyricsProviderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 30000L
private const val PROVIDER_NONE = ""

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private var lyricsProviders =
        listOf(
            BetterLyricsProvider,
            PaxsenixLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            LyricsPlusProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider
        )

    val preferred =
        context.dataStore.data
            .map { preferences ->
                val providerOrder = preferences[LyricsProviderOrderKey] ?: ""
                if (providerOrder.isNotBlank()) {
                    // Use the new provider order if available
                    LyricsProviderRegistry.getOrderedProviders(providerOrder)
                } else {
                    // Fall back to preferred provider logic for backward compatibility
                    val preferredProvider = preferences[PreferredLyricsProviderKey]
                        .toEnum(PreferredLyricsProvider.LRCLIB)
                    when (preferredProvider) {
                        PreferredLyricsProvider.LRCLIB -> listOf(
                            LrcLibLyricsProvider,
                            BetterLyricsProvider,
                            PaxsenixLyricsProvider,
                            KuGouLyricsProvider,
                            LyricsPlusProvider,
                            YouTubeSubtitleLyricsProvider,
                            YouTubeLyricsProvider
                        )
                        PreferredLyricsProvider.KUGOU -> listOf(
                            KuGouLyricsProvider,
                            BetterLyricsProvider,
                            PaxsenixLyricsProvider,
                            LrcLibLyricsProvider,
                            LyricsPlusProvider,
                            YouTubeSubtitleLyricsProvider,
                            YouTubeLyricsProvider
                        )
                        PreferredLyricsProvider.BETTER_LYRICS -> listOf(
                            BetterLyricsProvider,
                            PaxsenixLyricsProvider,
                            LrcLibLyricsProvider,
                            KuGouLyricsProvider,
                            LyricsPlusProvider,
                            YouTubeSubtitleLyricsProvider,
                            YouTubeLyricsProvider
                        )
                        PreferredLyricsProvider.PAXSENIX -> listOf(
                            PaxsenixLyricsProvider,
                            BetterLyricsProvider,
                            LrcLibLyricsProvider,
                            KuGouLyricsProvider,
                            LyricsPlusProvider,
                            YouTubeSubtitleLyricsProvider,
                            YouTubeLyricsProvider
                        )
                    }
                }
            }.distinctUntilChanged()
            .map { providers ->
                lyricsProviders = providers
            }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = lyricsProviders.filter { it.isEnabled(context) }

            // Launch ALL providers concurrently, indexed so we can sort by priority later
            val deferredResults = enabledProviders.mapIndexed { index, provider ->
                async {
                    try {
                        Timber.tag("LyricsHelper")
                            .d("Trying provider concurrently: ${provider.name} for $cleanedTitle")
                        val providerResult = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
                            provider.getLyrics(
                                context,
                                mediaMetadata.id,
                                cleanedTitle,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                        }
                        when {
                            providerResult?.isSuccess == true -> {
                                Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                                Triple(index, provider, providerResult)
                            }
                            providerResult == null -> {
                                Timber.tag("LyricsHelper").w("${provider.name} timed out")
                                null
                            }
                            else -> {
                                Timber.tag("LyricsHelper")
                                    .w("${provider.name} failed: ${providerResult.exceptionOrNull()?.message}")
                                null
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("LyricsHelper").w("${provider.name} threw exception: ${e.message}")
                        null
                    }
                }
            }

            // Wait for all providers to finish, then pick the highest-priority success
            val successResults = deferredResults.awaitAll().filterNotNull()
            val best = successResults.minByOrNull { it.first } // lowest index = highest priority

            if (best != null) {
                val (_, provider, providerResult) = best
                val filteredLyrics = LyricsUtils.filterLyricsCreditLines(providerResult.getOrNull()!!)
                LyricsWithProvider(filteredLyrics, provider.name)
            } else {
                Timber.tag("LyricsHelper").w("All providers failed for ${mediaMetadata.title}")
                LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
            }
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }

        if (!isNetworkAvailable) return // Still try to proceed in case of false negative

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val enabledProviders = lyricsProviders.filter { it.isEnabled(context) }

            // Fetch from all providers concurrently; callback fires as each one finishes
            val jobs = enabledProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            synchronized(allResult) { allResult += result }
                            callback(result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            jobs.forEach { it.join() }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
