package com.example.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.UnifiedMailApp
import com.example.model.MailAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Play-Policy-compliant dataSync Foreground Service that maintains real-time IMAP IDLE
 * push listeners across all connected Yandex, Mail.ru, and Gmail accounts.
 */
class RealtimeMailService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accountJobs = mutableMapOf<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? UnifiedMailApp
        if (app == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = app.notificationHelper.buildRealtimeServiceNotification(activeAccountsCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MailNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(MailNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            app.repository.accountsFlow.collectLatest { accounts ->
                if (accounts.isEmpty()) {
                    cancelAllAccountListeners()
                    stopSelf()
                    return@collectLatest
                }
                val updatedNotification = app.notificationHelper.buildRealtimeServiceNotification(accounts.size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        MailNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                        updatedNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(MailNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID, updatedNotification)
                }
                reconcileAccountListeners(app, accounts)
            }
        }

        return START_STICKY
    }

    private fun reconcileAccountListeners(app: UnifiedMailApp, accounts: List<MailAccount>) {
        val activeIds = accounts.map { it.id }.toSet()
        val toRemove = accountJobs.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            accountJobs.remove(id)?.cancel()
        }

        accounts.forEach { account ->
            if (!accountJobs.containsKey(account.id)) {
                accountJobs[account.id] = serviceScope.launch {
                    app.repository.observeAccountRealtime(
                        account = account,
                        onStatusChanged = { }
                    )
                }
            }
        }
    }

    private fun cancelAllAccountListeners() {
        accountJobs.values.forEach { it.cancel() }
        accountJobs.clear()
    }

    override fun onDestroy() {
        cancelAllAccountListeners()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context.applicationContext, RealtimeMailService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, RealtimeMailService::class.java)
            runCatching {
                context.applicationContext.stopService(intent)
            }
        }
    }
}
