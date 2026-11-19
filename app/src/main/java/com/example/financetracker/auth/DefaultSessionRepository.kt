package com.example.financetracker.auth

import android.app.Activity
import com.example.financetracker.data.db.AppDatabase
import com.example.financetracker.data.prefs.SessionMode
import com.example.financetracker.data.prefs.SessionStore
import com.example.financetracker.data.remote.SupabaseProvider
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncController
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultSessionRepository(
    private val store: SessionStore,
    private val supabase: SupabaseProvider,
    private val google: GoogleSignInHelper,
    private val db: AppDatabase,
    private val sync: SyncController,
) : SessionRepository {

    override val session: Flow<SessionState> = flow {
        coroutineScope {
            launch { reconcile() }
            emitAll(
                store.data.map { s ->
                    when {
                        s.mode == SessionMode.SIGNED_IN && s.userId != null ->
                            SessionState.SignedIn(s.userId, s.email, s.displayName)
                        s.mode == SessionMode.GUEST -> SessionState.Guest
                        else -> SessionState.Undecided
                    }
                }.distinctUntilChanged()
            )
        }
    }

    /** If we think we're signed in but Supabase definitively has no session, drop to guest (data stays local). */
    private suspend fun reconcile() {
        if (!supabase.isConfigured) return
        try {
            if (store.snapshot().mode != SessionMode.SIGNED_IN) return
            val auth = supabase.client.auth
            auth.awaitInitialization()
            if (auth.sessionStatus.value is SessionStatus.NotAuthenticated) store.setGuest()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline / transient: keep current mode
        }
    }

    override suspend fun continueAsGuest() = store.setGuest()

    override suspend fun signInWithGoogle(activity: Activity): Result<SessionState.SignedIn> {
        if (!supabase.isConfigured) return Result.failure(IllegalStateException("Cloud backup isn't configured yet"))
        val identity = google.getIdentity(activity).getOrElse { return Result.failure(it) }
        return try {
            val auth = supabase.client.auth
            auth.signInWith(IDToken) {
                idToken = identity.idToken
                provider = Google
                nonce = identity.rawNonce
            }
            val user = auth.currentSessionOrNull()?.user ?: auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Sign-in succeeded but no user was returned"))
            val state = SessionState.SignedIn(user.id, user.email ?: identity.email, identity.displayName)
            store.setSignedIn(state.userId, state.email, state.displayName)
            withContext(Dispatchers.IO) {
                // Local rows owned by a *different* account were already backed up to that
                // account; never re-own them into this one. Guest rows (ownerId null) are kept.
                val foreign = db.openHelper.readableDatabase.query(
                    "SELECT 1 FROM transactions WHERE ownerId IS NOT NULL AND ownerId != ? " +
                        "UNION SELECT 1 FROM categories WHERE ownerId IS NOT NULL AND ownerId != ? LIMIT 1",
                    arrayOf<Any?>(state.userId, state.userId),
                ).use { it.moveToFirst() }
                if (foreign) db.clearAllTables()
                db.transactionDao().claimAll(state.userId)
                db.categoryDao().claimAll(state.userId)
            }
            sync.syncNow()
            Result.success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't sign in to cloud backup: ${e.message ?: "unknown error"}", e))
        }
    }

    override suspend fun signOut(wipeLocal: Boolean) {
        if (supabase.isConfigured) {
            try {
                supabase.client.auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // offline: local state is cleared below regardless
            }
        }
        // Mode change makes WorkManagerSyncController cancel periodic/pending sync work.
        store.setGuest()
        if (wipeLocal) withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
