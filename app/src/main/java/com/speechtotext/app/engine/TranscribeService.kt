package com.speechtotext.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.speechtotext.app.MainActivity
import com.speechtotext.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranscribeService : Service() {

    companion object {
        const val CHANNEL_ID = "transcription_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.speechtotext.app.START_TRANSCRIBE"
        const val ACTION_UPDATE_PROGRESS = "com.speechtotext.app.UPDATE_PROGRESS"
        const val ACTION_CANCEL = "com.speechtotext.app.CANCEL_TRANSCRIBE"
        const val ACTION_STOP = "com.speechtotext.app.STOP_TRANSCRIBE"

        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        private val _isCancelled = MutableStateFlow(false)
        val isCancelled = _isCancelled.asStateFlow()

        fun cancelTranscription() {
            _isCancelled.value = true
        }

        fun resetCancellation() {
            _isCancelled.value = false
        }

        fun start(context: Context, statusText: String) {
            val intent = Intent(context, TranscribeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATUS, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, progress: Int, statusText: String) {
            val intent = Intent(context, TranscribeService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, statusText)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TranscribeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SpeechToText::TranscribeWakeLock")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                resetCancellation()
                try {
                    if (wakeLock?.isHeld == false) {
                        wakeLock?.acquire(3 * 3600 * 1000L) // 3 hours max wake lock
                    }
                } catch (ignored: Exception) {}

                val status = intent.getStringExtra(EXTRA_STATUS) ?: "Audio process ho raha hai..."
                val notification = buildNotification(0, status)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val status = intent.getStringExtra(EXTRA_STATUS) ?: "Audio process ho raha hai: $progress%"
                notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, status))
            }
            ACTION_CANCEL -> {
                cancelTranscription()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Speech to Text Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows transcription progress in background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TranscribeService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Speech to Text (Offline)")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
