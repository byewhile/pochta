package com.example.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "email_messages",
    foreignKeys = [
        ForeignKey(
            entity = MailAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId", "folder", "sentAt"]),
        Index(value = ["accountId", "folder", "remoteUid"]),
        Index(value = ["sentAt"])
    ]
)
data class EmailMessage(
    @PrimaryKey val id: String,
    val accountId: Long,
    val remoteUid: Long,
    val folder: String,
    val fromName: String,
    val fromEmail: String,
    val toRecipients: String,
    val ccRecipients: String = "",
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val sentAt: Long,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false,
    val attachmentNames: String = ""
) {
    companion object {
        fun buildId(accountId: Long, folder: String, remoteUid: Long): String =
            "${accountId}_${folder}_${remoteUid}"
    }
}

data class EmailWithAccountRow(
    val id: String,
    val accountId: Long,
    val remoteUid: Long,
    val folder: String,
    val fromName: String,
    val fromEmail: String,
    val toRecipients: String,
    val ccRecipients: String,
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String?,
    val sentAt: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val hasAttachments: Boolean,
    val attachmentNames: String,
    val accountEmail: String,
    val accountDisplayName: String,
    val accountProviderName: String
) {
    val provider: MailProvider
        get() = MailProvider.fromNameSafe(accountProviderName)

    val folderType: MailFolderType
        get() = MailFolderType.fromKey(folder)

    val attachmentsList: List<String>
        get() = attachmentNames.split("|").map { it.trim() }.filter { it.isNotEmpty() }
}

data class AccountUnreadCount(
    val accountId: Long,
    val unreadCount: Int
)
