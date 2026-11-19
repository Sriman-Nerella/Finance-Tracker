package com.example.financetracker.domain.session

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface SessionState {
    /** First launch: user hasn't chosen guest or sign-in yet -> show Intro. */
    data object Undecided : SessionState
    /** Fully offline, no backup. */
    data object Guest : SessionState
    data class SignedIn(val userId: String, val email: String?, val displayName: String?) : SessionState
}

sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val at: Instant) : SyncStatus
    data class Failed(val message: String, val at: Instant) : SyncStatus
}

interface SessionRepository {
    val session: Flow<SessionState>
    suspend fun continueAsGuest()

    /** Google via Credential Manager -> Supabase IDToken sign-in. Needs an Activity context for the picker. */
    suspend fun signInWithGoogle(activity: Activity): Result<SessionState.SignedIn>

    /** Keeps local data unless [wipeLocal]. */
    suspend fun signOut(wipeLocal: Boolean = false)
}

interface SyncController {
    val status: Flow<SyncStatus>
    /** Enqueue a one-off sync now (no-op unless signed in). */
    fun syncNow()
    /** Debounced sync request after local writes. */
    fun requestSync()
}
