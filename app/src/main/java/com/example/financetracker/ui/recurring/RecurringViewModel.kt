package com.example.financetracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringFrequency
import com.example.financetracker.domain.model.RecurringInput
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.recurring.nextAfter
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.RecurringRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringRowUi(val item: Recurring, val next: LocalDate, val category: Category?)

data class RecurringUiState(
    val today: LocalDate = LocalDate.now(),
    /** Soonest next occurrence first. */
    val rows: List<RecurringRowUi> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Recurring expenses converted to a per-month figure. */
    val monthlyExpenses: Money = Money.ZERO,
    val monthlyIncome: Money = Money.ZERO,
    val loaded: Boolean = false,
)

/** Rough monthly equivalent of one occurrence, for the summary line. */
internal fun RecurringFrequency.perMonth(amount: Money): Long = when (this) {
    RecurringFrequency.DAILY -> amount.minor * 365 / 12
    RecurringFrequency.WEEKLY -> amount.minor * 52 / 12
    RecurringFrequency.MONTHLY -> amount.minor
    RecurringFrequency.QUARTERLY -> amount.minor / 3
    RecurringFrequency.HALF_YEARLY -> amount.minor / 6
    RecurringFrequency.YEARLY -> amount.minor / 12
}

class RecurringViewModel(
    private val recurring: RecurringRepository,
    categories: CategoryRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    val state: StateFlow<RecurringUiState> = combine(recurring.observeAll(), categories.observeAll()) { items, cats ->
        val byId = cats.associateBy { it.id }
        RecurringUiState(
            today = today,
            rows = items.map { RecurringRowUi(it, it.nextAfter(today), byId[it.categoryId]) }.sortedBy { it.next },
            categories = cats,
            monthlyExpenses = Money(items.filter { it.type == TxType.EXPENSE }.sumOf { it.frequency.perMonth(it.amount) }),
            monthlyIncome = Money(items.filter { it.type == TxType.INCOME }.sumOf { it.frequency.perMonth(it.amount) }),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState(today = today))

    /** Creates when [id] is null, otherwise edits. Due occurrences are added to Transactions right after. */
    fun save(id: String?, input: RecurringInput) {
        viewModelScope.launch { if (id == null) recurring.add(input) else recurring.update(id, input) }
    }

    fun delete(id: String) {
        viewModelScope.launch { recurring.softDelete(id) }
    }
}
