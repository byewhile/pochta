package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UnifiedMailApp
import com.example.data.MailRepository
import com.example.data.MailUserSettings
import com.example.model.EmailWithAccountRow
import com.example.model.MailAccount
import com.example.model.MailFolderType
import com.example.model.MailProvider
import com.example.sync.RealtimeMailService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeDraft(
    val fromAccountId: Long? = null,
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String = ""
)

sealed interface MailScreen {
    data object Inbox : MailScreen
    data class MessageDetail(val messageId: String) : MailScreen
    data class Compose(val draft: ComposeDraft = ComposeDraft()) : MailScreen
    data object Accounts : MailScreen
    data object Settings : MailScreen
}

data class MailFilterState(
    val selectedAccountId: Long? = null, // null = Все ящики (Unified Inbox)
    val selectedFolder: MailFolderType = MailFolderType.INBOX,
    val searchQuery: String = "",
    val onlyUnread: Boolean = false,
    val onlyWithAttachments: Boolean = false
)

data class AccountConnectUiState(
    val isConnecting: Boolean = false,
    val errorMessage: String? = null
)

data class ComposeUiState(
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UnifiedMailApp
    private val repository: MailRepository = app.repository

    private val _filterState = MutableStateFlow(MailFilterState())
    val filterState: StateFlow<MailFilterState> = _filterState.asStateFlow()

    private val _activeScreen = MutableStateFlow<MailScreen>(MailScreen.Inbox)
    val activeScreen: StateFlow<MailScreen> = _activeScreen.asStateFlow()

    // Used in tablet split-pane mode to show a message alongside the list
    private val _selectedDetailMessageId = MutableStateFlow<String?>(null)
    val selectedDetailMessageId: StateFlow<String?> = _selectedDetailMessageId.asStateFlow()

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _realtimeConnectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val realtimeConnectedIds: StateFlow<Set<Long>> = _realtimeConnectedIds.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _connectState = MutableStateFlow(AccountConnectUiState())
    val connectState: StateFlow<AccountConnectUiState> = _connectState.asStateFlow()

    private val _composeState = MutableStateFlow(ComposeUiState())
    val composeState: StateFlow<ComposeUiState> = _composeState.asStateFlow()

    private val _preselectedProviderForConnect = MutableStateFlow<MailProvider?>(null)
    val preselectedProviderForConnect: StateFlow<MailProvider?> = _preselectedProviderForConnect.asStateFlow()

    val accounts: StateFlow<List<MailAccount>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadByAccount: StateFlow<Map<Long, Int>> = repository.unreadCountsFlow
        .combine(accounts) { counts, _ ->
            counts.associate { it.accountId to it.unreadCount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val settings: StateFlow<MailUserSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MailUserSettings())

    val messages: StateFlow<List<EmailWithAccountRow>> = _filterState
        .flatMapLatest { filter ->
            repository.observeMessages(
                accountId = filter.selectedAccountId,
                folder = filter.selectedFolder,
                query = filter.searchQuery,
                onlyUnread = filter.onlyUnread,
                onlyWithAttachments = filter.onlyWithAttachments
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDetailMessage: StateFlow<EmailWithAccountRow?> = combine(
        _activeScreen,
        _selectedDetailMessageId
    ) { screen, splitId ->
        when (screen) {
            is MailScreen.MessageDetail -> screen.messageId
            else -> splitId
        }
    }.flatMapLatest { id ->
        if (id == null) MutableStateFlow(null) else repository.observeMessageById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val realtimeJobs = mutableMapOf<Long, Job>()

    init {
        observeAccountsForRealtime()
        syncAllNow(notifyNew = false)
    }

    private fun observeAccountsForRealtime() {
        viewModelScope.launch {
            combine(accounts, settings) { list, prefs -> list to prefs }
                .collect { (accountList, prefs) ->
                    // Keep foreground ViewModel IDLE connections alive for instant UI updates
                    val currentIds = accountList.map { it.id }.toSet()
                    realtimeJobs.keys.filter { it !in currentIds }.forEach { removedId ->
                        realtimeJobs.remove(removedId)?.cancel()
                        _realtimeConnectedIds.update { it - removedId }
                    }

                    // If selected account was deleted, return cleanly to "Все ящики"
                    val selectedId = _filterState.value.selectedAccountId
                    if (selectedId != null && selectedId !in currentIds) {
                        _filterState.update { it.copy(selectedAccountId = null) }
                    }

                    accountList.forEach { account ->
                        if (!realtimeJobs.containsKey(account.id)) {
                            realtimeJobs[account.id] = viewModelScope.launch {
                                repository.observeAccountRealtime(account) { connected ->
                                    _realtimeConnectedIds.update { set ->
                                        if (connected) set + account.id else set - account.id
                                    }
                                }
                            }
                        }
                    }

                    // Manage background IMAP IDLE foreground service per user preference
                    if (accountList.isNotEmpty() && prefs.realtimeBackgroundPushEnabled && prefs.notificationsEnabled) {
                        RealtimeMailService.start(app)
                    } else {
                        RealtimeMailService.stop(app)
                    }
                }
        }
    }

    fun selectAccount(accountId: Long?) {
        _selectedMessageIds.value = emptySet()
        _filterState.update { it.copy(selectedAccountId = accountId) }
    }

    fun selectFolder(folder: MailFolderType) {
        _selectedMessageIds.value = emptySet()
        _filterState.update { it.copy(selectedFolder = folder) }
        syncCurrentScope(notifyNew = false)
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun toggleOnlyUnread() {
        _filterState.update { it.copy(onlyUnread = !it.onlyUnread) }
    }

    fun toggleOnlyWithAttachments() {
        _filterState.update { it.copy(onlyWithAttachments = !it.onlyWithAttachments) }
    }

    fun openMessage(message: EmailWithAccountRow, isSplitLayout: Boolean = false) {
        _selectedDetailMessageId.value = message.id
        if (!isSplitLayout) {
            _activeScreen.value = MailScreen.MessageDetail(message.id)
        }
        if (!message.isRead) {
            viewModelScope.launch {
                repository.setRead(message.id, true)
            }
        }
    }

    fun openMessageByIdFromNotification(messageId: String) {
        _selectedDetailMessageId.value = messageId
        _activeScreen.value = MailScreen.MessageDetail(messageId)
        viewModelScope.launch {
            repository.setRead(messageId, true)
        }
    }

    fun openCompose(draft: ComposeDraft = ComposeDraft()) {
        val resolvedFromId = draft.fromAccountId
            ?: _filterState.value.selectedAccountId
            ?: accounts.value.firstOrNull()?.id
        _composeState.value = ComposeUiState()
        _activeScreen.value = MailScreen.Compose(draft.copy(fromAccountId = resolvedFromId))
    }

    fun openReply(message: EmailWithAccountRow, replyAll: Boolean) {
        val subjectPrefix = if (message.subject.startsWith("Re:", ignoreCase = true)) {
            message.subject
        } else {
            "Re: ${message.subject}"
        }
        val cc = if (replyAll) message.ccRecipients else ""
        val quotedBody = "\n\n--- Исходное сообщение от ${message.fromName} ---\n${message.bodyText.take(1500)}"
        openCompose(
            ComposeDraft(
                fromAccountId = message.accountId,
                to = message.fromEmail.ifBlank { message.fromName },
                cc = cc,
                subject = subjectPrefix,
                body = quotedBody
            )
        )
    }

    fun openForward(message: EmailWithAccountRow) {
        val subjectPrefix = if (message.subject.startsWith("Fwd:", ignoreCase = true)) {
            message.subject
        } else {
            "Fwd: ${message.subject}"
        }
        val body = "\n\n--- Пересланное сообщение ---\nОт: ${message.fromName} <${message.fromEmail}>\nТема: ${message.subject}\n\n${message.bodyText.take(3000)}"
        openCompose(
            ComposeDraft(
                fromAccountId = message.accountId,
                to = "",
                subject = subjectPrefix,
                body = body
            )
        )
    }

    fun openAccounts(preselectProvider: MailProvider? = null) {
        _preselectedProviderForConnect.value = preselectProvider
        _connectState.value = AccountConnectUiState()
        _activeScreen.value = MailScreen.Accounts
    }

    fun clearPreselectedProvider() {
        _preselectedProviderForConnect.value = null
    }

    fun openSettings() {
        _activeScreen.value = MailScreen.Settings
    }

    fun navigateBackToInbox() {
        _activeScreen.value = MailScreen.Inbox
    }

    fun toggleMessageSelection(messageId: String) {
        _selectedMessageIds.update { current ->
            if (messageId in current) current - messageId else current + messageId
        }
    }

    fun selectAllVisibleMessages() {
        _selectedMessageIds.value = messages.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun syncAllNow(notifyNew: Boolean = true) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val folder = _filterState.value.selectedFolder
                repository.syncAllAccounts(folder = folder, notifyNew = notifyNew)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncCurrentScope(notifyNew: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val filter = _filterState.value
                val targetAccountId = filter.selectedAccountId
                if (targetAccountId == null) {
                    repository.syncAllAccounts(folder = filter.selectedFolder, notifyNew = notifyNew)
                } else {
                    val account = accounts.value.firstOrNull { it.id == targetAccountId }
                    if (account != null) {
                        repository.syncAccountFolder(account, filter.selectedFolder, notifyNew)
                    }
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncSingleAccount(account: MailAccount) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val res = repository.syncAccountFolder(account, MailFolderType.INBOX, notifyNew = false)
                res.onSuccess {
                    showSnackbar("Ящик ${account.email} обновлён")
                }.onFailure { err ->
                    showSnackbar(err.message ?: "Ошибка синхронизации")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun connectAccount(
        provider: MailProvider,
        email: String,
        displayName: String,
        password: String,
        imapHost: String,
        imapPort: Int,
        smtpHost: String,
        smtpPort: Int,
        useSsl: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _connectState.value = AccountConnectUiState(isConnecting = true, errorMessage = null)
            val result = repository.connectAccount(
                provider = provider,
                email = email,
                displayName = displayName,
                password = password,
                imapHost = imapHost,
                imapPort = imapPort,
                smtpHost = smtpHost,
                smtpPort = smtpPort,
                useSsl = useSsl
            )
            result.fold(
                onSuccess = { saved ->
                    _connectState.value = AccountConnectUiState(isConnecting = false)
                    showSnackbar("Подключён ящик ${saved.email}")
                    onSuccess()
                },
                onFailure = { error ->
                    _connectState.value = AccountConnectUiState(
                        isConnecting = false,
                        errorMessage = error.message ?: "Не удалось подключить почтовый ящик"
                    )
                }
            )
        }
    }

    fun removeAccount(account: MailAccount) {
        viewModelScope.launch {
            realtimeJobs.remove(account.id)?.cancel()
            _realtimeConnectedIds.update { it - account.id }
            repository.removeAccount(account.id)
            showSnackbar("Ящик ${account.email} отключён")
        }
    }

    fun sendEmail(
        fromAccountId: Long,
        to: String,
        cc: String,
        subject: String,
        body: String
    ) {
        viewModelScope.launch {
            _composeState.value = ComposeUiState(isSending = true, errorMessage = null)
            val result = repository.sendEmail(fromAccountId, to, cc, subject, body)
            result.fold(
                onSuccess = {
                    _composeState.value = ComposeUiState(isSending = false)
                    val fromEmail = accounts.value.firstOrNull { it.id == fromAccountId }?.email.orEmpty()
                    showSnackbar("Письмо отправлено с $fromEmail")
                    _activeScreen.value = MailScreen.Inbox
                },
                onFailure = { err ->
                    _composeState.value = ComposeUiState(
                        isSending = false,
                        errorMessage = err.message ?: "Не удалось отправить письмо"
                    )
                }
            )
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            if (_selectedDetailMessageId.value == messageId) {
                _selectedDetailMessageId.value = null
            }
            if ((_activeScreen.value as? MailScreen.MessageDetail)?.messageId == messageId) {
                _activeScreen.value = MailScreen.Inbox
            }
            val result = repository.deleteMessages(listOf(messageId))
            result.onSuccess {
                showSnackbar("Письмо удалено")
            }.onFailure { err ->
                showSnackbar("Удалено локально; сервер: ${err.message ?: "ошибка"}")
            }
        }
    }

    fun deleteSelectedMessages() {
        val ids = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        _selectedMessageIds.value = emptySet()
        viewModelScope.launch {
            val result = repository.deleteMessages(ids)
            result.onSuccess {
                showSnackbar("Удалено писем: ${ids.size}")
            }.onFailure {
                showSnackbar("Письма удалены (часть может требовать повторной синхронизации)")
            }
        }
    }

    fun markSelectedRead(isRead: Boolean) {
        val ids = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        _selectedMessageIds.value = emptySet()
        viewModelScope.launch {
            repository.markBatchRead(ids, isRead)
            showSnackbar(if (isRead) "Отмечено как прочитанное" else "Отмечено как непрочитанное")
        }
    }

    fun toggleMessageRead(message: EmailWithAccountRow) {
        viewModelScope.launch {
            repository.setRead(message.id, !message.isRead)
        }
    }

    fun toggleMessageStarred(messageId: String) {
        viewModelScope.launch {
            repository.toggleStarred(messageId)
        }
    }

    fun clearMailboxFolder(accountId: Long?, folder: MailFolderType) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.clearMailboxFolder(accountId, folder)
                val targetName = if (accountId == null) {
                    "всех ящиков"
                } else {
                    accounts.value.firstOrNull { it.id == accountId }?.email ?: "ящика"
                }
                result.onSuccess {
                    showSnackbar("Папка «${folder.titleRu}» ($targetName) очищена")
                }.onFailure { err ->
                    showSnackbar("Очищено локально; ошибка сервера: ${err.message ?: "проверьте сеть"}")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setRealtimeBackgroundPushEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setRealtimeBackgroundPushEnabled(enabled) }
    }

    fun setShowHtmlContentByDefault(enabled: Boolean) {
        viewModelScope.launch { repository.setShowHtmlContentByDefault(enabled) }
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        realtimeJobs.values.forEach { it.cancel() }
        realtimeJobs.clear()
        super.onCleared()
    }
}
