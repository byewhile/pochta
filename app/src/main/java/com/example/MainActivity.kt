package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.MailNotificationHelper
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.ComposeScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.MessageDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.UnifiedMailTheme
import com.example.ui.viewmodel.MailScreen
import com.example.ui.viewmodel.MailViewModel

class MainActivity : ComponentActivity() {

    private val mailViewModel: MailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)

        setContent {
            UnifiedMailTheme {
                UnifiedMailRoot(
                    viewModel = mailViewModel,
                    notificationHelper = (application as UnifiedMailApp).notificationHelper
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val messageId = intent?.getStringExtra(MailNotificationHelper.EXTRA_OPEN_MESSAGE_ID)
        if (!messageId.isNullOrBlank()) {
            mailViewModel.openMessageByIdFromNotification(messageId)
        }
    }
}

@Composable
private fun UnifiedMailRoot(
    viewModel: MailViewModel,
    notificationHelper: MailNotificationHelper
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val unreadByAccount by viewModel.unreadByAccount.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
    val activeDetailMessage by viewModel.activeDetailMessage.collectAsStateWithLifecycle()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val realtimeConnectedIds by viewModel.realtimeConnectedIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val composeState by viewModel.composeState.collectAsStateWithLifecycle()
    val preselectedProvider by viewModel.preselectedProviderForConnect.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var hasNotificationPermission by remember {
        mutableStateOf(notificationHelper.hasNotificationPermission())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || notificationHelper.hasNotificationPermission()
        if (granted) {
            viewModel.showSnackbar("Push-уведомления включены")
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = activeScreen,
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is MailScreen.Inbox -> {
                    InboxScreen(
                        accounts = accounts,
                        unreadByAccount = unreadByAccount,
                        messages = messages,
                        filterState = filterState,
                        selectedMessageIds = selectedMessageIds,
                        activeDetailMessage = activeDetailMessage,
                        showHtmlByDefault = settings.showHtmlContentByDefault,
                        isSyncing = isSyncing,
                        realtimeConnectedIds = realtimeConnectedIds,
                        hasNotificationPermission = hasNotificationPermission,
                        onSelectAccount = viewModel::selectAccount,
                        onSelectFolder = viewModel::selectFolder,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onToggleOnlyUnread = viewModel::toggleOnlyUnread,
                        onToggleOnlyWithAttachments = viewModel::toggleOnlyWithAttachments,
                        onOpenMessage = viewModel::openMessage,
                        onToggleMessageSelection = viewModel::toggleMessageSelection,
                        onSelectAllMessages = viewModel::selectAllVisibleMessages,
                        onClearSelection = viewModel::clearSelection,
                        onDeleteMessage = viewModel::deleteMessage,
                        onDeleteSelected = viewModel::deleteSelectedMessages,
                        onMarkSelectedRead = viewModel::markSelectedRead,
                        onToggleStar = viewModel::toggleMessageStarred,
                        onToggleRead = viewModel::toggleMessageRead,
                        onClearCurrentFolder = {
                            viewModel.clearMailboxFolder(
                                accountId = filterState.selectedAccountId,
                                folder = filterState.selectedFolder
                            )
                        },
                        onSyncNow = { viewModel.syncCurrentScope(notifyNew = false) },
                        onCompose = { viewModel.openCompose() },
                        onOpenAccounts = viewModel::openAccounts,
                        onOpenSettings = viewModel::openSettings,
                        onRequestNotificationPermission = requestNotificationPermission,
                        onReply = { msg -> viewModel.openReply(msg, replyAll = false) },
                        onReplyAll = { msg -> viewModel.openReply(msg, replyAll = true) },
                        onForward = viewModel::openForward
                    )
                }

                is MailScreen.MessageDetail -> {
                    MessageDetailScreen(
                        message = activeDetailMessage,
                        showHtmlByDefault = settings.showHtmlContentByDefault,
                        isSplitPane = false,
                        onBack = viewModel::navigateBackToInbox,
                        onDelete = viewModel::deleteMessage,
                        onToggleStar = viewModel::toggleMessageStarred,
                        onToggleRead = viewModel::toggleMessageRead,
                        onReply = { msg -> viewModel.openReply(msg, replyAll = false) },
                        onReplyAll = { msg -> viewModel.openReply(msg, replyAll = true) },
                        onForward = viewModel::openForward
                    )
                }

                is MailScreen.Compose -> {
                    ComposeScreen(
                        accounts = accounts,
                        initialDraft = screen.draft,
                        composeState = composeState,
                        onBack = viewModel::navigateBackToInbox,
                        onSend = viewModel::sendEmail,
                        onOpenAccounts = { viewModel.openAccounts(null) }
                    )
                }

                is MailScreen.Accounts -> {
                    AccountsScreen(
                        accounts = accounts,
                        unreadByAccount = unreadByAccount,
                        realtimeConnectedIds = realtimeConnectedIds,
                        connectState = connectState,
                        preselectedProvider = preselectedProvider,
                        onClearPreselectedProvider = viewModel::clearPreselectedProvider,
                        onBack = viewModel::navigateBackToInbox,
                        onSyncAccount = viewModel::syncSingleAccount,
                        onClearAccountFolder = viewModel::clearMailboxFolder,
                        onRemoveAccount = viewModel::removeAccount,
                        onConnectAccount = viewModel::connectAccount
                    )
                }

                is MailScreen.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        hasNotificationPermission = hasNotificationPermission,
                        connectedAccountsCount = accounts.size,
                        realtimeConnectedCount = realtimeConnectedIds.size,
                        onBack = viewModel::navigateBackToInbox,
                        onRequestNotificationPermission = requestNotificationPermission,
                        onToggleNotifications = viewModel::setNotificationsEnabled,
                        onToggleRealtimePush = viewModel::setRealtimeBackgroundPushEnabled,
                        onToggleShowHtml = viewModel::setShowHtmlContentByDefault,
                        onSyncAllNow = {
                            viewModel.syncAllNow(notifyNew = false)
                            viewModel.showSnackbar("Запущена синхронизация всех ящиков")
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}
