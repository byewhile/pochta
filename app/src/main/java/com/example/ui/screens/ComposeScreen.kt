package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MailAccount
import com.example.ui.components.ProviderBadge
import com.example.ui.viewmodel.ComposeDraft
import com.example.ui.viewmodel.ComposeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    accounts: List<MailAccount>,
    initialDraft: ComposeDraft,
    composeState: ComposeUiState,
    onBack: () -> Unit,
    onSend: (fromAccountId: Long, to: String, cc: String, subject: String, body: String) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = !composeState.isSending, onBack = onBack)

    var selectedAccountId by rememberSaveable {
        mutableLongStateOf(initialDraft.fromAccountId ?: accounts.firstOrNull()?.id ?: -1L)
    }
    var to by rememberSaveable { mutableStateOf(initialDraft.to) }
    var cc by rememberSaveable { mutableStateOf(initialDraft.cc) }
    var showCc by rememberSaveable { mutableStateOf(initialDraft.cc.isNotBlank()) }
    var subject by rememberSaveable { mutableStateOf(initialDraft.subject) }
    var body by rememberSaveable { mutableStateOf(initialDraft.body) }
    var showAccountPicker by remember { mutableStateOf(false) }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()

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
                        enabled = !composeState.isSending,
                        modifier = Modifier.testTag("compose_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Закрыть"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Новое письмо",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (composeState.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                val senderId = selectedAccount?.id ?: return@IconButton
                                onSend(senderId, to, cc, subject, body)
                            },
                            enabled = selectedAccount != null && to.isNotBlank(),
                            modifier = Modifier.testTag("compose_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить письмо",
                                tint = if (selectedAccount != null && to.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (accounts.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Сначала подключите хотя бы один почтовый ящик",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onOpenAccounts) {
                            Text("Перейти к подключению")
                        }
                    }
                }
            } else {
                // Sender ("От кого") selector card
                Text(
                    text = "Отправить с почты",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !composeState.isSending) {
                            showAccountPicker = true
                        }
                        .testTag("compose_from_account_selector")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedAccount != null) {
                            ProviderBadge(provider = selectedAccount.provider, size = 34.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedAccount.email,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedAccount.provider.displayName} • ${selectedAccount.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = "Выбрать ящик отправителя",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!composeState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = composeState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Кому") },
                placeholder = { Text("recipient@example.com") },
                singleLine = true,
                enabled = !composeState.isSending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                trailingIcon = {
                    if (!showCc) {
                        TextButton(onClick = { showCc = true }) {
                            Text("Копия")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_to_input")
            )

            if (showCc) {
                OutlinedTextField(
                    value = cc,
                    onValueChange = { cc = it },
                    label = { Text("Копия (CC)") },
                    placeholder = { Text("copy@example.com") },
                    singleLine = true,
                    enabled = !composeState.isSending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_cc_input")
                )
            }

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Тема") },
                placeholder = { Text("Тема письма") },
                singleLine = true,
                enabled = !composeState.isSending,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_subject_input")
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Текст письма") },
                placeholder = { Text("Напишите сообщение…") },
                enabled = !composeState.isSending,
                minLines = 9,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_body_input")
            )
        }
    }

    if (showAccountPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAccountPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Выберите ящик отправителя",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                accounts.forEachIndexed { index, account ->
                    val isSelected = account.id == selectedAccount?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedAccountId = account.id
                                showAccountPicker = false
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("compose_pick_account_${account.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProviderBadge(provider = account.provider, size = 34.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = account.provider.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Выбранный ящик",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (index < accounts.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
