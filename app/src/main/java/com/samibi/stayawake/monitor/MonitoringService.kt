package com.samibi.stayawake.monitor

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import com.samibi.stayawake.R
import com.samibi.stayawake.StayAwakeApp
import com.samibi.stayawake.alarm.AlarmActivity
import com.samibi.stayawake.data.SettingsStore
import com.samibi.stayawake.diag.DiagLog
import com.samibi.stayawake.ui.MainActivity
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitoringService : LifecycleService() {

    private var detector: MotionOnlyDetector? = null
    private var motionSettings: MotionSettings = MotionSettings.DEFAULT
    private var sensorManager: SensorManager? = null
    private var accelListener: SensorEventListener? = null
    private var vibrator: Vibrator? = null
    private var screenReceiver: BroadcastReceiver? = null

    private val motionSamples = mutableListOf<Double>()
    private var motionEpochStartElapsedMs: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_MANUAL -> {
                if (!MonitorState.flow.value.running) startSession(StartReason.MANUAL)
            }
            ACTION_START_SCHEDULED -> {
                if (!MonitorState.flow.value.running) startSession(StartReason.SCHEDULED)
            }
            ACTION_STOP -> stopSession()
            ACTION_STOP_SCHEDULED -> {
                if (MonitorState.flow.value.startReason == StartReason.SCHEDULED) stopSession()
            }
            ACTION_DISMISS_ALARM -> dismissAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startSession(reason: StartReason) {
        DiagLog.start(this)
        registerScreenReceiver()

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, StayAwakeApp.CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("StayAwake")
            .setContentText("Watching for sleep onset")
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)

        OngoingActivity.Builder(applicationContext, NOTIF_ID, builder)
            .setStaticIcon(R.drawable.ic_notif)
            .setTouchIntent(contentPendingIntent)
            .build()
            .apply(applicationContext)

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIF_ID, builder.build(), type)

        MonitorState.flow.value = MonitorSnapshot(running = true, startReason = reason)

        lifecycleScope.launch {
            val settings = SettingsStore.get(this@MonitoringService).currentMotion()
            // Stopped while the settings were loading: do not start sensing.
            if (!MonitorState.flow.value.running) return@launch
            motionSettings = settings
            val newDetector = MotionOnlyDetector(settings.toConfig())
            newDetector.start(System.currentTimeMillis())
            detector = newDetector
            MonitorState.flow.value = MonitorState.flow.value.copy(stillnessSec = settings.stillnessSec.toLong())
            DiagLog.log(
                "session_start",
                "reason" to reason.name,
                "stillSec" to settings.stillnessSec,
                "confirmSec" to settings.confirmSec,
                "thresholdCenti" to settings.thresholdCenti,
                "windowSec" to settings.windowSec,
                "sampleHz" to settings.sampleHz,
            )

            registerAccelerometer()
        }

        lifecycleScope.launch {
            while (MonitorState.flow.value.running) {
                try {
                    val battery = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    DiagLog.log("battery", "pct" to level)
                } catch (e: Exception) {
                    // ignore
                }
                delay(60_000)
            }
        }
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> DiagLog.log("screen", "on" to true)
                    Intent.ACTION_SCREEN_OFF -> DiagLog.log("screen", "on" to false)
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        DiagLog.log("screen", "on" to interactive)
    }

    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver
        if (receiver != null) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }
        screenReceiver = null
    }

    private fun registerAccelerometer() {
        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

        motionSamples.clear()
        motionEpochStartElapsedMs = SystemClock.elapsedRealtime()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                motionSamples.add(sqrt((x * x + y * y + z * z).toDouble()))

                val elapsed = SystemClock.elapsedRealtime() - motionEpochStartElapsedMs
                if (elapsed >= motionSettings.windowMs) {
                    val mad = meanAbsoluteDeviation(motionSamples)
                    motionSamples.clear()
                    motionEpochStartElapsedMs = SystemClock.elapsedRealtime()
                    onWindow(mad)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accelListener = listener
        // Delivered in batches so the CPU wakes about once per window.
        manager.registerListener(
            listener,
            sensor,
            motionSettings.samplePeriodUs,
            motionSettings.maxReportLatencyUs,
        )
    }

    private var windowCount = 0

    private fun onWindow(mad: Double) {
        val d = detector ?: return
        val now = System.currentTimeMillis()
        d.onMotion(now, mad)
        val event = d.tick(now)
        if (windowCount++ % 6 == 0 || event != MotionEvent.NONE) {
            DiagLog.log("motion", "mad" to mad, "stillSec" to d.stillForMs(now) / 1000, "event" to event.name)
        }
        MonitorState.flow.value = MonitorState.flow.value.copy(stillForSec = d.stillForMs(now) / 1000L)
        when (event) {
            MotionEvent.WARN -> warnVibrate()
            MotionEvent.ALARM -> {
                MonitorState.flow.value = MonitorState.flow.value.copy(
                    alarmCause = AlarmCause.STILLNESS_ONLY,
                    alarmStillSec = (d.config.stillnessMs + d.config.confirmMs) / 1000L,
                )
                triggerAlarm()
            }
            MotionEvent.NONE -> {}
        }
    }

    private fun warnVibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 150, 200), intArrayOf(0, 120, 0, 120), -1))
    }

    private fun meanAbsoluteDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.sum() / values.size
        return values.sumOf { abs(it - mean) } / values.size
    }

    private fun triggerAlarm() {
        DiagLog.log("alarm_fired")
        MonitorState.flow.value = MonitorState.flow.value.copy(alarmActive = true)

        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 300), intArrayOf(0, 255, 0), 1)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val alarmIntent = Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1,
            alarmIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, StayAwakeApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Wake up!")
            .setContentText(
                "No movement - are you asleep?",
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        try {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                NotificationManagerCompat.from(this).notify(ALARM_NOTIF_ID, notification)
            }
        } catch (e: SecurityException) {
            // Notifications permission not granted; rely on the direct startActivity below.
        }

        try {
            startActivity(alarmIntent)
        } catch (e: Exception) {
            // best-effort
        }
    }

    private fun dismissAlarm() {
        DiagLog.log("alarm_dismissed")
        vibrator?.cancel()
        try {
            NotificationManagerCompat.from(this).cancel(ALARM_NOTIF_ID)
        } catch (e: SecurityException) {
            // ignore
        }
        MonitorState.flow.value = MonitorState.flow.value.copy(alarmActive = false)
    }

    private fun stopSession() {
        lifecycleScope.launch {
            unregisterAccelerometer()
            unregisterScreenReceiver()
            vibrator?.cancel()
            try {
                NotificationManagerCompat.from(this@MonitoringService).cancel(ALARM_NOTIF_ID)
            } catch (e: SecurityException) {
                // ignore
            }

            DiagLog.log("session_stop")
            MonitorState.flow.value = MonitorSnapshot()

            ServiceCompat.stopForeground(this@MonitoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            DiagLog.stop()
        }
    }

    private fun unregisterAccelerometer() {
        val manager = sensorManager
        val listener = accelListener
        if (manager != null && listener != null) {
            manager.unregisterListener(listener)
        }
        sensorManager = null
        accelListener = null
    }

    override fun onDestroy() {
        unregisterAccelerometer()
        unregisterScreenReceiver()
        vibrator?.cancel()
        DiagLog.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val ALARM_NOTIF_ID = 1002

        private const val ACTION_START_MANUAL = "com.samibi.stayawake.action.START_MANUAL"
        private const val ACTION_START_SCHEDULED = "com.samibi.stayawake.action.START_SCHEDULED"
        private const val ACTION_STOP = "com.samibi.stayawake.action.STOP"
        private const val ACTION_STOP_SCHEDULED = "com.samibi.stayawake.action.STOP_SCHEDULED"
        private const val ACTION_DISMISS_ALARM = "com.samibi.stayawake.action.DISMISS_ALARM"

        fun startManual(context: Context) {
            context.startForegroundService(Intent(context, MonitoringService::class.java).setAction(ACTION_START_MANUAL))
        }

        fun startScheduled(context: Context) {
            context.startForegroundService(Intent(context, MonitoringService::class.java).setAction(ACTION_START_SCHEDULED))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitoringService::class.java).setAction(ACTION_STOP))
        }

        fun stopScheduled(context: Context) {
            context.startService(Intent(context, MonitoringService::class.java).setAction(ACTION_STOP_SCHEDULED))
        }

        fun dismissAlarm(context: Context) {
            context.startService(Intent(context, MonitoringService::class.java).setAction(ACTION_DISMISS_ALARM))
        }
    }
}
