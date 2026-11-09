package com.samibi.stayawake.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.samibi.stayawake.data.SettingsStore
import com.samibi.stayawake.monitor.MotionSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore.get(context) }
    val current by store.motion.collectAsStateWithLifecycle(initialValue = MotionSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    fun save(m: MotionSettings) {
        scope.launch { store.setMotion(m) }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            item {
                ParamRow(
                    title = "Warn after no movement",
                    hint = "Seconds without wrist movement before a gentle double buzz.",
                    value = "${current.stillnessSec}s",
                    onMinus = {
                        save(current.copy(stillnessSec = (current.stillnessSec - 5).coerceAtLeast(MotionSettings.STILL_MIN)))
                    },
                    onPlus = {
                        save(current.copy(stillnessSec = (current.stillnessSec + 5).coerceAtMost(MotionSettings.STILL_MAX)))
                    },
                )
            }
            item {
                ParamRow(
                    title = "Alarm after warning",
                    hint = "Seconds more without movement before the alarm sounds.",
                    value = "${current.confirmSec}s",
                    onMinus = {
                        save(current.copy(confirmSec = (current.confirmSec - 5).coerceAtLeast(MotionSettings.CONFIRM_MIN)))
                    },
                    onPlus = {
                        save(current.copy(confirmSec = (current.confirmSec + 5).coerceAtMost(MotionSettings.CONFIRM_MAX)))
                    },
                )
            }
            item {
                Text(
                    text = "Applies on next session start",
                    style = MaterialTheme.typography.caption3,
                )
            }
            item {
                Chip(
                    label = { Text("Advanced settings") },
                    onClick = { navController.navigate("advanced") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Schedule") },
                    onClick = { navController.navigate("schedule") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The alarm channel asks to bypass Do Not Disturb (sleep/bedtime mode),
            // but the system only honors that once the user grants DND access here.
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                item {
                    Chip(
                        label = { Text("Allow alarm in sleep mode") },
                        secondaryLabel = { Text("Grant Do Not Disturb access") },
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                )
                            } catch (_: Exception) {
                                // Settings screen unavailable on this build; nothing to do.
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ParamRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    hint: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = title, style = MaterialTheme.typography.caption2)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(label = { Text("−") }, onClick = onMinus)
            Text(text = value, style = MaterialTheme.typography.title3)
            CompactChip(label = { Text("+") }, onClick = onPlus)
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
            )
        }
    }
}
