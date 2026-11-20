package com.example.financetracker.ui.profile

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncController
import com.example.financetracker.domain.session.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class ProfileUiState(
    val session: SessionState = SessionState.Guest,
    val sync: SyncStatus = SyncStatus.Disabled,
    val totalEntries: Int = 0,
    val monthEntries: Int = 0,
    /** Consecutive days with at least one entry, ending today (or yesterday). */
    val streak: Int = 0,
    val categoryCount: Int = 0,
    val allowance: Money? = null,
    val signingIn: Boolean = false,
    val message: String? = null,
)

private data class Transient(val signingIn: Boolean = false, val message: String? = null)

class ProfileViewModel(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val session: SessionRepository,
    private val sync: SyncController,
    private val prefs: UiPrefs,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    private val stats = combine(transactions.observe(), categories.observeAll()) { list, cats ->
        val month = YearMonth.from(today)
        val days = list.map { it.occurredOn }.toSet()
        var streak = 0
        var day = if (today in days) today else today.minusDays(1)
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        Triple(list.size, list.count { YearMonth.from(it.occurredOn) == month }, streak) to cats.size
    }

    val state: StateFlow<ProfileUiState> = combine(
        session.session,
        sync.status,
        stats,
        prefs.allowance,
        transient,
    ) { s, syncStatus, (counts, catCount), allowance, t ->
        ProfileUiState(
            session = s,
            sync = syncStatus,
            totalEntries = counts.first,
            monthEntries = counts.second,
            streak = counts.third,
            categoryCount = catCount,
            allowance = allowance,
            signingIn = t.signingIn,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun signInWithGoogle(activity: Activity) {
        if (transient.value.signingIn) return
        transient.update { Transient(signingIn = true) }
        viewModelScope.launch {
            val result = session.signInWithGoogle(activity)
            transient.update {
                Transient(message = result.exceptionOrNull()?.let { e -> e.message ?: "Sign-in failed" })
            }
        }
    }

    fun signOut(wipeLocal: Boolean) {
        viewModelScope.launch {
            try {
                session.signOut(wipeLocal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update { it.copy(message = "Sign-out failed: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun syncNow() = sync.syncNow()

    fun setAllowance(value: Money?) {
        viewModelScope.launch { prefs.setAllowance(value) }
    }
}
