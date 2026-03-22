/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.EnableBlurEffectKey
import com.metrolist.music.utils.rememberPreference

/**
 * Default blur radius for backdrop blur effects (Material 3 Expressive style)
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
 * Native blur requires Android 12+ (API 31+) using RenderEffect
 *
 * @return true if blur is enabled and device supports it
 */
@Composable
fun isBlurEnabled(): Boolean {
    val (blurEnabled, _) = rememberPreference(EnableBlurEffectKey, defaultValue = true)
    return blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Apply backdrop blur effect to content based on user preference.
 * Uses native Android RenderEffect via Modifier.blur() when available.
 *
 * @param enabled Whether to apply blur (independent of user setting)
 * @param radius The radius of the blur effect
 * @param modifier Modifier to be applied
 * @param content The content to potentially apply blur to
 */
@Composable
fun BackdropBlurContent(
    enabled: Boolean = true,
    radius: Dp = BackdropBlurRadius,
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
 * Extension modifier to apply backdrop blur conditionally based on user settings.
 * Usage: Modifier.withBackdropBlur(radius = 20.dp)
 */
@Composable
fun Modifier.withBackdropBlur(
    enabled: Boolean = true,
    radius: Dp = BackdropBlurRadius
): Modifier {
    val blurAvailable = isBlurEnabled()
    return if (blurAvailable && enabled) {
        this.blur(radius)
    } else {
        this
    }
}

/**
 * Glass-like background modifier that applies blur and optional overlay color.
 * Creates Material 3 Expressive glassmorphism effect on overlays.
 *
 * @param enabled Whether to apply the effect
 * @param blurRadius The radius of the blur effect
 * @param overlayColor The color overlay to apply over the blurred content
 */
@Composable
fun Modifier.glassLikeBackground(
    enabled: Boolean = true,
    blurRadius: Dp = BackdropBlurRadius,
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
