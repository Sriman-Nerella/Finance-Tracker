package com.example.financetracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

enum class SessionMode { UNDECIDED, GUEST, SIGNED_IN }

data class StoredSession(
    val mode: SessionMode = SessionMode.UNDECIDED,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    /** Server-timestamp cursors (Instant.toString form) of the last pulled rows. */
    val lastPulledTransactionsAt: String? = null,
    val lastPulledCategoriesAt: String? = null,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
    val lastSyncErrorAt: Long? = null,
)

class SessionStore(context: Context) {
    private val ds = context.applicationContext.sessionDataStore

    val data: Flow<StoredSession> = ds.data.map { it.toStored() }

    suspend fun snapshot(): StoredSession = data.first()

    suspend fun setGuest() {
        ds.edit {
            it[MODE] = SessionMode.GUEST.name
            it.remove(USER_ID); it.remove(EMAIL); it.remove(NAME)
            it.remove(PULLED_TX); it.remove(PULLED_CAT)
            it.remove(SYNC_ERR); it.remove(SYNC_ERR_AT)
        }
    }

    /** Resets pull cursors when the account changes so a new user pulls everything. */
    suspend fun setSignedIn(userId: String, email: String?, displayName: String?) {
        ds.edit {
            if (it[USER_ID] != userId) {
                it.remove(PULLED_TX); it.remove(PULLED_CAT); it.remove(SYNC_AT)
            }
            it[MODE] = SessionMode.SIGNED_IN.name
            it[USER_ID] = userId
            if (email != null) it[EMAIL] = email else it.remove(EMAIL)
            if (displayName != null) it[NAME] = displayName else it.remove(NAME)
            it.remove(SYNC_ERR); it.remove(SYNC_ERR_AT)
        }
    }

    suspend fun setCursors(transactions: String?, categories: String?) {
        ds.edit {
            if (transactions != null) it[PULLED_TX] = transactions
            if (categories != null) it[PULLED_CAT] = categories
        }
    }

    suspend fun recordSyncSuccess(at: Long) {
        ds.edit {
            it[SYNC_AT] = at
            it.remove(SYNC_ERR); it.remove(SYNC_ERR_AT)
        }
    }

    suspend fun recordSyncFailure(message: String, at: Long) {
        ds.edit {
            it[SYNC_ERR] = message
            it[SYNC_ERR_AT] = at
        }
    }

    private fun Preferences.toStored() = StoredSession(
        mode = this[MODE]?.let { m -> SessionMode.entries.firstOrNull { it.name == m } } ?: SessionMode.UNDECIDED,
        userId = this[USER_ID], email = this[EMAIL], displayName = this[NAME],
        lastPulledTransactionsAt = this[PULLED_TX], lastPulledCategoriesAt = this[PULLED_CAT],
        lastSyncAt = this[SYNC_AT], lastSyncError = this[SYNC_ERR], lastSyncErrorAt = this[SYNC_ERR_AT],
    )

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NAME = stringPreferencesKey("display_name")
        val PULLED_TX = stringPreferencesKey("last_pulled_tx_at")
        val PULLED_CAT = stringPreferencesKey("last_pulled_cat_at")
        val SYNC_AT = longPreferencesKey("last_sync_at")
        val SYNC_ERR = stringPreferencesKey("last_sync_error")
        val SYNC_ERR_AT = longPreferencesKey("last_sync_error_at")
    }
}
