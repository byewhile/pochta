package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mailPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "unified_mail_settings"
)

data class MailUserSettings(
    val notificationsEnabled: Boolean = true,
    val realtimeBackgroundPushEnabled: Boolean = true,
    val showHtmlContentByDefault: Boolean = true
)

class AppPreferences(private val context: Context) {

    val settingsFlow: Flow<MailUserSettings> = context.mailPreferencesDataStore.data.map { prefs ->
        MailUserSettings(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            realtimeBackgroundPushEnabled = prefs[KEY_REALTIME_PUSH_ENABLED] ?: true,
            showHtmlContentByDefault = prefs[KEY_SHOW_HTML_DEFAULT] ?: true
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.mailPreferencesDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setRealtimeBackgroundPushEnabled(enabled: Boolean) {
        context.mailPreferencesDataStore.edit { it[KEY_REALTIME_PUSH_ENABLED] = enabled }
    }

    suspend fun setShowHtmlContentByDefault(enabled: Boolean) {
        context.mailPreferencesDataStore.edit { it[KEY_SHOW_HTML_DEFAULT] = enabled }
    }

    companion object {
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_REALTIME_PUSH_ENABLED = booleanPreferencesKey("realtime_push_enabled")
        private val KEY_SHOW_HTML_DEFAULT = booleanPreferencesKey("show_html_default")
    }
}
