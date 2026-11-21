package com.example.financetracker.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.ui.common.TransactionInput
import com.example.financetracker.ui.common.saveInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** All entries of one month, newest first. */
data class MonthGroup(
    val month: YearMonth,
    val expense: Money,
    val income: Money,
    val items: List<Transaction>,
)

enum class TypeFilter { ALL, EXPENSES, INCOME }

data class TransactionsUiState(
    val filter: TypeFilter = TypeFilter.ALL,
    val categories: List<Category> = emptyList(),
    val months: List<MonthGroup> = emptyList(),
    /** Selected entry for the tablet detail pane. */
    val selected: Transaction? = null,
    /** Entries ticked in multi-select mode; empty = not selecting. */
    val checkedIds: Set<String> = emptySet(),
    /** Every entry currently shown (after the filter), for "select all". */
    val visibleIds: List<String> = emptyList(),
    val loaded: Boolean = false,
) {
    val selecting: Boolean get() = checkedIds.isNotEmpty()
    val allChecked: Boolean get() = visibleIds.isNotEmpty() && checkedIds.containsAll(visibleIds)
}

sealed interface RecordsEvent {
    /** Records were soft-deleted; the screen offers Undo. */
    data class Deleted(val ids: List<String>) : RecordsEvent
}

/** Every entry, separated by month (newest month first). */
class TransactionsViewModel(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TypeFilter.ALL)
    private val selectedId = MutableStateFlow<String?>(null)
    private val checked = MutableStateFlow<Set<String>>(emptySet())
    private val eventChannel = Channel<RecordsEvent>(Channel.BUFFERED)

    val events: Flow<RecordsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions.observe(),
        categories.observeAll(),
        filter,
        selectedId,
        checked,
    ) { list, cats, f, sel, chk ->
        val visible = when (f) {
            TypeFilter.ALL -> list
            TypeFilter.EXPENSES -> list.filter { it.type == TxType.EXPENSE }
            TypeFilter.INCOME -> list.filter { it.type == TxType.INCOME }
        }
        val months = visible
            .groupBy { YearMonth.from(it.occurredOn) }
            .entries
            .sortedByDescending { it.key }
            .map { (month, items) ->
                MonthGroup(
                    month = month,
                    expense = Money(items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount.minor }),
                    income = Money(items.filter { it.type == TxType.INCOME }.sumOf { it.amount.minor }),
                    items = items.sortedWith(compareByDescending<Transaction> { it.occurredOn }.thenByDescending { it.createdAt }),
                )
            }
        TransactionsUiState(
            filter = f,
            categories = cats,
            months = months,
            selected = list.firstOrNull { it.id == sel },
            // Drop ticks for entries that are no longer shown (deleted elsewhere, synced away).
            checkedIds = chk.intersect(visible.map { it.id }.toSet()),
            visibleIds = visible.map { it.id },
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )

    fun setFilter(value: TypeFilter) {
        filter.value = value
        checked.value = emptySet()
    }

    /** Long press: enter multi-select with [id] ticked. */
    fun startSelection(id: String) {
        checked.value = setOf(id)
    }

    /** Tap while selecting. Unticking the last entry leaves multi-select. */
    fun toggleChecked(id: String) {
        checked.value = checked.value.let { if (id in it) it - id else it + id }
    }

    /** Ticks every shown entry, or clears the selection when all are already ticked. */
    fun toggleSelectAll() {
        val state = uiState.value
        checked.value = if (state.allChecked) emptySet() else state.visibleIds.toSet()
    }

    fun clearSelection() {
        checked.value = emptySet()
    }

    fun deleteChecked() {
        val ids = uiState.value.checkedIds.toList()
        if (ids.isEmpty()) return
        checked.value = emptySet()
        deleteAll(ids)
    }

    fun select(id: String?) {
        selectedId.value = id
    }

    fun save(input: TransactionInput) {
        viewModelScope.launch { transactions.saveInput(input) }
    }

    fun delete(id: String) = deleteAll(listOf(id))

    private fun deleteAll(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { transactions.softDelete(it) }
            if (selectedId.value in ids) selectedId.value = null
            eventChannel.send(RecordsEvent.Deleted(ids))
        }
    }

    fun undo(ids: List<String>) {
        viewModelScope.launch { ids.forEach { transactions.restore(it) } }
    }
}
