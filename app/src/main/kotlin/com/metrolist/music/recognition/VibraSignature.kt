package com.metrolist.music.recognition

import timber.log.Timber

/**
 * Native library interface for generating Shazam-compatible audio fingerprints.
 * Uses the vibra_fp library which implements the Shazam signature algorithm.
 */
object VibraSignature {

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("vibra_fp")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Timber.e("Native library vibra_fp not found. Music recognition will be disabled.")
        }
    }

    const val REQUIRED_SAMPLE_RATE = 16_000

    /**
     * Generates a Shazam signature from PCM audio data.
     * 
     * @param samples Raw PCM audio data (mono, 16-bit signed, 16kHz sample rate)
     * @return The encoded signature string suitable for Shazam API
     * @throws RuntimeException if signature generation fails or library is not loaded
     */
    @JvmStatic
    fun fromI16(samples: ByteArray): String {
        if (!isLibraryLoaded) {
            throw RuntimeException("Music recognition library is not available on this build.")
        }
        return nativeFromI16(samples)
    }

    @JvmStatic
    private external fun nativeFromI16(samples: ByteArray): String
}
