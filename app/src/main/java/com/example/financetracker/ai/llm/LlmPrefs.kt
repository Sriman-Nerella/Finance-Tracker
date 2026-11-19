package com.example.financetracker.ai.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.llmPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "llm_prefs")

internal val GEMMA_ENABLED_KEY = booleanPreferencesKey("gemma_enabled")
