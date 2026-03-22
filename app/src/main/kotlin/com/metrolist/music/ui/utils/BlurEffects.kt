/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.EnableBlurEffectKey
import com.metrolist.music.utils.rememberPreference

/**
 * Default blur radius used throughout the app
 */
val DefaultBlurRadius = 20.dp

/**
 * Light blur radius for subtle effects
 */
val LightBlurRadius = 10.dp

/**
 * Heavy blur radius for strong blur effects
 */
val HeavyBlurRadius = 30.dp

/**
 * Check if blur effect is currently enabled in settings
 * Also checks Android version since native blur requires API 31+
 */
@Composable
fun isBlurEnabled(): Boolean {
    val (blurEnabled, _) = rememberPreference(EnableBlurEffectKey, defaultValue = true)
    // Native blur is only available on Android 12+ (API 31+)
    return blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Composable that conditionally applies blur effect based on user preference.
 * Uses native Android 12+ blur when available and enabled.
 *
 * @param enabled Whether to apply blur (independent of user setting)
 * @param radius The radius of the blur effect
 * @param modifier Modifier to be applied
 * @param content The content to potentially apply blur to
 */
@Composable
fun BlurredContent(
    enabled: Boolean = true,
    radius: Dp = DefaultBlurRadius,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val blurAvailable = isBlurEnabled()

    Box(
        modifier = modifier.then(
            if (blurAvailable && enabled) {
                Modifier.blur(radius)
            } else {
                Modifier
            }
        )
    ) {
        content()
    }
}

/**
 * Extension modifier to apply blur conditionally based on user settings.
 * Usage: Modifier.blurIfEnabled(radius = 20.dp)
 */
@Composable
fun Modifier.blurIfEnabled(
    enabled: Boolean = true,
    radius: Dp = DefaultBlurRadius
): Modifier {
    val blurAvailable = isBlurEnabled()
    return if (blurAvailable && enabled) {
        this.blur(radius)
    } else {
        this
    }
}

/**
 * Background modifier that applies blur and optional overlay color.
 * Useful for creating glassmorphism backgrounds on overlays.
 */
@Composable
fun Modifier.glassBackground(
    enabled: Boolean = true,
    blurRadius: Dp = DefaultBlurRadius,
    overlayColor: Color = Color.Black.copy(alpha = 0.45f)
): Modifier {
    val blurAvailable = isBlurEnabled()
    
    return this.then(
        if (blurAvailable && enabled) {
            Modifier
                .blur(blurRadius)
                .background(overlayColor)
        } else {
            Modifier.background(overlayColor)
        }
    )
}
