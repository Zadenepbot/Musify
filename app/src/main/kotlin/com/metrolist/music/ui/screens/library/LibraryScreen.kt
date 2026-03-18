/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.TopAppBarExpandedHeight
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.utils.rememberEnumPreference


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, scrollBehavior: TopAppBarScrollBehavior) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.SONGS)
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)

    Column {
        PrimaryScrollableTabRow(
            selectedTabIndex = filterType.ordinal,
            modifier = Modifier
                .graphicsLayer {
                    translationY = scrollBehavior.state.heightOffset + TopAppBarExpandedHeight.toPx() + statusBarHeight
                }
                .zIndex(1f),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = true,
                onClick = { filterType = LibraryFilter.SONGS },
                text = { Text(text = stringResource(R.string.filter_songs)) }
            )
            Tab(
                selected = false,
                onClick = { filterType = LibraryFilter.ARTISTS },
                text = { Text(text = stringResource(R.string.filter_artists)) }
            )
            Tab(
                selected = false,
                onClick = { filterType = LibraryFilter.ALBUMS },
                text = { Text(text = stringResource(R.string.filter_albums)) }
            )
            Tab(
                selected = false,
                onClick = { filterType = LibraryFilter.PLAYLISTS },
                text = { Text(text = stringResource(R.string.filter_playlists)) }
            )
            Tab(
                selected = false,
                onClick = { filterType = LibraryFilter.PODCASTS },
                text = { Text(text = stringResource(R.string.filter_podcasts)) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (filterType) {
                LibraryFilter.LIBRARY -> LibraryMixScreen(navController)
                LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController)
                LibraryFilter.SONGS -> LibrarySongsScreen(navController)
                LibraryFilter.ALBUMS -> LibraryAlbumsScreen(navController)
                LibraryFilter.ARTISTS -> LibraryArtistsScreen(navController)
                LibraryFilter.PODCASTS -> LibraryPodcastsScreen(navController)
            }
        }
    }
}