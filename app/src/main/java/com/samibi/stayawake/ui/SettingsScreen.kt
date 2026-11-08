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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.samibi.stayawake.data.SettingsStore
import com.samibi.stayawake.monitor.DetectorParams
import com.samibi.stayawake.monitor.Sensitivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore.get(context) }
    val current by store.params.collectAsStateWithLifecycle(initialValue = DetectorParams.DEFAULT)
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    fun save(p: DetectorParams) {
        scope.launch { store.setParams(p) }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            item { Text(text = "Presets", style = MaterialTheme.typography.title3) }
            items(Sensitivity.values().toList()) { s ->
                val preset = DetectorParams.forSensitivity(s)
                Chip(
                    label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    secondaryLabel = {
                        Text("-${preset.hrDropPercent}% / ${preset.hrLowSustainSec}s / ${preset.stillnessSec}s")
                    },
                    onClick = { save(preset) },
                    colors = if (current == preset) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Text(text = "Custom", style = MaterialTheme.typography.title3) }
            item {
                ParamRow(
                    title = "HR drop below baseline",
                    value = "${current.hrDropPercent}%",
                    onMinus = {
                        save(current.copy(hrDropPercent = (current.hrDropPercent - 1).coerceAtLeast(DetectorParams.DROP_MIN)))
                    },
                    onPlus = {
                        save(current.copy(hrDropPercent = (current.hrDropPercent + 1).coerceAtMost(DetectorParams.DROP_MAX)))
                    },
                )
            }
            item {
                ParamRow(
                    title = "HR must stay low for",
                    value = "${current.hrLowSustainSec}s",
                    onMinus = {
                        save(current.copy(hrLowSustainSec = (current.hrLowSustainSec - 5).coerceAtLeast(DetectorParams.SUSTAIN_MIN)))
                    },
                    onPlus = {
                        save(current.copy(hrLowSustainSec = (current.hrLowSustainSec + 5).coerceAtMost(DetectorParams.SUSTAIN_MAX)))
                    },
                )
            }
            item {
                ParamRow(
                    title = "Wrist still for",
                    value = "${current.stillnessSec}s",
                    onMinus = {
                        save(current.copy(stillnessSec = (current.stillnessSec - 5).coerceAtLeast(DetectorParams.STILL_MIN)))
                    },
                    onPlus = {
                        save(current.copy(stillnessSec = (current.stillnessSec + 5).coerceAtMost(DetectorParams.STILL_MAX)))
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
private fun ParamRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
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
    }
}
