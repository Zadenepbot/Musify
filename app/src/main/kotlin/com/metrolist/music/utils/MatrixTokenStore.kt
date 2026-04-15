/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object MatrixTokenStore {
    private const val PREFS_FILE = "matrix_tokens"
    private const val TAG = "MatrixTokenStore"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences? {
        val cached = prefs
        if (cached != null) {
            return cached
        }

        return synchronized(this) {
            val recheck = prefs
            if (recheck != null) {
                recheck
            } else {
                try {
                    val appContext = context.applicationContext
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val created = EncryptedSharedPreferences.create(
                        appContext,
                        PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    prefs = created
                    created
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
                    null
                }
            }
        }
    }

    private fun createKey(homeserver: String, userId: String): String {
        return "${homeserver.length}:$homeserver|${userId.length}:$userId"
    }

    fun saveToken(context: Context, homeserver: String, userId: String, token: String) {
        val sharedPrefs = getPrefs(context)
        if (sharedPrefs == null) {
            Log.w(TAG, "saveToken: encrypted storage unavailable, token not persisted")
            return
        }
        sharedPrefs.edit().putString(createKey(homeserver, userId), token).apply()
    }

    fun getToken(context: Context, homeserver: String, userId: String): String? {
        val sharedPrefs = getPrefs(context)
        if (sharedPrefs == null) {
            Log.w(TAG, "getToken: encrypted storage unavailable, returning null")
            return null
        }
        return sharedPrefs.getString(createKey(homeserver, userId), null)
    }

    fun removeToken(context: Context, homeserver: String, userId: String) {
        val sharedPrefs = getPrefs(context)
        if (sharedPrefs == null) {
            Log.w(TAG, "removeToken: encrypted storage unavailable, nothing to remove")
            return
        }
        sharedPrefs.edit().remove(createKey(homeserver, userId)).apply()
    }
}
