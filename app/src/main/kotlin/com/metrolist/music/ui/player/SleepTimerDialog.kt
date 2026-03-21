package com.metrolist.music.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerDefaultTypeKey
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.SleepTimer
import com.metrolist.music.playback.SleepTimer.Companion.TIME_FINISH
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SleepTimerDialog(
    playerConnection: PlayerConnection,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sleepTimerDefault by rememberPreference(SleepTimerDefaultKey, 30f)
    var sleepTimerValue by remember { mutableFloatStateOf(sleepTimerDefault) }
    val sleepTimerDefaultType by rememberPreference(SleepTimerDefaultTypeKey, SleepTimer.TIME)
    var sleepTimerType by remember {
        mutableIntStateOf(sleepTimerDefaultType.takeIf {
            it in listOf(SleepTimer.TIME, TIME_FINISH, SleepTimer.SONGS)
        } ?: SleepTimer.TIME)
    }
    val isAtDefault by remember {
        derivedStateOf { sleepTimerValue.roundToInt() == sleepTimerDefault.roundToInt() && sleepTimerDefaultType == sleepTimerType }
    }


    if (show) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = onDismiss,
            onConfirm = {
                playerConnection.service.sleepTimer.start(
                    value = sleepTimerValue.roundToInt(),
                    timerType = sleepTimerType
                )
                onDismiss()
            },
            onCancel = onDismiss,
            onReset = {
                sleepTimerValue = sleepTimerDefault
                sleepTimerType = sleepTimerDefaultType
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        OutlinedToggleButton(
                            checked = sleepTimerType == SleepTimer.TIME,
                            onCheckedChange = {
                                if (sleepTimerType == SleepTimer.SONGS) sleepTimerValue*=5
                                sleepTimerType = SleepTimer.TIME
                            },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sleep_timer_time))
                        }
                        OutlinedToggleButton(
                            checked = sleepTimerType == TIME_FINISH,
                            onCheckedChange = {
                                if (sleepTimerType == SleepTimer.SONGS) sleepTimerValue*=5
                                sleepTimerType = TIME_FINISH
                            },
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sleep_timer_time_fs))
                        }
                        OutlinedToggleButton(
                            checked = sleepTimerType == SleepTimer.SONGS,
                            onCheckedChange = {
                                if (sleepTimerType != SleepTimer.SONGS ) sleepTimerValue/=5
                                sleepTimerType = SleepTimer.SONGS
                            },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sleep_timer_songs))
                        }
                    }

                    Text(
                        text = stringResource(R.string.sleep_timer_time_fs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = pluralStringResource(
                            if (sleepTimerType == SleepTimer.SONGS) R.plurals.song else R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(8.dp))

                    val topValue = if (sleepTimerType == SleepTimer.SONGS) 24 else 120
                    val bottomValue = if (sleepTimerType == SleepTimer.SONGS) 1 else 5
                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = bottomValue.toFloat()..topValue.toFloat(),
                        steps = (topValue - bottomValue) / bottomValue - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isAtDefault) {
                            val text = stringResource(R.string.already_set_as_default)
                            Button(
                                onClick = {
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        } else {
                            val toastText =
                                if (sleepTimerType == SleepTimer.SONGS) pluralStringResource(
                                    R.plurals.sleep_timer_default_set_songs,
                                    sleepTimerValue.roundToInt(),
                                    sleepTimerValue.roundToInt()
                                ) else stringResource(R.string.sleep_timer_default_set, sleepTimerValue.roundToInt())
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        context.dataStore.edit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                            settings[SleepTimerDefaultTypeKey] = sleepTimerType
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        toastText,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        }
                    }
                }
            }
        )
    }
}