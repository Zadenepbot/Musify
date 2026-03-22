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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisbanes.haze.HazeEffect
import com.chrisbanes.haze.HazeState
import com.chrisbanes.haze.haze
import com.chrisbanes.haze.hazeChild
import com.metrolist.music.constants.EnableBlurEffectKey
import com.metrolist.music.utils.rememberPreference

/**
 * Default blur intensity for backdrop blur effects
 */
val BackdropBlurIntensity = 0.7f

/**
 * Subtle blur intensity for light blur effects
 */
val SubtleBlurIntensity = 0.4f

/**
 * Strong blur intensity for prominent blur effects
 */
val ProminentBlurIntensity = 1.0f

/**
 * Check if haze blur effect is enabled in user preferences.
 * Haze uses RenderEffect which requires Android 12+ (API 31+)
 *
 * @return true if blur is enabled and device supports it
 */
@Composable
fun isBlurEnabled(): Boolean {
    val (blurEnabled, _) = rememberPreference(EnableBlurEffectKey, defaultValue = true)
    return blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Creates and remembers a haze state for blur effects.
 * Use this to control the blur state across composables.
 */
@Composable
fun rememberBlurState(): HazeState {
    return remember { HazeState() }
}

/**
 * Apply Haze blur effect to content based on user preference.
 * Uses the Haze library for Pixel-style Expressive blur effect.
 *
 * @param enabled Whether to apply blur
 * @param intensity The intensity of the blur effect (0.0 to 1.0)
 * @param blurRadius The radius of the blur effect
 * @param tint The tint color to apply over the blurred background
 * @param content The content to apply blur to
 */
@Composable
fun HazeBlurContent(
    enabled: Boolean = true,
    intensity: Float = BackdropBlurIntensity,
    blurRadius: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
    content: @Composable BoxScope.() -> Unit
) {
    val blurAvailable = isBlurEnabled()
    val hazeState = rememberBlurState()

    Box(
        modifier = if (blurAvailable && enabled) {
            Modifier.haze(
                state = hazeState,
                effect = HazeEffect(
                    intensity = intensity,
                    blurRadius = blurRadius,
                    tint = tint
                )
            )
        } else {
            Modifier
        }
    ) {
        content()
    }
}

/**
 * Apply Haze blur to a specific composable (hazes its background).
 * This is the main modifier for creating glassmorphism effects.
 *
 * @param state HazeState for the blur effect
 * @param intensity The intensity of the blur effect
 * @param blurRadius The radius of the blur
 * @param tint The tint color over the blur
 */
fun Modifier.hazeBackground(
    state: HazeState = remember { HazeState() },
    intensity: Float = BackdropBlurIntensity,
    blurRadius: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
): Modifier {
    return this.then(
        Modifier.haze(
            state = state,
            effect = HazeEffect(
                intensity = intensity,
                blurRadius = blurRadius,
                tint = tint
            )
        )
    )
}

/**
 * Apply haze effect to child content (blur behind this composable).
 * This is used inside a HazeContainer.
 *
 * @param state HazeState to reference the parent haze effect
 */
fun Modifier.hazeBackgroundChild(
    state: HazeState
): Modifier {
    return this.hazeChild(state)
}

/**
 * Extension modifier to apply Haze blur conditionally based on user settings.
 * Usage: Modifier.withHazeBlur(radius = 20.dp)
 *
 * @param enabled Whether to apply the blur effect
 * @param intensity The intensity of the blur
 * @param blurRadius The radius of the blur
 */
@Composable
fun Modifier.withHazeBlur(
    enabled: Boolean = true,
    intensity: Float = BackdropBlurIntensity,
    blurRadius: Dp = 20.dp
): Modifier {
    val blurAvailable = isBlurEnabled()
    val hazeState = rememberBlurState()

    return if (blurAvailable && enabled) {
        this.haze(
            state = hazeState,
            effect = HazeEffect(
                intensity = intensity,
                blurRadius = blurRadius,
                tint = Color.Black.copy(alpha = 0.3f)
            )
        )
    } else {
        this
    }
}

/**
 * Glassmorphism-style background modifier using Haze.
 * Creates a frosted glass effect similar to Material 3 Expressive.
 *
 * @param enabled Whether to apply the effect
 * @param blurRadius The radius of the blur
 * @param tint The overlay tint color
 */
@Composable
fun Modifier.glassLikeBackground(
    enabled: Boolean = true,
    blurRadius: Dp = 20.dp,
    tint: Color = Color.Black.copy(alpha = 0.45f)
): Modifier {
    val blurAvailable = isBlurEnabled()

    return if (blurAvailable && enabled) {
        this.then(
            Modifier.haze(
                effect = HazeEffect(
                    intensity = 0.6f,
                    blurRadius = blurRadius,
                    tint = tint
                )
            )
        )
    } else {
        this.background(tint)
    }
}