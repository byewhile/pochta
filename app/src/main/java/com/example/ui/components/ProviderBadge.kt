package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MailProvider
import com.example.ui.theme.CustomImapTeal
import com.example.ui.theme.GmailBrandBlue
import com.example.ui.theme.GmailBrandGreen
import com.example.ui.theme.GmailBrandRed
import com.example.ui.theme.GmailBrandYellow
import com.example.ui.theme.MailRuBrandBlue
import com.example.ui.theme.YandexBrandRed
import kotlin.math.abs

/**
 * Distinct, immediately recognizable logo badge for Yandex Mail, Mail.ru, Gmail, and Custom IMAP.
 */
@Composable
fun ProviderBadge(
    provider: MailProvider,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val cornerRadius = size * 0.3f
    val fontSizeSp = (size.value * 0.56f).sp

    when (provider) {
        MailProvider.YANDEX -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(YandexBrandRed)
                    .semantics { contentDescription = provider.displayName },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Я",
                    color = Color.White,
                    fontSize = fontSizeSp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = fontSizeSp
                )
            }
        }

        MailProvider.MAIL_RU -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MailRuBrandBlue)
                    .semantics { contentDescription = provider.displayName },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "@",
                    color = Color.White,
                    fontSize = (size.value * 0.54f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = fontSizeSp
                )
            }
        }

        MailProvider.GMAIL -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.White)
                    .border(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .semantics { contentDescription = provider.displayName },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(size * 0.68f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val strokeWidth = (w * 0.21f).coerceAtLeast(2.2f)

                    // Left vertical bar (Blue)
                    drawLine(
                        color = GmailBrandBlue,
                        start = Offset(w * 0.14f, h * 0.85f),
                        end = Offset(w * 0.14f, h * 0.22f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // Right vertical bar (Green)
                    drawLine(
                        color = GmailBrandGreen,
                        start = Offset(w * 0.86f, h * 0.85f),
                        end = Offset(w * 0.86f, h * 0.22f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // Right upper diagonal accent (Yellow)
                    drawLine(
                        color = GmailBrandYellow,
                        start = Offset(w * 0.86f, h * 0.22f),
                        end = Offset(w * 0.68f, h * 0.40f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // Main M envelope V (Gmail Red)
                    val vPath = Path().apply {
                        moveTo(w * 0.14f, h * 0.22f)
                        lineTo(w * 0.50f, h * 0.56f)
                        lineTo(w * 0.86f, h * 0.22f)
                    }
                    drawPath(
                        path = vPath,
                        color = GmailBrandRed,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        MailProvider.CUSTOM -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(CustomImapTeal)
                    .semantics { contentDescription = provider.displayName },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    color = Color.White,
                    fontSize = (size.value * 0.52f).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = fontSizeSp
                )
            }
        }
    }
}

/**
 * Multi-provider stacked emblem for the "Все ящики" (All Mailboxes) unified chip.
 */
@Composable
fun UnifiedAllBadge(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.22f)
                    .clip(CircleShape)
                    .background(YandexBrandRed)
            )
            Box(
                modifier = Modifier
                    .size(size * 0.22f)
                    .clip(CircleShape)
                    .background(MailRuBrandBlue)
            )
            Box(
                modifier = Modifier
                    .size(size * 0.22f)
                    .clip(CircleShape)
                    .background(GmailBrandRed)
            )
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF1D4ED8),
    Color(0xFF0F766E),
    Color(0xFF7C3AED),
    Color(0xFFB45309),
    Color(0xFFBE123C),
    Color(0xFF0369A1),
    Color(0xFF4338CA),
    Color(0xFF047857)
)

@Composable
fun SenderAvatar(
    name: String,
    email: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val seed = (name.ifBlank { email }).trim()
    val letter = seed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val bgColor = AvatarPalette[abs(seed.hashCode()) % AvatarPalette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = bgColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
