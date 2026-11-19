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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Records of one day, newest first inside the list. */
data class DayGroup(
    val date: LocalDate,
    val expense: Money,
    val income: Money,
    val items: List<Transaction>,
)

data class RecordsUiState(
    val month: YearMonth = YearMonth.now(),
    val categoryFilter: String? = null,
    val categories: List<Category> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface RecordsEvent {
    /** A record was soft-deleted; the screen offers Undo. */
    data class Deleted(val id: String) : RecordsEvent
}

class RecordsViewModel(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val categoryFilter = MutableStateFlow<String?>(null)
    private val eventChannel = Channel<RecordsEvent>(Channel.BUFFERED)

    val events: Flow<RecordsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RecordsUiState> = combine(month, categoryFilter, categories.observeAll()) { m, f, cats ->
        Triple(m, f, cats)
    }.flatMapLatest { (m, f, cats) ->
        transactions.observe(from = m.atDay(1), to = m.atEndOfMonth(), categoryId = f)
            .map { list -> buildState(m, f, cats, list) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(month = month.value),
    )

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun setCategoryFilter(categoryId: String?) {
        categoryFilter.value = categoryId
    }

    fun save(input: TransactionInput) {
        viewModelScope.launch { transactions.saveInput(input) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            transactions.softDelete(id)
            eventChannel.send(RecordsEvent.Deleted(id))
        }
    }

    fun undo(id: String) {
        viewModelScope.launch { transactions.restore(id) }
    }

    private fun buildState(
        m: YearMonth,
        filter: String?,
        cats: List<Category>,
        list: List<Transaction>,
    ): RecordsUiState {
        val groups = list
            .groupBy { it.occurredOn }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    expense = Money(items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount.minor }),
                    income = Money(items.filter { it.type == TxType.INCOME }.sumOf { it.amount.minor }),
                    items = items,
                )
            }
        return RecordsUiState(
            month = m,
            categoryFilter = filter,
            categories = cats,
            groups = groups,
            loaded = true,
        )
    }
}
