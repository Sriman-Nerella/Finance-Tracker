package com.example.financetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.CategoryTotal
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.ui.common.TransactionInput
import com.example.financetracker.ui.common.saveInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val spent: Money = Money.ZERO,
    val income: Money = Money.ZERO,
    /** Null when the user hasn't set one. */
    val allowance: Money? = null,
    /** Days left in the month, today included. */
    val daysLeft: Int = 1,
    /** This week's (Mon–Sun) entries, newest first. */
    val week: List<Transaction> = emptyList(),
    val weekSpent: Money = Money.ZERO,
    val byCategory: List<CategoryTotal> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val firstName: String? = null,
) {
    val left: Money? get() = allowance?.let { Money(it.minor - spent.minor) }

    /** What's left divided by the remaining days, rounded down; zero once over budget. */
    val perDay: Money? get() = left?.let { Money(maxOf(0L, it.minor) / daysLeft / 100 * 100) }
}

/** Dashboard for the current calendar month. */
class HomeViewModel(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    private val prefs: UiPrefs,
    session: SessionRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val month = YearMonth.from(today)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val weekEnd = weekStart.plusDays(6)

    val uiState: StateFlow<HomeUiState> = combine(
        transactions.observeSummary(from = month.atDay(1), to = month.atEndOfMonth(), recentLimit = 5),
        transactions.observe(from = weekStart, to = weekEnd),
        categories.observeAll(),
        prefs.allowance,
        session.session,
    ) { summary, week, cats, allowance, s ->
        HomeUiState(
            month = month,
            today = today,
            spent = summary.totalExpense,
            income = summary.totalIncome,
            allowance = allowance,
            daysLeft = (ChronoUnit.DAYS.between(today, month.atEndOfMonth()) + 1).toInt(),
            week = week,
            weekSpent = Money(week.filter { it.type == TxType.EXPENSE }.sumOf { it.amount.minor }),
            byCategory = summary.byCategory,
            recent = summary.recent,
            categories = cats,
            firstName = (s as? SessionState.SignedIn)?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(month = month, today = today),
    )

    fun save(input: TransactionInput) {
        viewModelScope.launch { transactions.saveInput(input) }
    }

    fun setAllowance(value: Money?) {
        viewModelScope.launch { prefs.setAllowance(value) }
    }
}
