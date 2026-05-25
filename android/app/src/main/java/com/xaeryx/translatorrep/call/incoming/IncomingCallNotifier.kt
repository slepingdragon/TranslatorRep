package com.xaeryx.translatorrep.call.incoming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xaeryx.translatorrep.MainActivity

/**
 * Posts (and clears) the incoming-call notification when a high-priority FCM push arrives while
 * the app is backgrounded (Story 2.5). A high-importance, CATEGORY_CALL notification with a
 * full-screen intent rings over the lock screen on capable devices and falls back to a heads-up
 * banner otherwise. Tapping it launches [MainActivity], which routes to the in-app
 * IncomingCallScreen off the `/pairs` signal (Accept/Decline). The full native ConnectionService /
 * Telecom in-call surface is a later refinement.
 */
object IncomingCallNotifier {

    private const val CHANNEL_ID = "incoming_calls"
    private const val CHANNEL_NAME = "Incoming calls"
    private const val NOTIFICATION_ID = 4711

    fun showIncomingCall(context: Context) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText("Partner")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** Dismiss the ring (on accept / decline / when the app brings up the in-app screen). */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when your partner calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
