package com.example.financetracker.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.ui.common.CategoryIcon
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.ScreenTitleBar
import com.example.financetracker.ui.common.TransactionEditorSheet
import com.example.financetracker.ui.common.TransactionRow
import com.example.financetracker.ui.common.WidthClass
import com.example.financetracker.ui.common.formatLong
import com.example.financetracker.ui.common.monthHeader
import com.example.financetracker.ui.common.signedInr
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType

/**
 * Every entry, separated by month under "NOV 2026 ——— totals" headers that stick while their month scrolls.
 * Phones open the editor on tap; tablets show the tapped entry in a detail pane. Swipe left to delete, with Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(container: AppContainer, widthClass: WidthClass, modifier: Modifier = Modifier) {
    val vm: TransactionsViewModel = viewModel {
        TransactionsViewModel(container.transactions, container.categories)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val twoPane = widthClass == WidthClass.EXPANDED

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RecordsEvent.Deleted -> {
                    val result = snackbar.showSnackbar(
                        message = "Entry deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undo(event.id)
                }
            }
        }
    }

    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }
    val c = FinanceTheme.colors
    val onRowClick: (Transaction) -> Unit = { tx ->
        if (twoPane) {
            vm.select(tx.id)
        } else {
            editing = tx
            editorOpen = true
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (widthClass != WidthClass.COMPACT) Modifier.navigationBarsPadding() else Modifier),
        ) {
            Column(if (twoPane) Modifier.width(440.dp).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) {
                ScreenTitleBar("Transactions") {
                    IconButton(onClick = {
                        editing = null
                        editorOpen = true
                    }) { Icon(Icons.Rounded.Add, contentDescription = "Add entry") }
                }
                FilterRow(selected = state.filter, onSelect = vm::setFilter)
                MonthList(
                    state = state,
                    categoryById = categoryById,
                    selectedId = if (twoPane) state.selected?.id else null,
                    onRowClick = onRowClick,
                    onDelete = vm::delete,
                    modifier = Modifier.weight(1f),
                )
            }
            if (twoPane) {
                VerticalDivider(color = c.line)
                DetailPane(
                    tx = state.selected,
                    category = state.selected?.let { categoryById[it.categoryId] },
                    onEdit = {
                        editing = state.selected
                        editorOpen = true
                    },
                    onDelete = { state.selected?.let { vm.delete(it.id) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
private fun FilterRow(selected: TypeFilter, onSelect: (TypeFilter) -> Unit) {
    val c = FinanceTheme.colors
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(TypeFilter.entries.toList(), key = { it.name }) { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = {
                    Text(
                        when (f) {
                            TypeFilter.ALL -> "All"
                            TypeFilter.EXPENSES -> "Expenses"
                            TypeFilter.INCOME -> "Income"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.primarySoft,
                    selectedLabelColor = c.onPrimarySoft,
                    labelColor = c.ink,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == f,
                    borderColor = c.outline,
                    selectedBorderColor = c.primarySoft,
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MonthList(
    state: TransactionsUiState,
    categoryById: Map<String, Category>,
    selectedId: String?,
    onRowClick: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
    if (state.loaded && state.months.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (state.filter == TypeFilter.ALL) "No entries yet. Tap + or use Ask AI to add one." else "Nothing here for this filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.inkMuted,
            )
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        state.months.forEach { group ->
            stickyHeader(key = "h-${group.month}") {
                MonthHeader(group)
            }
            item(key = "m-${group.month}") {
                FinanceCard(Modifier.padding(horizontal = 16.dp)) {
                    group.items.forEachIndexed { i, tx ->
                        if (i > 0) HorizontalDivider(color = c.lineSoft)
                        androidx.compose.runtime.key(tx.id) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onDelete(tx.id)
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { DeleteBackground() },
                            ) {
                                Box(Modifier.background(c.surface)) {
                                    TransactionRow(
                                        tx = tx,
                                        category = categoryById[tx.categoryId],
                                        onClick = { onRowClick(tx) },
                                        selected = tx.id == selectedId,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "NOV 2026 ———— −₹26,758 · +₹85,000" */
@Composable
private fun MonthHeader(group: MonthGroup) {
    val c = FinanceTheme.colors
    val totals = buildList {
        if (group.expense.minor > 0) add(group.expense.signedInr(income = false))
        if (group.income.minor > 0) add(group.income.signedInr(income = true))
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(group.month.monthHeader(), style = MaterialTheme.typography.labelSmall, color = c.ink)
        HorizontalDivider(color = c.line, modifier = Modifier.weight(1f))
        if (totals.isNotEmpty()) {
            Text(totals, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

@Composable
private fun DeleteBackground() {
    val c = FinanceTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.dangerSoft)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = c.danger)
    }
}

@Composable
private fun DetailPane(
    tx: Transaction?,
    category: Category?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
    if (tx == null) {
        Column(
            modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Select an entry to see its details.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
        }
        return
    }
    val income = tx.type == TxType.INCOME
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryIcon(category, size = 56.dp)
            Column {
                Text(category?.name ?: "Other", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Text(tx.title, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(tx.amount.signedInr(income), style = FinanceType.amountLg, color = if (income) c.income else c.ink)
        FinanceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine("Date", tx.occurredOn.formatLong())
                DetailLine("Category", category?.name ?: "Other")
                DetailLine("Type", if (income) "Income" else "Expense")
                DetailLine(
                    "Added",
                    when (tx.source) {
                        TxSource.MANUAL -> "Manually"
                        TxSource.CHAT -> "From chat"
                        TxSource.AGENT -> "By an assistant"
                        TxSource.RECURRING -> "From a recurring entry"
                    },
                )
                tx.note?.takeIf { it.isNotBlank() }?.let { DetailLine("Note", it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = FinanceTheme.colors
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.inkMuted, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
