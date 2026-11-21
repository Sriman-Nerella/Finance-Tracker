package com.example.financetracker.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.domain.model.BudgetInput
import com.example.financetracker.domain.model.BudgetStatus
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.BudgetRepository
import com.example.financetracker.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BudgetsUiState(
    val today: LocalDate = LocalDate.now(),
    val statuses: List<BudgetStatus> = emptyList(),
    /** Expense categories a budget can target. */
    val categories: List<Category> = emptyList(),
    val loaded: Boolean = false,
)

/** Recurring budgets with their progress for the current period. Shared by Home and Settings. */
class BudgetsViewModel(
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    val state: StateFlow<BudgetsUiState> = combine(
        budgets.observeStatuses(today),
        categories.observeAll(),
    ) { statuses, cats ->
        BudgetsUiState(
            today = today,
            statuses = statuses,
            categories = cats.filter { it.type == TxType.EXPENSE },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState(today = today))

    /** Creates a budget when [id] is null, otherwise edits it. */
    fun save(id: String?, input: BudgetInput) {
        viewModelScope.launch {
            if (id == null) budgets.add(input) else budgets.update(id, input)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { budgets.softDelete(id) }
    }
}
