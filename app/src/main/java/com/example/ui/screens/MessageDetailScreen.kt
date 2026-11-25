package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.EmailWithAccountRow
import com.example.ui.components.ProviderBadge
import com.example.ui.components.SenderAvatar
import com.example.ui.components.formatDetailedTimestamp
import com.example.ui.theme.StarGold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageDetailScreen(
    message: EmailWithAccountRow?,
    showHtmlByDefault: Boolean,
    isSplitPane: Boolean = false,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onToggleRead: (EmailWithAccountRow) -> Unit,
    onReply: (EmailWithAccountRow) -> Unit,
    onReplyAll: (EmailWithAccountRow) -> Unit,
    onForward: (EmailWithAccountRow) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isSplitPane) {
        BackHandler(onBack = onBack)
    }

    if (message == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Выберите письмо для просмотра",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var renderAsHtml by remember(message.id, showHtmlByDefault) {
        mutableStateOf(showHtmlByDefault && !message.bodyHtml.isNullOrBlank())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = if (isSplitPane) WindowInsets(0, 0, 0, 0) else WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (!isSplitPane) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderBadge(provider = message.provider, size = 22.dp)
                        Text(
                            text = message.accountEmail,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (!message.bodyHtml.isNullOrBlank()) {
                        IconButton(
                            onClick = { renderAsHtml = !renderAsHtml },
                            modifier = Modifier.testTag("detail_toggle_html_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = if (renderAsHtml) "Показать простой текст" else "Показать HTML",
                                tint = if (renderAsHtml) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleRead(message) },
                        modifier = Modifier.testTag("detail_toggle_read_button")
                    ) {
                        Icon(
                            imageVector = if (message.isRead) Icons.Filled.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                            contentDescription = if (message.isRead) "Отметить непрочитанным" else "Отметить прочитанным"
                        )
                    }
                    IconButton(
                        onClick = { onToggleStar(message.id) },
                        modifier = Modifier.testTag("detail_star_button")
                    ) {
                        Icon(
                            imageVector = if (message.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Избранное",
                            tint = if (message.isStarred) StarGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onDelete(message.id) },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Удалить письмо",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onReply(message) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_reply_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ответить", maxLines = 1)
                    }
                    if (message.ccRecipients.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onReplyAll(message) },
                            modifier = Modifier.testTag("detail_reply_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReplyAll,
                                contentDescription = "Ответить всем",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onForward(message) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Переслать", maxLines = 1)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = message.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Folder & Mailbox origin pill
            Surface(
                color = message.provider.brandColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderBadge(provider = message.provider, size = 16.dp)
                    Text(
                        text = "${message.provider.displayName} • ${message.folderType.titleRu}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sender Header Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SenderAvatar(
                            name = message.fromName,
                            email = message.fromEmail,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.fromName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (message.fromEmail.isNotBlank() && message.fromEmail != message.fromName) {
                                Text(
                                    text = message.fromEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatDetailedTimestamp(message.sentAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (message.toRecipients.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Кому: ${message.toRecipients}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (message.ccRecipients.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Копия: ${message.ccRecipients}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Attachments list
            if (message.attachmentsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.attachmentsList.forEach { fileName ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Email Body
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val htmlContent = message.bodyHtml
                if (renderAsHtml && !htmlContent.isNullOrBlank()) {
                    SafeEmailHtmlViewer(
                        rawHtml = htmlContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = message.bodyText.ifBlank { "(Пустое сообщение)" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Hardened HTML email viewer: JavaScript, file access, content access, and DOM storage are strictly disabled.
 * External link navigation is restricted to http, https, and mailto schemes only.
 */
@Composable
private fun SafeEmailHtmlViewer(
    rawHtml: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surface.toArgb())
    val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())

    // Strip any script/iframe/object/embed tags defensively before loading into WebView
    val sanitizedHtml = remember(rawHtml, bgColorHex, textColorHex, linkColorHex) {
        val stripped = rawHtml
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<iframe[^>]*>.*?</iframe>"), "")
            .replace(Regex("(?is)<object[^>]*>.*?</object>"), "")
            .replace(Regex("(?is)<embed[^>]*>"), "")
            .replace(Regex("(?i)\\son\\w+\\s*=\\s*(['\"]).*?\\1"), "")
            .replace(Regex("(?i)javascript:"), "")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0" />
            <style>
                body {
                    margin: 8px;
                    padding: 0;
                    background-color: $bgColorHex;
                    color: $textColorHex;
                    font-family: sans-serif;
                    font-size: 15px;
                    line-height: 1.55;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }
                img { max-width: 100%; height: auto; border-radius: 8px; }
                table { max-width: 100%; }
                a { color: $linkColorHex; }
                pre, code { white-space: pre-wrap; word-break: break-word; }
            </style>
        </head>
        <body>$stripped</body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    loadWithOverviewMode = true
                    useWideViewPort = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "https" || scheme == "http" || scheme == "mailto") {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://localhost/",
                sanitizedHtml,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}
