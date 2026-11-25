package com.example.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.EmailMessage
import com.example.model.MailAccount

class MailNotificationHelper(private val context: Context) {

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val newMailChannel = NotificationChannel(
            CHANNEL_NEW_MAIL,
            context.getString(R.string.notification_channel_new_mail_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_new_mail_desc)
            enableVibration(true)
        }

        val syncChannel = NotificationChannel(
            CHANNEL_REALTIME_SYNC,
            context.getString(R.string.notification_channel_sync_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_sync_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannel(newMailChannel)
        manager.createNotificationChannel(syncChannel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewMessages(account: MailAccount, newMessages: List<EmailMessage>) {
        if (newMessages.isEmpty() || !hasNotificationPermission()) return
        ensureChannelsCreated()

        val manager = NotificationManagerCompat.from(context)
        val providerLabel = account.provider.shortLabel

        newMessages.take(5).forEach { msg ->
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_MESSAGE_ID, msg.id)
                putExtra(EXTRA_ACCOUNT_ID, account.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                msg.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_NEW_MAIL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setSubText("$providerLabel • ${account.email}")
                .setContentTitle(msg.fromName)
                .setContentText(msg.subject)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${msg.subject}\n${msg.snippet}")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setGroup(GROUP_KEY_INCOMING_MAIL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            runCatching {
                manager.notify(msg.id.hashCode(), notification)
            }
        }
    }

    fun buildRealtimeServiceNotification(activeAccountsCount: Int): Notification {
        ensureChannelsCreated()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            activeAccountsCount == 0 -> "Нет подключённых ящиков"
            activeAccountsCount == 1 -> "Отслеживание 1 ящика в реальном времени"
            else -> "Отслеживание $activeAccountsCount ящиков в реальном времени"
        }

        return NotificationCompat.Builder(context, CHANNEL_REALTIME_SYNC)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_NEW_MAIL = "unified_mail_new_messages"
        const val CHANNEL_REALTIME_SYNC = "unified_mail_realtime_sync"
        const val GROUP_KEY_INCOMING_MAIL = "com.example.INCOMING_MAIL_GROUP"
        const val FOREGROUND_SERVICE_NOTIFICATION_ID = 41001
        const val EXTRA_OPEN_MESSAGE_ID = "extra_open_message_id"
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
    }
}
