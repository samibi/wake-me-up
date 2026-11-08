package com.samibi.stayawake.diag

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Lightweight diagnostic "flight recorder". Appends one JSON object per line
 * (JSONL) describing raw signals seen during a monitoring session, so a
 * session can be pulled with `adb pull` and inspected offline.
 *
 * All IO is best-effort: any failure is swallowed so logging can never crash
 * the app or interfere with monitoring.
 */
object DiagLog {

    private const val MAX_SESSIONS = 20

    private val lock = Any()
    private var writer: BufferedWriter? = null

    fun start(context: Context) {
        synchronized(lock) {
            try {
                closeLocked()

                val dir = context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
                dir.mkdirs()

                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "session-$stamp.jsonl")
                writer = BufferedWriter(FileWriter(file, true))

                pruneOldSessionsLocked(dir)
            } catch (e: Exception) {
                writer = null
            }
        }
    }

    fun log(event: String, vararg fields: Pair<String, Any?>) {
        synchronized(lock) {
            val w = writer ?: return
            try {
                val json = JSONObject()
                json.put("t", System.currentTimeMillis())
                json.put("e", event)
                for ((key, value) in fields) {
                    if (value == null) continue
                    json.put(key, value)
                }
                w.write(json.toString())
                w.newLine()
                w.flush()
            } catch (e: Exception) {
                // swallow — logging must never crash the app.
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            writer = null
        }
    }

    private fun pruneOldSessionsLocked(dir: File) {
        try {
            val files = dir.listFiles { f -> f.isFile && f.name.startsWith("session-") && f.name.endsWith(".jsonl") }
                ?: return
            if (files.size <= MAX_SESSIONS) return
            files.sortedByDescending { it.lastModified() }
                .drop(MAX_SESSIONS)
                .forEach { it.delete() }
        } catch (e: Exception) {
            // ignore
        }
    }
}
