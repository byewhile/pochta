package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.AccountUnreadCount
import com.example.model.EmailMessage
import com.example.model.EmailWithAccountRow
import com.example.model.MailAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {

    @Query("SELECT * FROM mail_accounts ORDER BY id ASC")
    fun observeAccounts(): Flow<List<MailAccount>>

    @Query("SELECT * FROM mail_accounts ORDER BY id ASC")
    suspend fun getAllAccounts(): List<MailAccount>

    @Query("SELECT * FROM mail_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: Long): MailAccount?

    @Query("SELECT * FROM mail_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getAccountByEmail(email: String): MailAccount?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: MailAccount): Long

    @Update
    suspend fun updateAccount(account: MailAccount)

    @Query("UPDATE mail_accounts SET lastSyncedAt = :timestamp, lastError = :error WHERE id = :accountId")
    suspend fun updateAccountSyncState(accountId: Long, timestamp: Long, error: String?)

    @Query("DELETE FROM mail_accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: Long)

    @Query(
        """
        SELECT accountId, COUNT(*) AS unreadCount
        FROM email_messages
        WHERE folder = 'INBOX' AND isRead = 0
        GROUP BY accountId
        """
    )
    fun observeInboxUnreadCounts(): Flow<List<AccountUnreadCount>>

    @Query(
        """
        SELECT m.*,
               a.email AS accountEmail,
               a.displayName AS accountDisplayName,
               a.providerName AS accountProviderName
        FROM email_messages m
        INNER JOIN mail_accounts a ON m.accountId = a.id
        WHERE (:accountId IS NULL OR m.accountId = :accountId)
          AND (
              (:folder = 'STARRED' AND m.isStarred = 1)
              OR (:folder != 'STARRED' AND m.folder = :folder)
          )
          AND (:onlyUnread = 0 OR m.isRead = 0)
          AND (:onlyWithAttachments = 0 OR m.hasAttachments = 1)
          AND (
              :query = ''
              OR m.subject LIKE '%' || :query || '%'
              OR m.fromName LIKE '%' || :query || '%'
              OR m.fromEmail LIKE '%' || :query || '%'
              OR m.snippet LIKE '%' || :query || '%'
              OR m.bodyText LIKE '%' || :query || '%'
              OR a.email LIKE '%' || :query || '%'
          )
        ORDER BY m.sentAt DESC
        """
    )
    fun observeMessages(
        accountId: Long?,
        folder: String,
        query: String,
        onlyUnread: Boolean,
        onlyWithAttachments: Boolean
    ): Flow<List<EmailWithAccountRow>>

    @Query(
        """
        SELECT m.*,
               a.email AS accountEmail,
               a.displayName AS accountDisplayName,
               a.providerName AS accountProviderName
        FROM email_messages m
        INNER JOIN mail_accounts a ON m.accountId = a.id
        WHERE m.id = :messageId
        LIMIT 1
        """
    )
    fun observeMessageById(messageId: String): Flow<EmailWithAccountRow?>

    @Query("SELECT * FROM email_messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<String>): List<EmailMessage>

    @Query("SELECT * FROM email_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageEntityById(id: String): EmailMessage?

    @Query("SELECT id FROM email_messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun getMessageIdsInFolder(accountId: Long, folder: String): List<String>

    @Query("SELECT MAX(remoteUid) FROM email_messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun getMaxRemoteUid(accountId: Long, folder: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<EmailMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: EmailMessage)

    @Query("UPDATE email_messages SET isRead = :isRead WHERE id = :messageId")
    suspend fun updateReadState(messageId: String, isRead: Boolean)

    @Query("UPDATE email_messages SET isRead = :isRead WHERE id IN (:ids)")
    suspend fun updateReadStateBatch(ids: List<String>, isRead: Boolean)

    @Query("UPDATE email_messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun updateStarredState(messageId: String, isStarred: Boolean)

    @Query("DELETE FROM email_messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<String>)

    @Query("DELETE FROM email_messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun deleteAllInAccountFolder(accountId: Long, folder: String)

    @Query("DELETE FROM email_messages WHERE folder = :folder")
    suspend fun deleteAllInFolderAcrossAccounts(folder: String)

    @Query("DELETE FROM email_messages WHERE accountId = :accountId AND folder = :folder AND id NOT IN (:keepIds)")
    suspend fun pruneRemovedMessages(accountId: Long, folder: String, keepIds: List<String>)
}
