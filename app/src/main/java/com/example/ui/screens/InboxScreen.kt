package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.EmailWithAccountRow
import com.example.model.MailAccount
import com.example.model.MailFolderType
import com.example.model.MailProvider
import com.example.ui.components.EmailListItem
import com.example.ui.components.EmptyMessagesState
import com.example.ui.components.NoAccountsOnboardingView
import com.example.ui.components.ProviderBadge
import com.example.ui.components.UnifiedAllBadge
import com.example.ui.theme.LiveGreen
import com.example.ui.viewmodel.MailFilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    accounts: List<MailAccount>,
    unreadByAccount: Map<Long, Int>,
    messages: List<EmailWithAccountRow>,
    filterState: MailFilterState,
    selectedMessageIds: Set<String>,
    activeDetailMessage: EmailWithAccountRow?,
    showHtmlByDefault: Boolean,
    isSyncing: Boolean,
    realtimeConnectedIds: Set<Long>,
    hasNotificationPermission: Boolean,
    onSelectAccount: (Long?) -> Unit,
    onSelectFolder: (MailFolderType) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleOnlyUnread: () -> Unit,
    onToggleOnlyWithAttachments: () -> Unit,
    onOpenMessage: (EmailWithAccountRow, Boolean) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
    onSelectAllMessages: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onMarkSelectedRead: (Boolean) -> Unit,
    onToggleStar: (String) -> Unit,
    onToggleRead: (EmailWithAccountRow) -> Unit,
    onClearCurrentFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onCompose: () -> Unit,
    onOpenAccounts: (MailProvider?) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onReply: (EmailWithAccountRow) -> Unit,
    onReplyAll: (EmailWithAccountRow) -> Unit,
    onForward: (EmailWithAccountRow) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearFolderDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    val selectedAccount = accounts.firstOrNull { it.id == filterState.selectedAccountId }
    val totalUnread = unreadByAccount.values.sum()
    val isSelectionMode = selectedMessageIds.isNotEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isSplitLayout = maxWidth >= 840.dp && accounts.isNotEmpty()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (accounts.isNotEmpty() && !isSelectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = onCompose,
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        text = { Text("Написать") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("inbox_compose_fab")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // Top Search Bar or Multi-Select Action Bar
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedMessageIds.size,
                        onClearSelection = onClearSelection,
                        onSelectAll = onSelectAllMessages,
                        onMarkRead = { onMarkSelectedRead(true) },
                        onDeleteSelected = { showDeleteSelectedDialog = true }
                    )
                } else {
                    FloatingSearchHeader(
                        query = filterState.searchQuery,
                        placeholder = if (selectedAccount == null) {
                            "Поиск по всем ящикам…"
                        } else {
                            "Поиск в ${selectedAccount.email}…"
                        },
                        isSyncing = isSyncing,
                        realtimeConnectedCount = realtimeConnectedIds.size,
                        accountsCount = accounts.size,
                        onQueryChange = onSearchQueryChange,
                        onSyncNow = onSyncNow,
                        onOpenAccounts = { onOpenAccounts(null) },
                        onOpenSettings = onOpenSettings
                    )
                }

                if (accounts.isEmpty()) {
                    NoAccountsOnboardingView(
                        onSelectProvider = { provider -> onOpenAccounts(provider) },
                        modifier = Modifier
                            .weight(1f)
                            .navigationBarsPadding()
                    )
                } else {
                    // Real-time Mailbox Switcher Strip ("Все ящики" + each connected mailbox)
                    MailboxSwitcherStrip(
                        accounts = accounts,
                        selectedAccountId = filterState.selectedAccountId,
                        unreadByAccount = unreadByAccount,
                        totalUnread = totalUnread,
                        realtimeConnectedIds = realtimeConnectedIds,
                        onSelectAccount = onSelectAccount,
                        onAddAccount = { onOpenAccounts(null) }
                    )

                    // Folder & Quick Filter Bar + Clear Folder button
                    FolderAndFiltersBar(
                        selectedFolder = filterState.selectedFolder,
                        onlyUnread = filterState.onlyUnread,
                        onlyWithAttachments = filterState.onlyWithAttachments,
                        canClearFolder = messages.isNotEmpty() && filterState.selectedFolder != MailFolderType.STARRED,
                        onSelectFolder = onSelectFolder,
                        onToggleOnlyUnread = onToggleOnlyUnread,
                        onToggleOnlyWithAttachments = onToggleOnlyWithAttachments,
                        onRequestClearFolder = { showClearFolderDialog = true }
                    )

                    // Notification Permission Banner on Android 13+
                    AnimatedVisibility(visible = !hasNotificationPermission) {
                        NotificationPermissionBanner(
                            onEnable = onRequestNotificationPermission
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (isSplitLayout) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .width(420.dp)
                                    .fillMaxHeight()
                            ) {
                                EmailListPane(
                                    messages = messages,
                                    filterState = filterState,
                                    selectedMessageIds = selectedMessageIds,
                                    activeSplitMessageId = activeDetailMessage?.id,
                                    isSyncing = isSyncing,
                                    isSplitLayout = true,
                                    onRefresh = onSyncNow,
                                    onOpenMessage = { msg -> onOpenMessage(msg, true) },
                                    onToggleSelection = onToggleMessageSelection,
                                    onToggleStar = onToggleStar,
                                    onSwipeDelete = onDeleteMessage
                                )
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                MessageDetailScreen(
                                    message = activeDetailMessage,
                                    showHtmlByDefault = showHtmlByDefault,
                                    isSplitPane = true,
                                    onBack = {},
                                    onDelete = onDeleteMessage,
                                    onToggleStar = onToggleStar,
                                    onToggleRead = onToggleRead,
                                    onReply = onReply,
                                    onReplyAll = onReplyAll,
                                    onForward = onForward
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            EmailListPane(
                                messages = messages,
                                filterState = filterState,
                                selectedMessageIds = selectedMessageIds,
                                activeSplitMessageId = null,
                                isSyncing = isSyncing,
                                isSplitLayout = false,
                                onRefresh = onSyncNow,
                                onOpenMessage = { msg -> onOpenMessage(msg, false) },
                                onToggleSelection = onToggleMessageSelection,
                                onToggleStar = onToggleStar,
                                onSwipeDelete = onDeleteMessage
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearFolderDialog) {
        val scopeLabel = selectedAccount?.email ?: "всех подключённых ящиков"
        AlertDialog(
            onDismissRequest = { showClearFolderDialog = false },
            title = { Text("Очистить папку «${filterState.selectedFolder.titleRu}»?") },
            text = {
                Text("Все письма в папке «${filterState.selectedFolder.titleRu}» для $scopeLabel будут удалены в приложении и на почтовых серверах.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearFolderDialog = false
                        onClearCurrentFolder()
                    },
                    modifier = Modifier.testTag("confirm_clear_folder_button")
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFolderDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Удалить выбранные письма (${selectedMessageIds.size})?") },
            text = {
                Text("Письма будут удалены локально и на серверах соответствующих почтовых ящиков.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedDialog = false
                        onDeleteSelected()
                    },
                    modifier = Modifier.testTag("confirm_delete_selected_button")
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun FloatingSearchHeader(
    query: String,
    placeholder: String,
    isSyncing: Boolean,
    realtimeConnectedCount: Int,
    accountsCount: Int,
    onQueryChange: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenAccounts,
                    modifier = Modifier.testTag("header_accounts_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ManageAccounts,
                        contentDescription = "Управление ящиками",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inbox_search_input")
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("inbox_search_clear")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Очистить поиск"
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSyncNow,
                        modifier = Modifier.testTag("header_sync_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Обновить почту",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("header_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Настройки",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (accountsCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            RealtimeStatusRow(
                isSyncing = isSyncing,
                realtimeConnectedCount = realtimeConnectedCount,
                accountsCount = accountsCount
            )
        }
    }
}

@Composable
private fun RealtimeStatusRow(
    isSyncing: Boolean,
    realtimeConnectedCount: Int,
    accountsCount: Int
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (realtimeConnectedCount > 0) pulseAlpha else 0.7f)
                    .clip(CircleShape)
                    .background(
                        if (realtimeConnectedCount > 0) LiveGreen else MaterialTheme.colorScheme.outline
                    )
            )
            Text(
                text = when {
                    isSyncing -> "Синхронизация почты…"
                    realtimeConnectedCount > 0 -> "В реальном времени • $realtimeConnectedCount/$accountsCount ящ."
                    else -> "Подключено ящиков: $accountsCount"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Снять выделение",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "Выбрано: $selectedCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("selection_select_all_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = "Выбрать все",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(
                onClick = onMarkRead,
                modifier = Modifier.testTag("selection_mark_read_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MarkEmailRead,
                    contentDescription = "Отметить прочитанными",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(
                onClick = onDeleteSelected,
                modifier = Modifier.testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Удалить выбранные",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MailboxSwitcherStrip(
    accounts: List<MailAccount>,
    selectedAccountId: Long?,
    unreadByAccount: Map<Long, Int>,
    totalUnread: Int,
    realtimeConnectedIds: Set<Long>,
    onSelectAccount: (Long?) -> Unit,
    onAddAccount: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Unified "Все ящики" chip
        item(key = "all_mailboxes") {
            val isSelected = selectedAccountId == null
            MailboxPill(
                label = "Все ящики",
                unreadCount = totalUnread,
                isSelected = isSelected,
                isLive = realtimeConnectedIds.isNotEmpty(),
                onClick = { onSelectAccount(null) },
                testTagName = "mailbox_chip_all",
                leadingBadge = { UnifiedAllBadge(size = 24.dp) }
            )
        }

        // Individual Mailbox chips (Яндекс, Mail.ru, Gmail, etc.)
        items(accounts, key = { it.id }) { account ->
            val isSelected = selectedAccountId == account.id
            val shortEmail = account.email.substringBefore("@").take(14)
            MailboxPill(
                label = "${account.provider.shortLabel} • $shortEmail",
                unreadCount = unreadByAccount[account.id] ?: 0,
                isSelected = isSelected,
                isLive = account.id in realtimeConnectedIds,
                onClick = { onSelectAccount(account.id) },
                testTagName = "mailbox_chip_${account.id}",
                leadingBadge = { ProviderBadge(provider = account.provider, size = 24.dp) }
            )
        }

        // Quick Add Mailbox pill
        item(key = "add_mailbox") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(48.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onAddAccount)
                    .testTag("mailbox_chip_add")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Добавить почтовый ящик",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ящик",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MailboxPill(
    label: String,
    unreadCount: Int,
    isSelected: Boolean,
    isLive: Boolean,
    onClick: () -> Unit,
    testTagName: String,
    leadingBadge: @Composable () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .height(48.dp)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingBadge()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
            if (unreadCount > 0) {
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (isLive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(LiveGreen)
                )
            }
        }
    }
}

@Composable
private fun FolderAndFiltersBar(
    selectedFolder: MailFolderType,
    onlyUnread: Boolean,
    onlyWithAttachments: Boolean,
    canClearFolder: Boolean,
    onSelectFolder: (MailFolderType) -> Unit,
    onToggleOnlyUnread: () -> Unit,
    onToggleOnlyWithAttachments: () -> Unit,
    onRequestClearFolder: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(MailFolderType.entries) { folder ->
            FilterChip(
                selected = selectedFolder == folder,
                onClick = { onSelectFolder(folder) },
                label = { Text(folder.titleRu) },
                modifier = Modifier.testTag("folder_chip_${folder.dbKey.lowercase()}")
            )
        }

        item(key = "filter_unread") {
            FilterChip(
                selected = onlyUnread,
                onClick = onToggleOnlyUnread,
                label = { Text("Непрочитанные") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailUnread,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("filter_only_unread")
            )
        }

        item(key = "filter_attachments") {
            FilterChip(
                selected = onlyWithAttachments,
                onClick = onToggleOnlyWithAttachments,
                label = { Text("С файлами") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("filter_only_attachments")
            )
        }

        if (canClearFolder) {
            item(key = "clear_folder_action") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRequestClearFolder)
                        .testTag("clear_current_folder_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = "Очистить папку",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Очистить",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(
    onEnable: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Включите push-уведомления о новых письмах",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onEnable,
                modifier = Modifier.testTag("banner_enable_notifications_button")
            ) {
                Text("Включить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailListPane(
    messages: List<EmailWithAccountRow>,
    filterState: MailFilterState,
    selectedMessageIds: Set<String>,
    activeSplitMessageId: String?,
    isSyncing: Boolean,
    isSplitLayout: Boolean,
    onRefresh: () -> Unit,
    onOpenMessage: (EmailWithAccountRow) -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onSwipeDelete: (String) -> Unit
) {
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    val isUnifiedView = filterState.selectedAccountId == null

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (messages.isEmpty()) {
            EmptyMessagesState(
                isSearchActive = filterState.searchQuery.isNotBlank() ||
                    filterState.onlyUnread ||
                    filterState.onlyWithAttachments,
                folder = filterState.selectedFolder,
                onSyncNow = onRefresh
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    EmailListItem(
                        message = message,
                        showAccountOriginBadge = isUnifiedView,
                        isSelected = message.id in selectedMessageIds,
                        isActiveInSplitPane = isSplitLayout && message.id == activeSplitMessageId,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(message.id)
                            } else {
                                onOpenMessage(message)
                            }
                        },
                        onLongClick = { onToggleSelection(message.id) },
                        onToggleStar = { onToggleStar(message.id) },
                        onSwipeDelete = { onSwipeDelete(message.id) }
                    )
                }
            }
        }
    }
}
