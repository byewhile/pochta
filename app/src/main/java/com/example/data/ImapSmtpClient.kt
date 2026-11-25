package com.example.data

import com.example.model.EmailMessage
import com.example.model.MailAccount
import com.example.model.MailFolderType
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import javax.mail.Address
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

class ImapSmtpClient {

    suspend fun verifyConnection(account: MailAccount, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = connectImapStore(account, password)
                try {
                    val inbox = store.getFolder("INBOX")
                    inbox.open(Folder.READ_ONLY)
                    inbox.close(false)
                } finally {
                    runCatching { store.close() }
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { err -> Result.failure(Exception(mapFriendlyError(err, account))) }
            )
        }

    suspend fun fetchFolderMessages(
        account: MailAccount,
        password: String,
        folderType: MailFolderType,
        maxMessages: Int = 45
    ): Result<List<EmailMessage>> = withContext(Dispatchers.IO) {
        if (folderType == MailFolderType.STARRED) {
            return@withContext fetchFolderMessages(account, password, MailFolderType.INBOX, maxMessages)
        }
        runCatching {
            val store = connectImapStore(account, password)
            try {
                val folder = resolveImapFolder(store, folderType)
                    ?: return@runCatching emptyList()
                if (!folder.exists()) return@runCatching emptyList()
                folder.open(Folder.READ_ONLY)
                try {
                    val totalCount = folder.messageCount
                    if (totalCount <= 0) return@runCatching emptyList()

                    val start = (totalCount - maxMessages + 1).coerceAtLeast(1)
                    val rawMessages = folder.getMessages(start, totalCount)
                    val fetchProfile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(FetchProfile.Item.CONTENT_INFO)
                        add(UIDFolder.FetchProfileItem.UID)
                    }
                    folder.fetch(rawMessages, fetchProfile)

                    val uidFolder = folder as? UIDFolder
                    rawMessages.reversed().mapNotNull { msg ->
                        runCatching {
                            parseMessage(
                                message = msg,
                                uid = uidFolder?.getUID(msg) ?: msg.messageNumber.toLong(),
                                accountId = account.id,
                                folderKey = folderType.dbKey
                            )
                        }.getOrNull()
                    }
                } finally {
                    runCatching { if (folder.isOpen) folder.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err -> Result.failure(Exception(mapFriendlyError(err, account))) }
        )
    }

    suspend fun sendEmail(
        account: MailAccount,
        password: String,
        to: String,
        cc: String,
        subject: String,
        bodyText: String
    ): Result<EmailMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val useSmtps = account.smtpPort == 465
            val protocol = if (useSmtps) "smtps" else "smtp"
            val props = Properties().apply {
                put("mail.transport.protocol", protocol)
                put("mail.$protocol.host", account.smtpHost)
                put("mail.$protocol.port", account.smtpPort.toString())
                put("mail.$protocol.auth", "true")
                put("mail.$protocol.connectiontimeout", "15000")
                put("mail.$protocol.timeout", "20000")
                put("mail.$protocol.writetimeout", "20000")
                if (useSmtps) {
                    put("mail.smtps.ssl.enable", "true")
                    put("mail.smtps.ssl.checkserveridentity", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
            }

            val session = Session.getInstance(props)
            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email, account.displayName.ifBlank { account.email }, "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.trim(), false))
                if (cc.isNotBlank()) {
                    setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc.trim(), false))
                }
                setSubject(subject.trim(), "UTF-8")
                sentDate = Date()
                setText(bodyText, "UTF-8")
            }

            val transport: Transport = session.getTransport(protocol)
            try {
                transport.connect(account.smtpHost, account.smtpPort, account.email, password)
                transport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            } finally {
                runCatching { transport.close() }
            }

            // Best-effort append to IMAP Sent folder for servers that do not auto-copy sent mail
            val now = System.currentTimeMillis()
            val syntheticUid = now
            EmailMessage(
                id = EmailMessage.buildId(account.id, MailFolderType.SENT.dbKey, syntheticUid),
                accountId = account.id,
                remoteUid = syntheticUid,
                folder = MailFolderType.SENT.dbKey,
                fromName = account.displayName.ifBlank { account.email },
                fromEmail = account.email,
                toRecipients = to.trim(),
                ccRecipients = cc.trim(),
                subject = subject.trim().ifBlank { "(Без темы)" },
                snippet = bodyText.replace(Regex("\\s+"), " ").trim().take(180),
                bodyText = bodyText,
                bodyHtml = null,
                sentAt = now,
                isRead = true,
                isStarred = false,
                hasAttachments = false,
                attachmentNames = ""
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err -> Result.failure(Exception(mapFriendlyError(err, account))) }
        )
    }

    suspend fun deleteMessagesOnServer(
        account: MailAccount,
        password: String,
        folderType: MailFolderType,
        uids: List<Long>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uids.isEmpty()) return@withContext Result.success(Unit)
        runCatching {
            val store = connectImapStore(account, password)
            try {
                val sourceFolder = resolveImapFolder(store, folderType) ?: return@runCatching
                if (!sourceFolder.exists()) return@runCatching
                sourceFolder.open(Folder.READ_WRITE)
                try {
                    val uidFolder = sourceFolder as? UIDFolder ?: return@runCatching
                    val messages = uids.mapNotNull { uid ->
                        runCatching { uidFolder.getMessageByUID(uid) }.getOrNull()
                    }.toTypedArray()
                    if (messages.isEmpty()) return@runCatching

                    if (folderType != MailFolderType.TRASH && folderType != MailFolderType.SPAM) {
                        val trashFolder = resolveImapFolder(store, MailFolderType.TRASH)
                        if (trashFolder != null && trashFolder.exists() && trashFolder.fullName != sourceFolder.fullName) {
                            runCatching { sourceFolder.copyMessages(messages, trashFolder) }
                        }
                    }

                    messages.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                    sourceFolder.expunge()
                } finally {
                    runCatching { if (sourceFolder.isOpen) sourceFolder.close(true) }
                }
            } finally {
                runCatching { store.close() }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { err -> Result.failure(Exception(mapFriendlyError(err, account))) }
        )
    }

    suspend fun clearFolderOnServer(
        account: MailAccount,
        password: String,
        folderType: MailFolderType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val store = connectImapStore(account, password)
            try {
                val folder = resolveImapFolder(store, folderType) ?: return@runCatching
                if (!folder.exists()) return@runCatching
                folder.open(Folder.READ_WRITE)
                try {
                    val count = folder.messageCount
                    if (count > 0) {
                        val allMessages = folder.getMessages(1, count)
                        if (folderType == MailFolderType.INBOX) {
                            val trashFolder = resolveImapFolder(store, MailFolderType.TRASH)
                            if (trashFolder != null && trashFolder.exists() && trashFolder.fullName != folder.fullName) {
                                runCatching { folder.copyMessages(allMessages, trashFolder) }
                            }
                        }
                        folder.setFlags(allMessages, Flags(Flags.Flag.DELETED), true)
                        folder.expunge()
                    }
                } finally {
                    runCatching { if (folder.isOpen) folder.close(true) }
                }
            } finally {
                runCatching { store.close() }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { err -> Result.failure(Exception(mapFriendlyError(err, account))) }
        )
    }

    suspend fun updateMessageFlagOnServer(
        account: MailAccount,
        password: String,
        folderType: MailFolderType,
        uid: Long,
        flag: Flags.Flag,
        value: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val store = connectImapStore(account, password)
            try {
                val folder = resolveImapFolder(store, folderType) ?: return@runCatching
                if (!folder.exists()) return@runCatching
                folder.open(Folder.READ_WRITE)
                try {
                    val uidFolder = folder as? UIDFolder ?: return@runCatching
                    val msg = uidFolder.getMessageByUID(uid) ?: return@runCatching
                    msg.setFlag(flag, value)
                } finally {
                    runCatching { if (folder.isOpen) folder.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
        }
    }

    /**
     * Maintains a real-time IMAP IDLE connection to INBOX for instant incoming mail notifications.
     * Falls back seamlessly to short-interval polling if the remote server closes IDLE early.
     */
    suspend fun observeInboxRealtime(
        account: MailAccount,
        password: String,
        onStatusChanged: (Boolean) -> Unit,
        onMailboxChanged: suspend () -> Unit
    ) = withContext(Dispatchers.IO) {
        var backoffMs = 5_000L
        while (currentCoroutineContext().isActive) {
            var store: IMAPStore? = null
            var inbox: IMAPFolder? = null
            try {
                store = connectImapStore(account, password)
                inbox = store.getFolder("INBOX") as? IMAPFolder
                if (inbox == null || !inbox.exists()) {
                    onStatusChanged(false)
                    delay(30_000L)
                    continue
                }
                inbox.open(Folder.READ_ONLY)
                onStatusChanged(true)
                backoffMs = 5_000L

                val changed = AtomicBoolean(false)
                inbox.addMessageCountListener(object : MessageCountAdapter() {
                    override fun messagesAdded(ev: MessageCountEvent?) {
                        changed.set(true)
                    }

                    override fun messagesRemoved(ev: MessageCountEvent?) {
                        changed.set(true)
                    }
                })

                while (currentCoroutineContext().isActive && inbox.isOpen) {
                    changed.set(false)
                    try {
                        // idle(true) returns immediately on server event or server timeout
                        inbox.idle(true)
                    } catch (_: MessagingException) {
                        // Server may not support IDLE or connection timed out; poll with NOOP
                        if (inbox.isOpen) {
                            inbox.doCommand { protocol ->
                                protocol.simpleCommand("NOOP", null)
                                null
                            }
                        }
                        delay(20_000L)
                    }
                    if (changed.get()) {
                        onMailboxChanged()
                    }
                }
            } catch (_: Exception) {
                onStatusChanged(false)
                if (!currentCoroutineContext().isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
            } finally {
                runCatching { if (inbox?.isOpen == true) inbox.close(false) }
                runCatching { store?.close() }
            }
        }
        onStatusChanged(false)
    }

    private fun connectImapStore(account: MailAccount, password: String): IMAPStore {
        val protocol = if (account.useSsl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.host", account.imapHost)
            put("mail.$protocol.port", account.imapPort.toString())
            put("mail.$protocol.connectiontimeout", "15000")
            put("mail.$protocol.timeout", "25000")
            put("mail.$protocol.fetchsize", "1048576")
            if (account.useSsl) {
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.ssl.checkserveridentity", "true")
            } else {
                put("mail.imap.starttls.enable", "true")
                put("mail.imap.ssl.checkserveridentity", "true")
            }
        }
        val session = Session.getInstance(props)
        val store = session.getStore(protocol) as IMAPStore
        store.connect(account.imapHost, account.imapPort, account.email, password)
        return store
    }

    private fun resolveImapFolder(store: IMAPStore, folderType: MailFolderType): IMAPFolder? {
        if (folderType == MailFolderType.INBOX) {
            return store.getFolder("INBOX") as? IMAPFolder
        }
        // First try RFC 6154 SPECIAL-USE attributes (\Sent, \Trash, \Junk)
        val targetAttr = folderType.specialUseAttribute
        val allFolders = runCatching { store.defaultFolder.list("*") }.getOrDefault(emptyArray())
        if (targetAttr != null) {
            val byAttr = allFolders.firstOrNull { folder ->
                (folder as? IMAPFolder)?.attributes?.any {
                    it.equals(targetAttr, ignoreCase = true)
                } == true
            } as? IMAPFolder
            if (byAttr != null) return byAttr
        }

        // Next try known provider candidate folder names
        for (candidate in folderType.candidateRemoteNames) {
            val direct = runCatching { store.getFolder(candidate) }.getOrNull() as? IMAPFolder
            if (direct != null && runCatching { direct.exists() }.getOrDefault(false)) {
                return direct
            }
        }

        // Fallback case-insensitive search across all listed folders
        val lowerCandidates = folderType.candidateRemoteNames.map { it.lowercase() }.toSet()
        return allFolders.firstOrNull { f ->
            f.name.lowercase() in lowerCandidates || f.fullName.lowercase() in lowerCandidates
        } as? IMAPFolder
    }

    private fun parseMessage(
        message: Message,
        uid: Long,
        accountId: Long,
        folderKey: String
    ): EmailMessage {
        val fromAddr = message.from?.firstOrNull()
        val (fromName, fromEmail) = extractNameAndEmail(fromAddr)
        val toList = formatAddressList(message.getRecipients(Message.RecipientType.TO))
        val ccList = formatAddressList(message.getRecipients(Message.RecipientType.CC))
        val rawSubject = message.subject?.let { decodeHeader(it) }?.trim().orEmpty()
        val subject = rawSubject.ifBlank { "(Без темы)" }

        val contentCollector = ContentCollector()
        extractContent(message, contentCollector)

        val cleanText = when {
            contentCollector.plainText.isNotBlank() -> contentCollector.plainText.trim()
            !contentCollector.htmlText.isNullOrBlank() -> stripHtmlTags(contentCollector.htmlText!!)
            else -> ""
        }
        val snippet = cleanText
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)

        val timestamp = (message.receivedDate ?: message.sentDate)?.time ?: System.currentTimeMillis()
        val flags = message.flags
        val isRead = flags?.contains(Flags.Flag.SEEN) == true
        val isStarred = flags?.contains(Flags.Flag.FLAGGED) == true

        return EmailMessage(
            id = EmailMessage.buildId(accountId, folderKey, uid),
            accountId = accountId,
            remoteUid = uid,
            folder = folderKey,
            fromName = fromName.ifBlank { fromEmail.ifBlank { "Неизвестный отправитель" } },
            fromEmail = fromEmail,
            toRecipients = toList,
            ccRecipients = ccList,
            subject = subject,
            snippet = snippet,
            bodyText = cleanText,
            bodyHtml = contentCollector.htmlText?.take(MAX_HTML_CHARS),
            sentAt = timestamp,
            isRead = isRead,
            isStarred = isStarred,
            hasAttachments = contentCollector.attachments.isNotEmpty(),
            attachmentNames = contentCollector.attachments.joinToString("|")
        )
    }

    private class ContentCollector {
        val plainBuilder = StringBuilder()
        var htmlText: String? = null
        val attachments = mutableListOf<String>()

        val plainText: String
            get() = plainBuilder.toString()
    }

    private fun extractContent(part: Part, collector: ContentCollector, depth: Int = 0) {
        if (depth > 6) return
        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { part.fileName?.let { decodeHeader(it) } }.getOrNull()

        if (Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
            (!fileName.isNullOrBlank() && !part.isMimeType("text/*"))
        ) {
            collector.attachments.add(fileName ?: "Вложение")
            return
        }

        when {
            part.isMimeType("text/plain") -> {
                val text = readPartAsString(part)
                if (text.isNotBlank()) {
                    if (collector.plainBuilder.isNotEmpty()) collector.plainBuilder.append("\n")
                    collector.plainBuilder.append(text.take(MAX_TEXT_CHARS))
                }
            }
            part.isMimeType("text/html") -> {
                if (collector.htmlText == null) {
                    collector.htmlText = readPartAsString(part).take(MAX_HTML_CHARS)
                }
            }
            part.isMimeType("multipart/*") -> {
                val multipart = runCatching { part.content as? Multipart }.getOrNull() ?: return
                for (i in 0 until multipart.count) {
                    runCatching { extractContent(multipart.getBodyPart(i), collector, depth + 1) }
                }
            }
        }
    }

    private fun readPartAsString(part: Part): String {
        return runCatching {
            when (val content = part.content) {
                is String -> content
                is InputStream -> readLimitedStream(content, MAX_STREAM_BYTES)
                else -> content?.toString().orEmpty()
            }
        }.getOrDefault("")
    }

    private fun readLimitedStream(stream: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        stream.use { input ->
            while (total < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun extractNameAndEmail(address: Address?): Pair<String, String> {
        val internet = address as? InternetAddress
        if (internet != null) {
            val personal = internet.personal?.let { decodeHeader(it) }?.trim().orEmpty()
            val email = internet.address?.trim().orEmpty()
            return personal to email
        }
        val raw = address?.toString()?.let { decodeHeader(it) }?.trim().orEmpty()
        return raw to raw
    }

    private fun formatAddressList(addresses: Array<Address>?): String {
        if (addresses.isNullOrEmpty()) return ""
        return addresses.joinToString(", ") { addr ->
            val (name, email) = extractNameAndEmail(addr)
            if (name.isNotBlank() && !name.equals(email, ignoreCase = true)) {
                "$name <$email>"
            } else {
                email
            }
        }
    }

    private fun decodeHeader(value: String): String =
        runCatching { MimeUtility.decodeText(value) }.getOrDefault(value)

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>|</div>|</tr>|</li>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun mapFriendlyError(error: Throwable, account: MailAccount): String {
        val msg = error.message.orEmpty()
        return when {
            error is AuthenticationFailedException ||
                msg.contains("AUTHENTICATIONFAILED", ignoreCase = true) ||
                msg.contains("Invalid credentials", ignoreCase = true) ||
                msg.contains("login failed", ignoreCase = true) ||
                msg.contains("Application-specific password", ignoreCase = true) ->
                "Ошибка входа в ${account.provider.displayName}. Проверьте e-mail и используйте специальный «Пароль приложения» (а не обычный пароль)."
            msg.contains("UnknownHostException", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Нет подключения к серверу ${account.imapHost}. Проверьте интернет-соединение."
            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ->
                "Превышено время ожидания ответа от ${account.imapHost}."
            msg.contains("IMAP", ignoreCase = true) && msg.contains("disabled", ignoreCase = true) ->
                "IMAP отключён в настройках ящика ${account.email}. Включите доступ по IMAP в веб-версии почты."
            else -> msg.ifBlank { "Не удалось подключиться к ${account.imapHost}" }
        }
    }

    companion object {
        private const val MAX_TEXT_CHARS = 50_000
        private const val MAX_HTML_CHARS = 120_000
        private const val MAX_STREAM_BYTES = 131_072
    }
}
