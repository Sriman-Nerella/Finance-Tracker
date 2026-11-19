package com.example.financetracker.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.ui.common.TransactionEditorSheet
import com.example.financetracker.ui.common.TransactionRow
import com.example.financetracker.ui.common.categoryIcon
import com.example.financetracker.ui.common.dayHeaderLabel
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.common.monthLabel
import java.time.LocalDate

/**
 * Records for one month: month selector, category filter, day-grouped list.
 * Tap a row to edit; swipe left to delete with Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: RecordsViewModel = viewModel {
        RecordsViewModel(container.transactions, container.categories)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RecordsEvent.Deleted -> {
                    val result = snackbar.showSnackbar(
                        message = "Record deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undo(event.id)
                }
            }
        }
    }

    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MonthSelector(
                label = state.month.monthLabel(),
                onPrevious = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) },
            )

            CategoryFilterRow(
                categories = state.categories,
                selectedId = state.categoryFilter,
                onSelect = { vm.setCategoryFilter(it) },
            )

            if (state.loaded && state.groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No records in ${state.month.monthLabel()}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "day-${group.date}") {
                            DayHeader(
                                date = group.date,
                                expense = group.expense,
                                income = group.income,
                            )
                        }
                        items(group.items, key = { it.id }) { tx ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.Settled) {
                                        false
                                    } else {
                                        vm.delete(tx.id)
                                        true
                                    }
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { DeleteBackground() },
                            ) {
                                TransactionRow(
                                    tx = tx,
                                    category = categoryById[tx.categoryId],
                                    onClick = {
                                        editing = tx
                                        editorOpen = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        TransactionEditorSheet(
            initial = editing,
            categories = state.categories,
            onSave = { input ->
                vm.save(input)
                editorOpen = false
                editing = null
            },
            onDismiss = {
                editorOpen = false
                editing = null
            },
        )
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(if (selectedId == category.id) null else category.id) },
                label = { Text(category.name) },
                leadingIcon = { Icon(categoryIcon(category.icon), contentDescription = null) },
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, expense: Money, income: Money) {
    val totals = buildList {
        if (expense.minor > 0L) add("Spent " + expense.formatInr())
        if (income.minor > 0L) add("In " + income.formatInr())
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.dayHeaderLabel(),
            style = MaterialTheme.typography.titleSmall,
        )
        if (totals.isNotEmpty()) {
            Text(
                text = totals,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
