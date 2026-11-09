package com.samibi.stayawake.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.samibi.stayawake.data.SettingsStore
import com.samibi.stayawake.monitor.MotionSettings
import kotlinx.coroutines.launch

@Composable
fun AdvancedSettingsScreen(@Suppress("UNUSED_PARAMETER") navController: NavHostController) {
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
            item { Text(text = "Advanced", style = MaterialTheme.typography.title3) }
            item {
                ParamRow(
                    title = "Movement threshold",
                    hint = "Smallest wrist motion that counts as movement. Higher ignores small motions, so alarms come sooner.",
                    value = "%.2f".format(current.thresholdCenti / 100.0),
                    onMinus = { save(current.copy(thresholdCenti = current.thresholdCenti - 1).coerced()) },
                    onPlus = { save(current.copy(thresholdCenti = current.thresholdCenti + 1).coerced()) },
                )
            }
            item {
                ParamRow(
                    title = "Measurement window",
                    hint = "Movement is checked once per window. Longer saves battery but reacts more slowly.",
                    value = "${current.windowSec}s",
                    onMinus = { save(current.copy(windowSec = current.windowSec - 1).coerced()) },
                    onPlus = { save(current.copy(windowSec = current.windowSec + 1).coerced()) },
                )
            }
            item {
                ParamRow(
                    title = "Sensor samples per second",
                    hint = "Sensor readings per second. Fewer saves battery but is less precise.",
                    value = "${current.sampleHz}",
                    onMinus = { save(current.copy(sampleHz = current.sampleHz - 1).coerced()) },
                    onPlus = { save(current.copy(sampleHz = current.sampleHz + 1).coerced()) },
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
                    label = { Text("Reset all to defaults") },
                    onClick = { save(MotionSettings.DEFAULT) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
