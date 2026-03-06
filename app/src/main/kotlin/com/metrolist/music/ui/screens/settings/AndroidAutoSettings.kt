/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AndroidAutoSectionsOrderKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.PreferenceEntry
import com.metrolist.music.ui.component.PreferenceGroupTitle
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


enum class AndroidAutoSection(val id: String) {
    LIKED("liked"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
}

@Composable
fun AndroidAutoSection.label(): String = when (this) {
    AndroidAutoSection.LIKED -> stringResource(R.string.liked_songs)
    AndroidAutoSection.SONGS -> stringResource(R.string.songs)
    AndroidAutoSection.ARTISTS -> stringResource(R.string.artists)
    AndroidAutoSection.ALBUMS -> stringResource(R.string.albums)
    AndroidAutoSection.PLAYLISTS -> stringResource(R.string.playlists)
}

fun serializeSections(sections: List<Pair<AndroidAutoSection, Boolean>>): String =
    sections.joinToString(",") { (section, enabled) -> "${section.id}:$enabled" }

fun deserializeSections(raw: String): List<Pair<AndroidAutoSection, Boolean>> {
    if (raw.isBlank()) return AndroidAutoSection.values().map { it to true }
    return raw.split(",").mapNotNull { token ->
        val parts = token.split(":")
        if (parts.size != 2) return@mapNotNull null
        val section = AndroidAutoSection.values().find { it.id == parts[0] } ?: return@mapNotNull null
        val enabled = parts[1].toBooleanStrictOrNull() ?: true
        section to enabled
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAutoSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val haptic = LocalHapticFeedback.current

    val (sectionsRaw, onSectionsChange) = rememberPreference(
        key = AndroidAutoSectionsOrderKey,
        defaultValue = serializeSections(AndroidAutoSection.values().map { it to true })
    )

    var sections by remember(sectionsRaw) {
        mutableStateOf(deserializeSections(sectionsRaw))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset = 2
        val fromReal = from.index - offset
        val toReal = to.index - offset
        if (fromReal >= 0 && toReal >= 0 && fromReal < sections.size && toReal < sections.size) {
            sections = sections.toMutableList().apply {
                add(toReal, removeAt(fromReal))
            }
            onSectionsChange(serializeSections(sections))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        item(key = "header_sections") {
            PreferenceGroupTitle(
                title = stringResource(R.string.android_auto_visible_sections),
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item(key = "header_hint") {
            Text(
                text = stringResource(R.string.android_auto_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        items(sections, key = { (section, _) -> section.id }) { (section, enabled) ->
            ReorderableItem(reorderableState, key = section.id) { isDragging ->
                PreferenceEntry(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            painter = painterResource(
                                when (section) {
                                    AndroidAutoSection.LIKED -> R.drawable.favorite
                                    AndroidAutoSection.SONGS -> R.drawable.music_note
                                    AndroidAutoSection.ARTISTS -> R.drawable.artist
                                    AndroidAutoSection.ALBUMS -> R.drawable.album
                                    AndroidAutoSection.PLAYLISTS -> R.drawable.queue_music
                                }
                            ),
                            contentDescription = null,
                        )
                    },
                    title = { Text(section.label()) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = stringResource(R.string.android_auto_reorder_hint),
                                modifier = Modifier
                                    .size(24.dp)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { newValue ->
                                    sections = sections.map { (s, e) ->
                                        if (s == section) s to newValue else s to e
                                    }
                                    onSectionsChange(serializeSections(sections))
                                },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            if (enabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            )
                        }
                    },
                    onClick = {
                        sections = sections.map { (s, e) ->
                            if (s == section) s to !e else s to e
                        }
                        onSectionsChange(serializeSections(sections))
                    },
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.android_auto)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
