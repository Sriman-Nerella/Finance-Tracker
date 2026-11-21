package com.example.financetracker.ui.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.ui.common.AllowanceDialog
import com.example.financetracker.ui.common.CardHeader
import com.example.financetracker.ui.common.DividedRows
import com.example.financetracker.ui.budget.BudgetsCard
import com.example.financetracker.ui.budget.BudgetsViewModel
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.TransactionEditorSheet
import com.example.financetracker.ui.common.TransactionRow
import com.example.financetracker.ui.common.WidthClass
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.common.toneFor
import com.example.financetracker.ui.theme.FinanceTheme
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Home: greeting, dashboard card, spending by category, recent entries. Phones float the Ask AI button
 * above the bottom bar; tablets have it on the navigation rail and lay the content out in two columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    widthClass: WidthClass,
    onOpenChat: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel {
        HomeViewModel(container.transactions, container.categories, UiPrefs.get(context), container.session)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val budgetsVm: BudgetsViewModel = viewModel { BudgetsViewModel(container.budgets, container.categories) }
    val budgets by budgetsVm.state.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var allowanceOpen by remember { mutableStateOf(false) }
    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }

    val openEntry: (Transaction) -> Unit = {
        editing = it
        editorOpen = true
    }
    val addEntry = {
        editing = null
        editorOpen = true
    }

    val c = FinanceTheme.colors
    Box(modifier.fillMaxSize().background(c.bg)) {
        if (widthClass == WidthClass.EXPANDED) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                HomeHeader(state.firstName, onAdd = addEntry, modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(Modifier.weight(1.1f)) {
                        DashboardCard(
                            state = state,
                            categoryById = categoryById,
                            onSetAllowance = { allowanceOpen = true },
                            onEntryClick = openEntry,
                            expandedByDefault = true,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (budgets.statuses.isNotEmpty()) {
                            BudgetsCard(budgets, onSave = budgetsVm::save, onDelete = budgetsVm::delete)
                        }
                        CategoryBreakdown(state)
                        RecentCard(state, categoryById, onSeeAll, openEntry)
                    }
                }
            }
        } else {
            val medium = widthClass == WidthClass.MEDIUM
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (medium) Modifier.navigationBarsPadding() else Modifier),
                contentPadding = PaddingValues(
                    start = if (medium) 24.dp else 16.dp,
                    end = if (medium) 24.dp else 16.dp,
                    bottom = if (medium) 32.dp else 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val itemMod = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                item(key = "header") { HomeHeader(state.firstName, onAdd = addEntry, modifier = itemMod.padding(horizontal = 0.dp)) }
                item(key = "summary") {
                    DashboardCard(
                        state = state,
                        categoryById = categoryById,
                        onSetAllowance = { allowanceOpen = true },
                        onEntryClick = openEntry,
                        modifier = itemMod,
                    )
                }
                if (budgets.statuses.isNotEmpty()) {
                    item(key = "budgets") {
                        BudgetsCard(budgets, onSave = budgetsVm::save, onDelete = budgetsVm::delete, modifier = itemMod)
                    }
                }
                item(key = "breakdown") { CategoryBreakdown(state, itemMod) }
                item(key = "recent") { RecentCard(state, categoryById, onSeeAll, openEntry, itemMod) }
            }
            if (!medium) {
                ExtendedFloatingActionButton(
                    onClick = onOpenChat,
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    text = { Text("Ask AI", style = MaterialTheme.typography.labelLarge) },
                    containerColor = c.primary,
                    contentColor = c.onPrimary,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
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
    if (allowanceOpen) {
        AllowanceDialog(
            current = state.allowance,
            onDismiss = { allowanceOpen = false },
            onSave = {
                vm.setAllowance(it)
                allowanceOpen = false
            },
        )
    }
}

@Composable
private fun HomeHeader(firstName: String?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            if (firstName != null) {
                Text(firstName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = c.onPrimarySoft)
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = c.onPrimarySoft)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(greeting, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            Text(firstName ?: "Your month", style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalIconButton(
            onClick = onAdd,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surface2, contentColor = c.ink),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add entry")
        }
    }
}

/** One stacked bar plus a legend, grouped by category colour so every slice has its own hue. */
@Composable
private fun CategoryBreakdown(state: HomeUiState, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    data class Slice(val tone: Int, val label: String, val minor: Long)

    val slices = state.byCategory
        .groupBy { toneFor(it.category) }
        .map { (tone, totals) ->
            Slice(tone, totals.sortedByDescending { it.total.minor }.joinToString(" & ") { it.category.name }, totals.sumOf { it.total.minor })
        }
        .filter { it.minor > 0 }
        .sortedByDescending { it.minor }
    val total = slices.sumOf { it.minor }.coerceAtLeast(1L)

    FinanceCard(modifier) {
        CardHeader("Spending by category") {
            Text(
                state.month.month.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (slices.isEmpty()) {
                Text("Nothing spent yet this month.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                return@Column
            }
            Row(
                Modifier
                    .padding(top = 4.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                slices.forEach { s ->
                    Box(
                        Modifier
                            .weight(s.minor.toFloat())
                            .fillMaxHeight()
                            .background(c.cat[s.tone]),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(c.cat[s.tone]),
                        )
                        Text(
                            s.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                        Text(com.example.financetracker.domain.model.Money(s.minor).formatInr(), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(s.minor * 100f / total).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkMuted,
                            modifier = Modifier
                                .widthIn(min = 40.dp)
                                .padding(start = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    state: HomeUiState,
    categoryById: Map<String, Category>,
    onSeeAll: () -> Unit,
    onEntryClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
    FinanceCard(modifier) {
        CardHeader("Recent") {
            TextButton(onClick = onSeeAll) { Text("See all", style = MaterialTheme.typography.labelLarge) }
        }
        if (state.recent.isEmpty()) {
            Text(
                "No entries yet. Tap + or Ask AI to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        } else {
            DividedRows(state.recent) { tx ->
                TransactionRow(tx = tx, category = categoryById[tx.categoryId], onClick = { onEntryClick(tx) }, today = state.today)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
