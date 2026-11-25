package com.example

import android.app.Application
import com.example.data.AppPreferences
import com.example.data.ImapSmtpClient
import com.example.data.MailDatabase
import com.example.data.MailRepository
import com.example.security.SecureCredentialStore
import com.example.sync.MailNotificationHelper
import com.example.sync.MailSyncWorker

class UnifiedMailApp : Application() {

    lateinit var notificationHelper: MailNotificationHelper
        private set

    lateinit var repository: MailRepository
        private set

    override fun onCreate() {
        super.onCreate()
        notificationHelper = MailNotificationHelper(this).apply {
            ensureChannelsCreated()
        }
        val database = MailDatabase.getInstance(this)
        val credentialStore = SecureCredentialStore(this)
        val imapSmtpClient = ImapSmtpClient()
        val preferences = AppPreferences(this)

        repository = MailRepository(
            mailDao = database.mailDao(),
            credentialStore = credentialStore,
            imapSmtpClient = imapSmtpClient,
            notificationHelper = notificationHelper,
            appPreferences = preferences
        )

        MailSyncWorker.schedule(this)
    }
}
