/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_PLAYLIST
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistSortDescendingKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.PlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.constants.ShowCachedPlaylistKey
import com.metrolist.music.constants.ShowDownloadedPlaylistKey
import com.metrolist.music.constants.ShowLikedPlaylistKey
import com.metrolist.music.constants.ShowTopPlaylistKey
import com.metrolist.music.constants.ShowUploadedPlaylistKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LibraryPlaylistGridItem
import com.metrolist.music.ui.component.LibraryPlaylistListItem
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSortTypeKey,
            PlaylistSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSortDescendingKey,
            true,
        )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by viewModel.filteredPlaylists.collectAsState()
    val emptyPlaylists by viewModel.emptyPlaylists.collectAsState()
    val duplicatePlaylists by viewModel.duplicatePlaylists.collectAsState()

    val topSize by viewModel.topValue.collectAsState(initial = 50)

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusRequester = remember { FocusRequester() }

    var showCleanupMenu by remember { mutableStateOf(false) }
    var showDeleteEmptyDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicatesDialog by rememberSaveable { mutableStateOf(false) }

    val likedPlaylist =
        Playlist(
            playlist =
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = stringResource(R.string.liked),
                ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val downloadPlaylist =
        Playlist(
            playlist =
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = stringResource(R.string.offline),
                ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val topPlaylist =
        Playlist(
            playlist =
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = stringResource(R.string.my_top) + " $topSize",
                ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val uploadedPlaylist =
        Playlist(
            playlist =
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = stringResource(R.string.uploaded_playlist),
                ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val cachedPlaylist =
        Playlist(
            playlist =
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = stringResource(R.string.cached_playlist),
                ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(isLoggedIn, ytmSync) {
        if (ytmSync && isLoggedIn) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            viewModel.updateSearchQuery("")
        }
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            },
        )
    }

    if (showDeleteEmptyDialog) {
        val count = emptyPlaylists.size
        var deleteFromYouTube by remember { mutableStateOf(false) }
        LaunchedEffect(showDeleteEmptyDialog, isLoggedIn) {
            if (!isLoggedIn) deleteFromYouTube = false
        }
        DefaultDialog(
            onDismiss = { showDeleteEmptyDialog = false },
            title = { Text(stringResource(R.string.delete_empty_playlists)) },
            buttons = {
                TextButton(onClick = { showDeleteEmptyDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                if (count > 0) {
                    TextButton(onClick = {
                        viewModel.deleteEmptyPlaylists(alsoDeleteFromYouTube = deleteFromYouTube)
                        showDeleteEmptyDialog = false
                    }) {
                        Text(text = stringResource(R.string.delete))
                    }
                }
            },
        ) {
            if (count == 0) {
                Text(
                    text = stringResource(R.string.no_empty_playlists),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = pluralStringResource(R.plurals.delete_empty_playlists_confirm, count, count),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                if (isLoggedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFromYouTube = !deleteFromYouTube }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = deleteFromYouTube,
                            onCheckedChange = { deleteFromYouTube = it },
                        )
                        Text(
                            text = stringResource(R.string.also_delete_from_youtube),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(emptyPlaylists) { playlist ->
                        Text(
                            text = "• ${playlist.playlist.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDuplicatesDialog) {
        if (duplicatePlaylists.isEmpty()) {
            DefaultDialog(
                onDismiss = { showDuplicatesDialog = false },
                title = { Text(stringResource(R.string.find_duplicates)) },
                buttons = {
                    TextButton(onClick = { showDuplicatesDialog = false }) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.no_duplicate_playlists),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val grouped = remember(duplicatePlaylists) {
                duplicatePlaylists.groupBy { it.playlist.name }
            }
            val selectedForDeletion = remember { mutableStateListOf<Playlist>() }
            var deleteFromYouTube by remember { mutableStateOf(false) }
            LaunchedEffect(showDuplicatesDialog, isLoggedIn) {
                if (!isLoggedIn) deleteFromYouTube = false
            }
            val allExceptLargest = remember(grouped) {
                grouped.flatMap { (_, copies) ->
                    val largest = copies.maxByOrNull {
                        maxOf(it.songCount, it.playlist.remoteSongCount ?: 0)
                    }
                    copies.filter { it != largest }
                }
            }

            DefaultDialog(
                onDismiss = {
                    selectedForDeletion.clear()
                    showDuplicatesDialog = false
                },
                title = { Text(stringResource(R.string.duplicate_playlists)) },
                buttons = {
                    TextButton(onClick = {
                        selectedForDeletion.clear()
                        showDuplicatesDialog = false
                    }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        enabled = selectedForDeletion.isNotEmpty(),
                        onClick = {
                            viewModel.deletePlaylists(
                                selectedForDeletion.toList(),
                                alsoDeleteFromYouTube = deleteFromYouTube,
                            )
                            selectedForDeletion.clear()
                            showDuplicatesDialog = false
                        },
                    ) {
                        Text(text = stringResource(R.string.delete_selected, selectedForDeletion.size))
                    }
                },
            ) {
                if (isLoggedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFromYouTube = !deleteFromYouTube }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = deleteFromYouTube,
                            onCheckedChange = { deleteFromYouTube = it },
                        )
                        Text(
                            text = stringResource(R.string.also_delete_from_youtube),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            selectedForDeletion.clear()
                            if (selectedForDeletion.size < duplicatePlaylists.size) {
                                selectedForDeletion.addAll(duplicatePlaylists)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.select_all),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = {
                            selectedForDeletion.clear()
                            selectedForDeletion.addAll(allExceptLargest)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.select_all_except_largest),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = { selectedForDeletion.clear() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.deselect_all),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    grouped.forEach { (name, copies) ->
                        item(key = "header_$name") {
                            Text(
                                text = stringResource(R.string.duplicate_group_header, name, copies.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = copies,
                            key = { it.id },
                        ) { playlist ->
                            val isChecked = playlist in selectedForDeletion
                            val effectiveCount = maxOf(playlist.songCount, playlist.playlist.remoteSongCount ?: 0)
                            val isLargest = playlist == copies.maxByOrNull {
                                maxOf(it.songCount, it.playlist.remoteSongCount ?: 0)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedForDeletion.remove(playlist)
                                        else selectedForDeletion.add(playlist)
                                    }
                                    .padding(vertical = 2.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedForDeletion.add(playlist)
                                        else selectedForDeletion.remove(playlist)
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    val remote = playlist.playlist.remoteSongCount
                                    Text(
                                        text = if (remote != null && remote != playlist.songCount)
                                            stringResource(R.string.local_remote_songs, playlist.songCount, remote)
                                        else
                                            stringResource(R.string.n_songs, effectiveCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (playlist.playlist.browseId != null) {
                                        Text(
                                            text = stringResource(R.string.yt_browse_id, playlist.playlist.browseId!!),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (isLargest && copies.size > 1) {
                                    Text(
                                        text = stringResource(R.string.keep_largest),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val headerContent = @Composable {
        Column {
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_playlists),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp,
                                    ),
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                            PlaylistSortType.NAME -> R.string.sort_by_name
                            PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                            PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                        }
                    },
                )

                Spacer(Modifier.weight(1f))

                if (!isSearchActive) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.n_playlist,
                            playlists.size,
                            playlists.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                IconButton(
                    onClick = { isSearchActive = !isSearchActive },
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isSearchActive) R.drawable.search_off else R.drawable.search
                        ),
                        contentDescription = stringResource(
                            if (isSearchActive) {
                                R.string.close_playlist_search
                            } else {
                                R.string.open_playlist_search
                            },
                        ),
                    )
                }

                Box {
                    IconButton(
                        onClick = { showCleanupMenu = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.manage_search),
                            contentDescription = stringResource(R.string.playlist_cleanup),
                        )
                    }

                    DropdownMenu(
                        expanded = showCleanupMenu,
                        onDismissRequest = { showCleanupMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.delete_empty_playlists))
                                }
                            },
                            onClick = {
                                showCleanupMenu = false
                                showDeleteEmptyDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.content_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.find_duplicates))
                                }
                            },
                            onClick = {
                                showCleanupMenu = false
                                showDuplicatesDialog = true
                            },
                        )
                    }
                }

                IconButton(
                    onClick = {
                        viewType = viewType.toggle()
                    },
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Icon(
                        painter =
                        painterResource(
                            when (viewType) {
                                LibraryViewType.LIST -> R.drawable.list
                                LibraryViewType.GRID -> R.drawable.grid_view
                            },
                        ),
                        contentDescription = null,
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (!isSearchActive) {
                        if (showLiked) {
                            item(
                                key = "likedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistListItem(
                                    playlist = likedPlaylist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/liked")
                                            }
                                            .animateItem(),
                                )
                            }
                        }

                        if (showDownloaded) {
                            item(
                                key = "downloadedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistListItem(
                                    playlist = downloadPlaylist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/downloaded")
                                            }
                                            .animateItem(),
                                )
                            }
                        }

                        if (showCached) {
                            item(
                                key = "cachedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistListItem(
                                    playlist = cachedPlaylist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("cache_playlist/cached")
                                            }
                                            .animateItem(),
                                )
                            }
                        }

                        if (showTop) {
                            item(
                                key = "TopPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistListItem(
                                    playlist = topPlaylist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("top_playlist/$topSize")
                                            }
                                            .animateItem(),
                                )
                            }
                        }

                        if (showUploaded) {
                            item(
                                key = "uploadedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistListItem(
                                    playlist = uploadedPlaylist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/uploaded")
                                            }
                                            .animateItem(),
                                )
                            }
                        }
                    }

                    playlists.let { playlists ->
                        if (playlists.isEmpty()) {
                            item(key = "empty_placeholder") {
                            }
                        }

                        items(
                            items = playlists.distinctBy { it.id },
                            key = { "lib_playlist_list_${it.id}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { playlist ->
                            LibraryPlaylistListItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns =
                        GridCells.Adaptive(
                            minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                        ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (!isSearchActive) {
                        if (showLiked) {
                            item(
                                key = "likedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistGridItem(
                                    playlist = likedPlaylist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("auto_playlist/liked")
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        }

                        if (showDownloaded) {
                            item(
                                key = "downloadedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistGridItem(
                                    playlist = downloadPlaylist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("auto_playlist/downloaded")
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        }

                        if (showCached) {
                            item(
                                key = "cachedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistGridItem(
                                    playlist = cachedPlaylist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("cache_playlist/cached")
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        }

                        if (showTop) {
                            item(
                                key = "TopPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistGridItem(
                                    playlist = topPlaylist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("top_playlist/$topSize")
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        }

                        if (showUploaded) {
                            item(
                                key = "uploadedPlaylist",
                                contentType = { CONTENT_TYPE_PLAYLIST },
                            ) {
                                PlaylistGridItem(
                                    playlist = uploadedPlaylist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/uploaded")
                                            }
                                            .animateItem(),
                                )
                            }
                        }
                    }

                    playlists.let { playlists ->
                        if (playlists.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                            }
                        }

                        items(
                            items = playlists.distinctBy { it.id },
                            key = { "lib_playlist_grid_${it.id}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { playlist ->
                            LibraryPlaylistGridItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyGridState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }
        }
    }
}
