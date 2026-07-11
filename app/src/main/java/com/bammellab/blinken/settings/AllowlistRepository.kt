package com.bammellab.blinken.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blinken_settings")
private val APP_SETTINGS_KEY = stringPreferencesKey("app_settings_json")

private val json = Json { ignoreUnknownKeys = true }

class AllowlistRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[APP_SETTINGS_KEY]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings.DEFAULT
    }

    suspend fun addOrUpdate(entry: AppEntry) {
        update { current ->
            val withoutExisting = current.entries.filterNot { it.packageName == entry.packageName }
            current.copy(entries = withoutExisting + entry)
        }
    }

    suspend fun remove(packageName: String) {
        update { current ->
            current.copy(entries = current.entries.filterNot { it.packageName == packageName })
        }
    }

    suspend fun setEcoModeEnabled(enabled: Boolean) {
        update { current -> current.copy(ecoModeEnabled = enabled) }
    }

    private suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[APP_SETTINGS_KEY]?.let { raw ->
                runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
            } ?: AppSettings.DEFAULT
            prefs[APP_SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }
}
