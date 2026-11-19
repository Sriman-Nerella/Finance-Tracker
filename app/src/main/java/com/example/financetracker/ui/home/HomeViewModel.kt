package com.example.financetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.CategoryTotal
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.ui.common.TransactionInput
import com.example.financetracker.ui.common.monthLabel
import com.example.financetracker.ui.common.saveInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val monthLabel: String = "",
    val totalExpense: Money = Money.ZERO,
    val totalIncome: Money = Money.ZERO,
    val net: Money = Money.ZERO,
    val byCategory: List<CategoryTotal> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/** Dashboard for the current calendar month. */
class HomeViewModel(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val month = YearMonth.from(today)
    private val monthName = month.monthLabel()

    val uiState: StateFlow<HomeUiState> = combine(
        transactions.observeSummary(from = month.atDay(1), to = month.atEndOfMonth()),
        categories.observeAll(),
    ) { summary, cats ->
        HomeUiState(
            monthLabel = monthName,
            totalExpense = summary.totalExpense,
            totalIncome = summary.totalIncome,
            net = Money(summary.totalIncome.minor - summary.totalExpense.minor),
            byCategory = summary.byCategory,
            recent = summary.recent,
            categories = cats,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(monthLabel = monthName),
    )

    fun save(input: TransactionInput) {
        viewModelScope.launch { transactions.saveInput(input) }
    }
}
