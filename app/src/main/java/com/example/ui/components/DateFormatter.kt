package com.example.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ruLocale = Locale("ru", "RU")

fun formatEmailTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }

    val sameYear = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val yesterday = sameYear && (now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1)

    return when {
        sameDay -> SimpleDateFormat("HH:mm", ruLocale).format(Date(timestampMillis))
        yesterday -> "Вчера"
        sameYear -> SimpleDateFormat("d MMM", ruLocale).format(Date(timestampMillis))
        else -> SimpleDateFormat("dd.MM.yy", ruLocale).format(Date(timestampMillis))
    }
}

fun formatDetailedTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    return SimpleDateFormat("d MMMM yyyy, HH:mm", ruLocale).format(Date(timestampMillis))
}

fun formatLastSyncedTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Ещё не синхронизировано"
    val diffSec = ((System.currentTimeMillis() - timestampMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Обновлено только что"
        diffSec < 3600L -> "Обновлено ${diffSec / 60} мин назад"
        else -> "Обновлено в ${SimpleDateFormat("HH:mm", ruLocale).format(Date(timestampMillis))}"
    }
}
