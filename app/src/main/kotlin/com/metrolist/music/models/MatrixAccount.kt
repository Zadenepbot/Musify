/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.models

import kotlinx.serialization.Serializable

@Serializable
data class MatrixAccount(
    val homeserver: String,
    val userId: String,
) {
    override fun toString(): String {
        return "MatrixAccount(homeserver='$homeserver', userId='$userId')"
    }
}
