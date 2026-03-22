/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.EnableBlurEffectKey
import com.metrolist.music.utils.rememberPreference

/**
 * Default blur radius for backdrop blur effects
 */
val BackdropBlurRadius = 20.dp

/**
 * Subtle blur radius for light blur effects
 */
val SubtleBlurRadius = 10.dp

/**
 * Strong blur radius for prominent blur effects
 */
val ProminentBlurRadius = 30.dp

/**
 * Check if backdrop blur effect is enabled in user preferences.
 * Native blur requires Android 12+ (API 31+)
 *
 * @return true if blur is enabled and device supports it
 */
@Composable
fun isBlurEnabled(): Boolean {
    val (blurEnabled, _) = rememberPreference(EnableBlurEffectKey, defaultValue = true)
    return blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
