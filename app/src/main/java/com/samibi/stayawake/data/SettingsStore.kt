package com.samibi.stayawake.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.samibi.stayawake.monitor.DetectorParams
import com.samibi.stayawake.schedule.ScheduleWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.dataStore

    val params: Flow<DetectorParams> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val d = DetectorParams.DEFAULT
            DetectorParams(
                hrDropPercent = (prefs[KEY_DROP] ?: d.hrDropPercent)
                    .coerceIn(DetectorParams.DROP_MIN, DetectorParams.DROP_MAX),
                hrLowSustainSec = (prefs[KEY_SUSTAIN] ?: d.hrLowSustainSec)
                    .coerceIn(DetectorParams.SUSTAIN_MIN, DetectorParams.SUSTAIN_MAX),
                stillnessSec = (prefs[KEY_STILL] ?: d.stillnessSec)
                    .coerceIn(DetectorParams.STILL_MIN, DetectorParams.STILL_MAX),
            )
        }

    val windows: Flow<List<ScheduleWindow>> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> deserializeWindows(prefs[KEY_WINDOWS].orEmpty()) }

    suspend fun setParams(p: DetectorParams) {
        store.edit { prefs ->
            prefs[KEY_DROP] = p.hrDropPercent
            prefs[KEY_SUSTAIN] = p.hrLowSustainSec
            prefs[KEY_STILL] = p.stillnessSec
        }
    }

    suspend fun setWindows(windows: List<ScheduleWindow>) {
        store.edit { prefs -> prefs[KEY_WINDOWS] = serializeWindows(windows) }
    }

    suspend fun currentParams(): DetectorParams = params.first()

    suspend fun currentWindows(): List<ScheduleWindow> = windows.first()

    companion object {
        private val KEY_DROP = intPreferencesKey("hr_drop_percent")
        private val KEY_SUSTAIN = intPreferencesKey("hr_low_sustain_sec")
        private val KEY_STILL = intPreferencesKey("stillness_sec")
        private val KEY_WINDOWS = stringPreferencesKey("windows")

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
        }

        internal fun serializeWindows(windows: List<ScheduleWindow>): String =
            windows.joinToString(";") { w ->
                val daysStr = w.days.sorted().joinToString("-")
                "${w.id},$daysStr,${w.startMin},${w.endMin},${if (w.enabled) 1 else 0}"
            }

        internal fun deserializeWindows(raw: String): List<ScheduleWindow> {
            if (raw.isEmpty()) return emptyList()
            val result = mutableListOf<ScheduleWindow>()
            for (entry in raw.split(";")) {
                if (entry.isEmpty()) continue
                try {
                    val parts = entry.split(",")
                    if (parts.size != 5) continue
                    val id = parts[0].toInt()
                    val days = if (parts[1].isEmpty()) {
                        emptySet()
                    } else {
                        parts[1].split("-").map { it.toInt() }.toSet()
                    }
                    val startMin = parts[2].toInt()
                    val endMin = parts[3].toInt()
                    val enabled = parts[4] == "1"
                    result.add(ScheduleWindow(id, days, startMin, endMin, enabled))
                } catch (e: Exception) {
                    // skip malformed entry
                }
            }
            return result
        }
    }
}
