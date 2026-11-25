package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CustomImapTeal
import com.example.ui.theme.GmailBrandRed
import com.example.ui.theme.MailRuBrandBlue
import com.example.ui.theme.YandexBrandRed

enum class MailProvider(
    val displayName: String,
    val shortLabel: String,
    val defaultImapHost: String,
    val defaultImapPort: Int,
    val defaultSmtpHost: String,
    val defaultSmtpPort: Int,
    val brandColor: Color,
    val emailHint: String,
    val appPasswordUrl: String,
    val setupHint: String
) {
    YANDEX(
        displayName = "Яндекс Почта",
        shortLabel = "Яндекс",
        defaultImapHost = "imap.yandex.ru",
        defaultImapPort = 993,
        defaultSmtpHost = "smtp.yandex.ru",
        defaultSmtpPort = 465,
        brandColor = YandexBrandRed,
        emailHint = "name@yandex.ru",
        appPasswordUrl = "https://id.yandex.ru/security/app-passwords",
        setupHint = "Включите IMAP в настройках Яндекс Почты и создайте «Пароль приложения» для почты в Яндекс ID."
    ),
    MAIL_RU(
        displayName = "Mail.ru",
        shortLabel = "Mail.ru",
        defaultImapHost = "imap.mail.ru",
        defaultImapPort = 993,
        defaultSmtpHost = "smtp.mail.ru",
        defaultSmtpPort = 465,
        brandColor = MailRuBrandBlue,
        emailHint = "name@mail.ru",
        appPasswordUrl = "https://account.mail.ru/user/2-step-auth/passwords/",
        setupHint = "В настройках безопасности Mail.ru откройте «Пароли для внешних приложений» и создайте новый пароль."
    ),
    GMAIL(
        displayName = "Gmail",
        shortLabel = "Gmail",
        defaultImapHost = "imap.gmail.com",
        defaultImapPort = 993,
        defaultSmtpHost = "smtp.gmail.com",
        defaultSmtpPort = 465,
        brandColor = GmailBrandRed,
        emailHint = "name@gmail.com",
        appPasswordUrl = "https://myaccount.google.com/apppasswords",
        setupHint = "Включите двухэтапную аутентификацию в аккаунте Google и сгенерируйте 16-значный пароль приложения."
    ),
    CUSTOM(
        displayName = "Другой IMAP",
        shortLabel = "IMAP",
        defaultImapHost = "",
        defaultImapPort = 993,
        defaultSmtpHost = "",
        defaultSmtpPort = 465,
        brandColor = CustomImapTeal,
        emailHint = "name@company.ru",
        appPasswordUrl = "",
        setupHint = "Укажите IMAP и SMTP серверы вашего почтового провайдера с поддержкой SSL/TLS."
    );

    companion object {
        fun fromNameSafe(name: String): MailProvider =
            entries.firstOrNull { it.name == name } ?: CUSTOM

        fun detectFromEmail(email: String): MailProvider {
            val domain = email.substringAfter('@', "").lowercase().trim()
            return when {
                domain.endsWith("yandex.ru") ||
                    domain.endsWith("yandex.com") ||
                    domain.endsWith("ya.ru") ||
                    domain.endsWith("yandex.by") ||
                    domain.endsWith("yandex.kz") -> YANDEX
                domain.endsWith("mail.ru") ||
                    domain.endsWith("bk.ru") ||
                    domain.endsWith("inbox.ru") ||
                    domain.endsWith("list.ru") ||
                    domain.endsWith("internet.ru") -> MAIL_RU
                domain.endsWith("gmail.com") || domain.endsWith("googlemail.com") -> GMAIL
                else -> CUSTOM
            }
        }
    }
}
