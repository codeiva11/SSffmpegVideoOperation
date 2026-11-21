package com.simform.videoimageeditor.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.simform.videoimageeditor.MainActivity
import com.simform.videoimageeditor.R

/**
 * Keeps an FFmpeg operation alive when the app is minimised — and when
 * the phone screen is off.
 *
 * The encode itself still runs on CallBackOfQuery's background thread;
 * this service holds the process in the foreground (with a persistent
 * notification, so Android cannot silently kill the encode) and offers
 * a Cancel action. A foreground service alone does NOT keep the CPU
 * awake once the screen turns off (Doze puts the CPU to sleep, and the
 * encode stalls) — so the service also holds a PARTIAL_WAKE_LOCK while
 * an encode runs, which keeps the CPU on with the screen off. The lock
 * is released on destroy / cancel, so it never outlives the encode.
 * Started in [com.simform.videoimageeditor.ui.mvi.OperationViewModel.executeQuery]
 * so it covers every operation screen, stopped on success / failure /
 * cancel. START_NOT_STICKY: if the system kills it, the encode is gone
 * and the user re-runs it — no silent restart.
 *
 * Sessions: several steps of one job (YouTube download -> merge ->
 * convert) each call start()/stop(), and the service must stay
 * foreground for the WHOLE chain, not flap off between steps. start()
 * therefore only sends the start intent when the session count goes
 * 0 -> 1, and stop() only stops the service when the count drops
 * back to 0. A step that starts while another session is already
 * held just bumps the counter — no new start intent, so a step that
 * begins while the app is minimised never hits Android 12+'s rule
 * against starting a foreground service from the background (the
 * service is already running). The wake lock is held while the count
 * is above zero and released when it hits zero or the service is
 * destroyed.
 *
 * Type is dataSync (not mediaProcessing: this toolchain's AAPT does not
 * know that value yet and rejects the manifest).
 */
class EncodeService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createChannel()
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "FFmpeg operation"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(label),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(label))
                }
                acquireWakeLock()
            }
            ACTION_CANCEL -> {
                // User tapped Cancel in the notification: drop every
                // session, stop the YouTube download too, and stop.
                synchronized(LOCK) { sessionCount = 0 }
                FFmpegKit.cancel()
                YoutubeDownloadRunner.cancel()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Keeps the CPU awake with the screen off; released in onDestroy. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SSffmpegVideoOperation:Encode"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Background encoding",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Shows while an FFmpeg operation runs in the background." }
            )
        }
    }

    private fun buildNotification(label: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, EncodeService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(label)
            .setContentText("Running in the background — tap to open the app.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancel
            )
            .build()
    }

    companion object {
        const val ACTION_START = "com.simform.videoimageeditor.action.START_ENCODE"
        const val ACTION_CANCEL = "com.simform.videoimageeditor.action.CANCEL_ENCODE"
        const val EXTRA_LABEL = "label"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "encode"

        /** Application context, set once in MainActivity.onCreate, so the
         * ViewModels can start/stop this service without an Activity. */
        @Volatile
        var appContext: Context? = null
            private set

        /**
         * Sessions held right now (see the class doc). Guarded by LOCK;
         * every start()/stop() pair keeps it balanced, and a cancel
         * resets it to zero.
         */
        private val LOCK = Any()
        private var sessionCount = 0

        fun init(context: Context) {
            appContext = context.applicationContext
        }

        /**
         * Starts (or joins) one foreground session. Only the first
         * session sends the start intent — later ones just bump the
         * counter while the service is already foreground.
         */
        fun start(label: String) {
            val context = appContext ?: return
            val first = synchronized(LOCK) {
                val wasZero = sessionCount == 0
                sessionCount++
                wasZero
            }
            if (!first) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, EncodeService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_LABEL, label)
                }
            )
        }

        /**
         * Ends one session; the service stops only when the last
         * session ends. Safe to call when nothing is running.
         */
        fun stop() {
            val context = appContext ?: return
            val last = synchronized(LOCK) {
                if (sessionCount > 0) sessionCount--
                sessionCount == 0
            }
            if (!last) return
            context.stopService(Intent(context, EncodeService::class.java))
        }
    }
}
