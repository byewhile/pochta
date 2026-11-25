package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mail_accounts",
    indices = [Index(value = ["email"], unique = true)]
)
data class MailAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerName: String,
    val email: String,
    val displayName: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val useSsl: Boolean = true,
    val lastSyncedAt: Long = 0L,
    val isRealtimeEnabled: Boolean = true,
    val lastError: String? = null
) {
    val provider: MailProvider
        get() = MailProvider.fromNameSafe(providerName)
}
