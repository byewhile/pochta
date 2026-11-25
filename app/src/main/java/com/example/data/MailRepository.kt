package com.example.data

import com.example.model.AccountUnreadCount
import com.example.model.EmailMessage
import com.example.model.EmailWithAccountRow
import com.example.model.MailAccount
import com.example.model.MailFolderType
import com.example.model.MailProvider
import com.example.security.SecureCredentialStore
import com.example.sync.MailNotificationHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.mail.Flags

class MailRepository(
    private val mailDao: MailDao,
    private val credentialStore: SecureCredentialStore,
    private val imapSmtpClient: ImapSmtpClient,
    private val notificationHelper: MailNotificationHelper,
    private val appPreferences: AppPreferences
) {
    val accountsFlow: Flow<List<MailAccount>> = mailDao.observeAccounts()
    val unreadCountsFlow: Flow<List<AccountUnreadCount>> = mailDao.observeInboxUnreadCounts()
    val settingsFlow: Flow<MailUserSettings> = appPreferences.settingsFlow

    fun observeMessages(
        accountId: Long?,
        folder: MailFolderType,
        query: String,
        onlyUnread: Boolean,
        onlyWithAttachments: Boolean
    ): Flow<List<EmailWithAccountRow>> = mailDao.observeMessages(
        accountId = accountId,
        folder = folder.dbKey,
        query = query.trim(),
        onlyUnread = onlyUnread,
        onlyWithAttachments = onlyWithAttachments
    )

    fun observeMessageById(messageId: String): Flow<EmailWithAccountRow?> =
        mailDao.observeMessageById(messageId)

    suspend fun getAllAccounts(): List<MailAccount> = mailDao.getAllAccounts()

    fun getPassword(accountId: Long): String? = credentialStore.getPassword(accountId)

    suspend fun connectAccount(
        provider: MailProvider,
        email: String,
        displayName: String,
        password: String,
        imapHost: String,
        imapPort: Int,
        smtpHost: String,
        smtpPort: Int,
        useSsl: Boolean = true
    ): Result<MailAccount> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty() || !normalizedEmail.contains("@")) {
            return Result.failure(Exception("Введите корректный адрес электронной почты."))
        }
        if (password.isBlank()) {
            return Result.failure(Exception("Введите пароль приложения."))
        }
        if (imapHost.isBlank() || smtpHost.isBlank()) {
            return Result.failure(Exception("Укажите адреса IMAP и SMTP серверов."))
        }

        val existing = mailDao.getAccountByEmail(normalizedEmail)
        if (existing != null) {
            return Result.failure(Exception("Ящик $normalizedEmail уже подключён."))
        }

        val candidate = MailAccount(
            providerName = provider.name,
            email = normalizedEmail,
            displayName = displayName.trim().ifBlank { normalizedEmail.substringBefore("@") },
            imapHost = imapHost.trim(),
            imapPort = imapPort,
            smtpHost = smtpHost.trim(),
            smtpPort = smtpPort,
            useSsl = useSsl
        )

        val verifyResult = imapSmtpClient.verifyConnection(candidate, password)
        if (verifyResult.isFailure) {
            return Result.failure(verifyResult.exceptionOrNull() ?: Exception("Не удалось подключиться"))
        }

        val newId = mailDao.insertAccount(candidate)
        credentialStore.savePassword(newId, password)
        val savedAccount = candidate.copy(id = newId)

        // Initial inbox sync right away (suppress notifications on first historical load)
        syncAccountFolder(savedAccount, MailFolderType.INBOX, notifyNew = false)
        return Result.success(savedAccount)
    }

    suspend fun removeAccount(accountId: Long) {
        credentialStore.deletePassword(accountId)
        mailDao.deleteAccountById(accountId)
    }

    suspend fun syncAllAccounts(
        folder: MailFolderType = MailFolderType.INBOX,
        notifyNew: Boolean = true
    ): Int = coroutineScope {
        val accounts = mailDao.getAllAccounts()
        if (accounts.isEmpty()) return@coroutineScope 0
        val counts = accounts.map { account ->
            async {
                syncAccountFolder(account, folder, notifyNew).getOrDefault(0)
            }
        }.awaitAll()
        counts.sum()
    }

    suspend fun syncAccountFolder(
        account: MailAccount,
        folder: MailFolderType = MailFolderType.INBOX,
        notifyNew: Boolean = true
    ): Result<Int> {
        val password = credentialStore.getPassword(account.id)
            ?: return Result.failure(Exception("Не найден сохранённый пароль для ${account.email}"))

        val effectiveFolder = if (folder == MailFolderType.STARRED) MailFolderType.INBOX else folder
        val previousMaxUid = mailDao.getMaxRemoteUid(account.id, effectiveFolder.dbKey)
        val hadExistingMessages = previousMaxUid != null

        val fetchedResult = imapSmtpClient.fetchFolderMessages(account, password, effectiveFolder)
        return fetchedResult.fold(
            onSuccess = { fetched ->
                val newlyArrived = if (effectiveFolder == MailFolderType.INBOX && hadExistingMessages) {
                    fetched.filter { it.remoteUid > (previousMaxUid ?: 0L) && !it.isRead }
                } else {
                    emptyList()
                }

                if (fetched.isNotEmpty()) {
                    mailDao.upsertMessages(fetched)
                    mailDao.pruneRemovedMessages(account.id, effectiveFolder.dbKey, fetched.map { it.id })
                } else {
                    mailDao.deleteAllInAccountFolder(account.id, effectiveFolder.dbKey)
                }

                mailDao.updateAccountSyncState(
                    accountId = account.id,
                    timestamp = System.currentTimeMillis(),
                    error = null
                )

                if (notifyNew && newlyArrived.isNotEmpty()) {
                    val settings = appPreferences.settingsFlow.first()
                    if (settings.notificationsEnabled) {
                        notificationHelper.notifyNewMessages(account, newlyArrived)
                    }
                }
                Result.success(newlyArrived.size)
            },
            onFailure = { error ->
                mailDao.updateAccountSyncState(
                    accountId = account.id,
                    timestamp = account.lastSyncedAt,
                    error = error.message
                )
                Result.failure(error)
            }
        )
    }

    suspend fun sendEmail(
        fromAccountId: Long,
        to: String,
        cc: String,
        subject: String,
        body: String
    ): Result<Unit> {
        val account = mailDao.getAccountById(fromAccountId)
            ?: return Result.failure(Exception("Выберите ящик отправителя."))
        val password = credentialStore.getPassword(account.id)
            ?: return Result.failure(Exception("Не удалось расшифровать пароль для ${account.email}"))

        if (to.isBlank() || !to.contains("@")) {
            return Result.failure(Exception("Укажите корректный адрес получателя."))
        }

        val sendResult = imapSmtpClient.sendEmail(account, password, to, cc, subject, body)
        return sendResult.map { sentMessage ->
            mailDao.upsertMessage(sentMessage)
        }
    }

    suspend fun deleteMessages(messageIds: List<String>): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        val entities = mailDao.getMessagesByIds(messageIds)
        if (entities.isEmpty()) return Result.success(Unit)

        // Optimistically remove locally first for snappy UI, then sync deletion to real IMAP servers
        mailDao.deleteMessagesByIds(messageIds)

        val grouped = entities.groupBy { it.accountId to MailFolderType.fromKey(it.folder) }
        var firstError: Throwable? = null
        for ((key, msgs) in grouped) {
            val (accountId, folder) = key
            val account = mailDao.getAccountById(accountId) ?: continue
            val password = credentialStore.getPassword(accountId) ?: continue
            val result = imapSmtpClient.deleteMessagesOnServer(
                account = account,
                password = password,
                folderType = folder,
                uids = msgs.map { it.remoteUid }
            )
            if (result.isFailure && firstError == null) {
                firstError = result.exceptionOrNull()
            }
        }
        return if (firstError != null) Result.failure(firstError) else Result.success(Unit)
    }

    suspend fun clearMailboxFolder(accountId: Long?, folder: MailFolderType): Result<Unit> {
        val effectiveFolder = if (folder == MailFolderType.STARRED) MailFolderType.INBOX else folder
        val targetAccounts = if (accountId != null) {
            listOfNotNull(mailDao.getAccountById(accountId))
        } else {
            mailDao.getAllAccounts()
        }

        if (accountId != null) {
            mailDao.deleteAllInAccountFolder(accountId, effectiveFolder.dbKey)
        } else {
            mailDao.deleteAllInFolderAcrossAccounts(effectiveFolder.dbKey)
        }

        var firstError: Throwable? = null
        for (account in targetAccounts) {
            val password = credentialStore.getPassword(account.id) ?: continue
            val result = imapSmtpClient.clearFolderOnServer(account, password, effectiveFolder)
            if (result.isFailure && firstError == null) {
                firstError = result.exceptionOrNull()
            }
        }
        return if (firstError != null) Result.failure(firstError) else Result.success(Unit)
    }

    suspend fun setRead(messageId: String, isRead: Boolean) {
        val msg = mailDao.getMessageEntityById(messageId) ?: return
        mailDao.updateReadState(messageId, isRead)
        val account = mailDao.getAccountById(msg.accountId) ?: return
        val password = credentialStore.getPassword(account.id) ?: return
        imapSmtpClient.updateMessageFlagOnServer(
            account = account,
            password = password,
            folderType = MailFolderType.fromKey(msg.folder),
            uid = msg.remoteUid,
            flag = Flags.Flag.SEEN,
            value = isRead
        )
    }

    suspend fun markBatchRead(messageIds: List<String>, isRead: Boolean) {
        if (messageIds.isEmpty()) return
        val entities = mailDao.getMessagesByIds(messageIds)
        mailDao.updateReadStateBatch(messageIds, isRead)
        entities.forEach { msg ->
            val account = mailDao.getAccountById(msg.accountId) ?: return@forEach
            val password = credentialStore.getPassword(account.id) ?: return@forEach
            imapSmtpClient.updateMessageFlagOnServer(
                account = account,
                password = password,
                folderType = MailFolderType.fromKey(msg.folder),
                uid = msg.remoteUid,
                flag = Flags.Flag.SEEN,
                value = isRead
            )
        }
    }

    suspend fun toggleStarred(messageId: String) {
        val msg = mailDao.getMessageEntityById(messageId) ?: return
        val newValue = !msg.isStarred
        mailDao.updateStarredState(messageId, newValue)
        val account = mailDao.getAccountById(msg.accountId) ?: return
        val password = credentialStore.getPassword(account.id) ?: return
        imapSmtpClient.updateMessageFlagOnServer(
            account = account,
            password = password,
            folderType = MailFolderType.fromKey(msg.folder),
            uid = msg.remoteUid,
            flag = Flags.Flag.FLAGGED,
            value = newValue
        )
    }

    suspend fun observeAccountRealtime(
        account: MailAccount,
        onStatusChanged: (Boolean) -> Unit
    ) {
        val password = credentialStore.getPassword(account.id) ?: return
        imapSmtpClient.observeInboxRealtime(
            account = account,
            password = password,
            onStatusChanged = onStatusChanged,
            onMailboxChanged = {
                syncAccountFolder(account, MailFolderType.INBOX, notifyNew = true)
            }
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        appPreferences.setNotificationsEnabled(enabled)

    suspend fun setRealtimeBackgroundPushEnabled(enabled: Boolean) =
        appPreferences.setRealtimeBackgroundPushEnabled(enabled)

    suspend fun setShowHtmlContentByDefault(enabled: Boolean) =
        appPreferences.setShowHtmlContentByDefault(enabled)
}
