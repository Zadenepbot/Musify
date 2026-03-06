package com.metrolist.music.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.db.entities.SongWithStats
import com.metrolist.music.viewmodels.StatsViewModel
import java.util.concurrent.TimeUnit

@Composable
fun TimeTransfer(
    onDismiss: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val sourceSong = remember { mutableStateOf<SongWithStats?>(null) }
    val targetSong = remember { mutableStateOf<SongWithStats?>(null) }

    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsState()

    DefaultDialog(
        onDismiss = onDismiss,
        title = {Text("Time Transfer", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        content = {
            Text("WARNING: It is not possible to revert this action once it is completed. A backup file should be created before proceeding.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = androidx.compose.ui.graphics.Color.Red)

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                SongSelectDropdown(
                    titleT = "Source Song",
                    songs = mostPlayedSongsStats,
                    onSelectionChange = {},
                    selectedSong = sourceSong
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Text("Listen Time: ")
                    if (sourceSong.value != null) {
                        Text(formatMillis(sourceSong.value!!.timeListened), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))


                SongSelectDropdown(
                    titleT = "Target Song",
                    songs = mostPlayedSongsStats,
                    onSelectionChange = {},
                    selectedSong = targetSong,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Text("Listen Time: ")
                    if (targetSong.value != null) {
                        Text(formatMillis(targetSong.value!!.timeListened), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                IconButton(
                    onClick = {
                        val from = sourceSong.value?.id
                        val to = targetSong.value?.id
                        if (from != null && to != null && from != to) {
                            viewModel.transferSongStats(from, to) {
                                // optional: clear selection / close dialog
                                sourceSong.value = null
                                targetSong.value = null
                                onDismiss()
                            }
                        }
                    },
                    onLongClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sourceSong.value != null &&
                            targetSong.value != null &&
                            sourceSong.value!!.id != targetSong.value!!.id,
                    content = {
                        if (sourceSong.value != null && targetSong.value != null) {
                            Text("Convert", color = androidx.compose.ui.graphics.Color.White)
                        } else {
                            Text("Convert", color = androidx.compose.ui.graphics.Color.Transparent)
                        }
                    }
                )
            }
        }

    )
}

@SuppressLint("DefaultLocale")
fun formatMillis(ms: Long?): String {
    if (ms == null) {
        return "00:00:00"
    }
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}