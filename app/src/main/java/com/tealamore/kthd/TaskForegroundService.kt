package com.tealamore.kthd

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.RequestSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TaskForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var pollingJob: Job? = null
    private var lastPollTime = System.currentTimeMillis()
    private var currentForegroundPackage: String? = null
    private var currentAppStartTime = System.currentTimeMillis()
    private var thresholdTriggered = false
    private var currentTaskId: Long = -1
    private var appSwitchCount = 0

    // Reward monitoring starts only once the backend has responded to the context request.
    private val contextResponseHandler = ContextResponseHandler(
        showNotification = { pending -> showThresholdNotification(pending.appName) },
        startRewardMonitoring = { pending ->
            startRewardMonitoring(
                targetPackage = pending.targetPackage,
                contextId = pending.contextId,
                taskId = pending.taskId,
                deviceId = pending.deviceId
            )
        }
    )

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            RequestSyncManager.contextResponses.collect { contextResponseHandler.onResponse(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val taskLabel = intent?.getStringExtra(EXTRA_TASK_LABEL) ?: "Active Task"
        currentTaskId = intent?.getLongExtra(EXTRA_TASK_ID, -1) ?: -1
        createNotificationChannel()
        val notification = createNotification(taskLabel)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Never collect data without consent (e.g. a sticky restart after the participant
        // deleted their data). startForeground must still be called first to avoid a crash.
        if (!ConsentManager.hasConsented(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (pollingJob == null || pollingJob?.isCompleted == true) {
            startPolling(taskLabel)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contextResponseHandler.clear()
        serviceJob.cancel()
    }

    private fun startPolling(taskLabel: String) {
        pollingJob = serviceScope.launch {
            while (true) {
                pollUsageEvents(taskLabel)
                delay(20.seconds) // Poll every 20 seconds
            }
        }
    }

    private fun pollUsageEvents(taskLabel: String) {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isDeviceLocked

        if (isLocked) {
            currentForegroundPackage = null
            thresholdTriggered = false
            updateNotification(taskLabel, "Device Locked", null)
            lastPollTime = System.currentTimeMillis()
            return
        }

        if (!isScreenOn) {
            // Effectively pause polling - don't process events, just keep lastPollTime current
            updateNotification(taskLabel, "Screen Off", null)
            lastPollTime = System.currentTimeMillis()
            return
        }

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastPollTime, endTime)
        val event = UsageEvents.Event()

        val resumeEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        
        val pauseEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resumeEvent) {
                if (event.packageName != currentForegroundPackage) {
                    currentForegroundPackage = event.packageName
                    currentAppStartTime = event.timeStamp
                    thresholdTriggered = false
                    appSwitchCount++
                }
            } else if (event.eventType == pauseEvent) {
                if (event.packageName == currentForegroundPackage) {
                    // App went to background
                    currentForegroundPackage = null
                    thresholdTriggered = false
                }
            }
        }

        // Always update based on the current known foreground app
        currentForegroundPackage?.let { pkg ->
            val durationMillis = System.currentTimeMillis() - currentAppStartTime
            val durationSec = durationMillis / 1000
            Log.d("TaskForegroundService", "Current app: $pkg, Continuous duration: ${durationSec}s")
            
            if (pkg != packageName && durationSec >= THRESHOLD_DURATION && !thresholdTriggered) {
                triggerThresholdAction(pkg, durationSec)
                thresholdTriggered = true
            }
            
            updateNotification(taskLabel, pkg, durationSec)
        } ?: run {
            // No app in foreground (e.g. Home screen or screen off)
            updateNotification(taskLabel, "System / Home", null)
        }
        
        lastPollTime = endTime
    }

    private fun triggerThresholdAction(pkg: String, durationSec: Long) {
        val appName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pm = packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                val title = ApplicationInfo.getCategoryTitle(this, info.category)
                Log.d("TaskForegroundService", "Title: ${title}, category: ${info.category}, info: ${info.toString()}")
                title as String
            } else {
                pkg
            }
        } catch (e: Exception) {
            Log.d("TaskForegroundService", "exception thrown: $e")
            pkg
        }
        
        Log.d("TaskForegroundService", "${THRESHOLD_DURATION/60}-minute threshold hit for $appName ($pkg)")
        
        serviceScope.launch {
            if (!ConsentManager.hasConsented(applicationContext)) return@launch

            ApiClient.init(applicationContext)
            val deviceId = ApiClient.getDeviceId(applicationContext)
            val payload = ContextPayload(
                taskId = currentTaskId,
                deviceId = deviceId,
                appType = appName,
                continuousDuration = durationSec,
                switchCount = appSwitchCount
            )
            
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.contextPayloadDao()
            val payloadId = dao.insert(payload)
            
            contextResponseHandler.register(
                ContextResponseHandler.PendingContext(
                    contextId = payloadId,
                    taskId = currentTaskId,
                    targetPackage = pkg,
                    appName = appName,
                    deviceId = deviceId
                )
            )
            val requestId = RequestSyncManager.enqueueContext(applicationContext, payload.copy(id = payloadId))
            if (requestId < 0) contextResponseHandler.unregister(payloadId)
        }
    }

    private fun startRewardMonitoring(
        targetPackage: String,
        contextId: Long,
        taskId: Long,
        deviceId: String
    ) {
        serviceScope.launch {
            val startTime = System.currentTimeMillis()
            val observationDurationMs = 5 * 60 * 1000L // 5 minutes
            val pollIntervalMs = 1000L

            var screenLockedEver = false
            var appClosedEver = false
            var timeToCloseSec = 300

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

            while (System.currentTimeMillis() - startTime < observationDurationMs) {
                val now = System.currentTimeMillis()

                val isScreenOff = !powerManager.isInteractive
                val isLocked = keyguardManager.isDeviceLocked

                val state = RewardEvaluator.evaluate(
                    targetPackage = targetPackage,
                    currentForegroundPackage = currentForegroundPackage,
                    isScreenOff = isScreenOff,
                    isLocked = isLocked,
                    startTimeMs = startTime,
                    nowMs = now,
                    previousScreenLockedEver = screenLockedEver,
                    previousAppClosedEver = appClosedEver,
                    previousTimeToCloseSec = timeToCloseSec
                )

                screenLockedEver = state.screenLocked
                appClosedEver = state.appClosed
                timeToCloseSec = state.timeToCloseSec

                delay(pollIntervalMs.milliseconds)
            }

            val rewardPayload = com.tealamore.kthd.network.RewardPayload(
                deviceId = deviceId,
                contextId = contextId,
                taskId = taskId,
                screenLocked = screenLockedEver,
                appClosed = appClosedEver,
                timeToClose = timeToCloseSec
            )

            RequestSyncManager.enqueueReward(applicationContext, rewardPayload)
        }
    }

    private fun showThresholdNotification(appName: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createAlertChannel()

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Threshold Alert")
            .setContentText("1-minute threshold hit for $appName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows alerts when app usage thresholds are hit"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(taskLabel: String, foregroundPackage: String, durationSec: Long? = null) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(foregroundPackage, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            foregroundPackage
        }
        
        val subText = if (durationSec != null) {
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            "Using: $appName (${minutes}m ${seconds}s)"
        } else {
            "Using: $appName"
        }
        
        val notification = createNotification(taskLabel, subText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Task Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while a task is active"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(taskLabel: String, subText: String? = null): Notification {
        val contentText = subText ?: "Tracking active"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(taskLabel)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "active_task_channel"
        private const val ALERT_CHANNEL_ID = "threshold_alert_channel"
        private const val EXTRA_TASK_LABEL = "extra_task_label"
        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val ACTION_STOP = "action_stop"
        private const val THRESHOLD_DURATION = 60

        fun startService(context: Context, taskLabel: String, taskId: Long) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                putExtra(EXTRA_TASK_LABEL, taskLabel)
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
