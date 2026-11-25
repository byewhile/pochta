package com.example.model

enum class MailFolderType(
    val dbKey: String,
    val titleRu: String,
    val candidateRemoteNames: List<String>,
    val specialUseAttribute: String?
) {
    INBOX(
        dbKey = "INBOX",
        titleRu = "Входящие",
        candidateRemoteNames = listOf("INBOX"),
        specialUseAttribute = null
    ),
    SENT(
        dbKey = "SENT",
        titleRu = "Отправленные",
        candidateRemoteNames = listOf(
            "Sent",
            "[Gmail]/Sent Mail",
            "[Gmail]/Отправленные",
            "Sent Messages",
            "Sent Items",
            "Отправленные"
        ),
        specialUseAttribute = "\\Sent"
    ),
    STARRED(
        dbKey = "STARRED",
        titleRu = "Избранные",
        candidateRemoteNames = listOf("INBOX"),
        specialUseAttribute = "\\Flagged"
    ),
    SPAM(
        dbKey = "SPAM",
        titleRu = "Спам",
        candidateRemoteNames = listOf(
            "Spam",
            "[Gmail]/Spam",
            "[Gmail]/Спам",
            "Junk",
            "Bulk Mail",
            "Спам"
        ),
        specialUseAttribute = "\\Junk"
    ),
    TRASH(
        dbKey = "TRASH",
        titleRu = "Корзина",
        candidateRemoteNames = listOf(
            "Trash",
            "[Gmail]/Trash",
            "[Gmail]/Корзина",
            "Deleted Items",
            "Deleted Messages",
            "Корзина",
            "Удаленные"
        ),
        specialUseAttribute = "\\Trash"
    );

    companion object {
        fun fromKey(key: String): MailFolderType =
            entries.firstOrNull { it.dbKey == key } ?: INBOX
    }
}
