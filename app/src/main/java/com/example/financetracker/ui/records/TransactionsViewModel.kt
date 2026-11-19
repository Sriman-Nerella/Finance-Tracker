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
    val loaded: Boolean = false,
)

sealed interface RecordsEvent {
    /** A record was soft-deleted; the screen offers Undo. */
    data class Deleted(val id: String) : RecordsEvent
}

/** Every entry, separated by month (newest month first). */
class TransactionsViewModel(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TypeFilter.ALL)
    private val selectedId = MutableStateFlow<String?>(null)
    private val eventChannel = Channel<RecordsEvent>(Channel.BUFFERED)

    val events: Flow<RecordsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions.observe(),
        categories.observeAll(),
        filter,
        selectedId,
    ) { list, cats, f, sel ->
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
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )

    fun setFilter(value: TypeFilter) {
        filter.value = value
    }

    fun select(id: String?) {
        selectedId.value = id
    }

    fun save(input: TransactionInput) {
        viewModelScope.launch { transactions.saveInput(input) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            transactions.softDelete(id)
            if (selectedId.value == id) selectedId.value = null
            eventChannel.send(RecordsEvent.Deleted(id))
        }
    }

    fun undo(id: String) {
        viewModelScope.launch { transactions.restore(id) }
    }
}
