package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MailAccount
import com.example.model.MailFolderType
import com.example.model.MailProvider
import com.example.ui.components.ProviderBadge
import com.example.ui.components.formatLastSyncedTime
import com.example.ui.theme.LiveGreen
import com.example.ui.viewmodel.AccountConnectUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accounts: List<MailAccount>,
    unreadByAccount: Map<Long, Int>,
    realtimeConnectedIds: Set<Long>,
    connectState: AccountConnectUiState,
    preselectedProvider: MailProvider?,
    onClearPreselectedProvider: () -> Unit,
    onBack: () -> Unit,
    onSyncAccount: (MailAccount) -> Unit,
    onClearAccountFolder: (Long, MailFolderType) -> Unit,
    onRemoveAccount: (MailAccount) -> Unit,
    onConnectAccount: (
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
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showAddSheet by remember { mutableStateOf(preselectedProvider != null) }
    var sheetInitialProvider by remember { mutableStateOf(preselectedProvider ?: MailProvider.YANDEX) }
    var accountPendingRemove by remember { mutableStateOf<MailAccount?>(null) }
    var accountPendingClear by remember { mutableStateOf<MailAccount?>(null) }

    LaunchedEffect(preselectedProvider) {
        if (preselectedProvider != null) {
            sheetInitialProvider = preselectedProvider
            showAddSheet = true
            onClearPreselectedProvider()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("accounts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Почтовые ящики",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sheetInitialProvider = MailProvider.YANDEX
                    showAddSheet = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Подключить ящик") },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("accounts_add_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Быстрое добавление",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(MailProvider.entries) { provider ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(14.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    sheetInitialProvider = provider
                                    showAddSheet = true
                                }
                                .testTag("quick_add_${provider.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProviderBadge(provider = provider, size = 24.dp)
                                Text(
                                    text = provider.shortLabel,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Подключённые ящики (${accounts.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (accounts.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Нет подключённых ящиков",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Нажмите на Яндекс, Mail.ru или Gmail выше, чтобы подключить ваш первый ящик.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(accounts, key = { it.id }) { account ->
                    AccountManagementCard(
                        account = account,
                        unreadCount = unreadByAccount[account.id] ?: 0,
                        isRealtimeConnected = account.id in realtimeConnectedIds,
                        onSync = { onSyncAccount(account) },
                        onClear = { accountPendingClear = account },
                        onRemove = { accountPendingRemove = account }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        ConnectAccountSheet(
            initialProvider = sheetInitialProvider,
            connectState = connectState,
            onDismiss = { if (!connectState.isConnecting) showAddSheet = false },
            onConnect = { provider, email, displayName, password, imapHost, imapPort, smtpHost, smtpPort, useSsl ->
                onConnectAccount(
                    provider,
                    email,
                    displayName,
                    password,
                    imapHost,
                    imapPort,
                    smtpHost,
                    smtpPort,
                    useSsl
                ) {
                    showAddSheet = false
                }
            }
        )
    }

    accountPendingRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { accountPendingRemove = null },
            title = { Text("Отключить ящик?") },
            text = {
                Text("Аккаунт ${target.email} и локальный кэш его писем будут удалены с устройства. Письма на сервере останутся нетронутыми.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAccount(target)
                        accountPendingRemove = null
                    }
                ) {
                    Text("Отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingRemove = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    accountPendingClear?.let { target ->
        AlertDialog(
            onDismissRequest = { accountPendingClear = null },
            title = { Text("Очистить ящик ${target.email}?") },
            text = {
                Text("Выберите, какую папку очистить на сервере и в приложении:")
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            onClearAccountFolder(target.id, MailFolderType.INBOX)
                            accountPendingClear = null
                        }
                    ) {
                        Text("Очистить «Входящие»", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            onClearAccountFolder(target.id, MailFolderType.TRASH)
                            accountPendingClear = null
                        }
                    ) {
                        Text("Очистить «Корзину»", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            onClearAccountFolder(target.id, MailFolderType.SPAM)
                            accountPendingClear = null
                        }
                    ) {
                        Text("Очистить «Спам»", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingClear = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun AccountManagementCard(
    account: MailAccount,
    unreadCount: Int,
    isRealtimeConnected: Boolean,
    onSync: () -> Unit,
    onClear: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .testTag("account_card_${account.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(provider = account.provider, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.email,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRealtimeConnected) LiveGreen else MaterialTheme.colorScheme.outline
                                )
                        )
                        Text(
                            text = if (isRealtimeConnected) {
                                "В реальном времени • ${formatLastSyncedTime(account.lastSyncedAt)}"
                            } else {
                                formatLastSyncedTime(account.lastSyncedAt)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (unreadCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$unreadCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!account.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = account.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSync,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Обновить", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Очистить", maxLines = 1)
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Отключить аккаунт",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectAccountSheet(
    initialProvider: MailProvider,
    connectState: AccountConnectUiState,
    onDismiss: () -> Unit,
    onConnect: (
        provider: MailProvider,
        email: String,
        displayName: String,
        password: String,
        imapHost: String,
        imapPort: Int,
        smtpHost: String,
        smtpPort: Int,
        useSsl: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var provider by remember(initialProvider) { mutableStateOf(initialProvider) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var imapHost by remember(initialProvider) { mutableStateOf(initialProvider.defaultImapHost) }
    var imapPort by remember(initialProvider) { mutableStateOf(initialProvider.defaultImapPort.toString()) }
    var smtpHost by remember(initialProvider) { mutableStateOf(initialProvider.defaultSmtpHost) }
    var smtpPort by remember(initialProvider) { mutableStateOf(initialProvider.defaultSmtpPort.toString()) }
    var useSsl by remember { mutableStateOf(true) }
    var showAdvanced by remember(initialProvider) { mutableStateOf(initialProvider == MailProvider.CUSTOM) }

    fun applyProvider(newProvider: MailProvider) {
        provider = newProvider
        if (newProvider != MailProvider.CUSTOM) {
            imapHost = newProvider.defaultImapHost
            imapPort = newProvider.defaultImapPort.toString()
            smtpHost = newProvider.defaultSmtpHost
            smtpPort = newProvider.defaultSmtpPort.toString()
        } else {
            showAdvanced = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Подключение почтового ящика",
                style = MaterialTheme.typography.titleLarge
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MailProvider.entries) { item ->
                    FilterChip(
                        selected = provider == item,
                        onClick = { applyProvider(item) },
                        label = { Text(item.shortLabel) },
                        leadingIcon = { ProviderBadge(provider = item, size = 18.dp) },
                        modifier = Modifier.testTag("connect_provider_${item.name.lowercase()}")
                    )
                }
            }

            // Helpful provider setup card with direct link to official App Passwords page
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = provider.setupHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (provider.appPasswordUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                val uri = Uri.parse(provider.appPasswordUrl)
                                if (uri.scheme == "https") {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Открыть страницу паролей приложения")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (!connectState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = connectState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { value ->
                    email = value
                    if (value.contains("@")) {
                        val detected = MailProvider.detectFromEmail(value)
                        if (detected != MailProvider.CUSTOM && detected != provider) {
                            applyProvider(detected)
                        }
                    }
                },
                label = { Text("Адрес почты (E-mail)") },
                placeholder = { Text(provider.emailHint) },
                singleLine = true,
                enabled = !connectState.isConnecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль приложения") },
                placeholder = { Text("Введите пароль приложения IMAP") },
                singleLine = true,
                enabled = !connectState.isConnecting,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_password_input")
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Имя отправителя (необязательно)") },
                placeholder = { Text("Например: Алексей") },
                singleLine = true,
                enabled = !connectState.isConnecting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_name_input")
            )

            // Expandable IMAP / SMTP server configuration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Серверы IMAP / SMTP (${imapHost.ifBlank { "не задано" }}:$imapPort)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Настройки серверов",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = imapHost,
                        onValueChange = { imapHost = it },
                        label = { Text("IMAP сервер") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = imapPort,
                        onValueChange = { imapPort = it.filter(Char::isDigit) },
                        label = { Text("Порт") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP сервер") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it.filter(Char::isDigit) },
                        label = { Text("Порт") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Использовать SSL/TLS шифрование",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    onConnect(
                        provider,
                        email,
                        displayName,
                        password,
                        imapHost,
                        imapPort.toIntOrNull() ?: 993,
                        smtpHost,
                        smtpPort.toIntOrNull() ?: 465,
                        useSsl
                    )
                },
                enabled = !connectState.isConnecting && email.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("connect_submit_button")
            ) {
                if (connectState.isConnecting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Проверка подключения…")
                } else {
                    Text("Подключить и загрузить письма")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
