package com.example.financetracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.financetracker.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore by preferencesDataStore(name = "ui_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-level user preferences: monthly allowance, theme, and whether chat may use the on-device model. */
class UiPrefs private constructor(context: Context) {
    private val ds = context.applicationContext.uiDataStore

    /** Null until the user sets one. */
    val allowance: Flow<Money?> = ds.data.map { p -> p[ALLOWANCE]?.takeIf { it > 0 }?.let(::Money) }

    val themeMode: Flow<ThemeMode> = ds.data.map { p ->
        p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** User switch for on-device AI in chat. Only has an effect when a local model is available. */
    val localAiEnabled: Flow<Boolean> = ds.data.map { p -> p[LOCAL_AI] ?: true }

    suspend fun setAllowance(value: Money?) {
        ds.edit { p -> if (value == null || value.minor <= 0) p.remove(ALLOWANCE) else p[ALLOWANCE] = value.minor }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        ds.edit { it[THEME] = mode.name }
    }

    suspend fun setLocalAiEnabled(enabled: Boolean) {
        ds.edit { it[LOCAL_AI] = enabled }
    }

    companion object {
        private val ALLOWANCE = longPreferencesKey("allowance_minor")
        private val THEME = stringPreferencesKey("theme_mode")
        private val LOCAL_AI = booleanPreferencesKey("local_ai_enabled")

        @Volatile private var instance: UiPrefs? = null

        fun get(context: Context): UiPrefs =
            instance ?: synchronized(this) {
                instance ?: UiPrefs(context.applicationContext).also { instance = it }
            }
    }
}
