package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "linksave_prefs")

class UserPreferences(private val context: Context) {
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyDarkTheme] ?: true
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyDarkTheme] = enabled
        }
    }
}
