package com.example.financetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.ui.common.CategoryBarRow
import com.example.financetracker.ui.common.SectionTitle
import com.example.financetracker.ui.common.TransactionEditorSheet
import com.example.financetracker.ui.common.TransactionRow
import com.example.financetracker.ui.common.formatInr

/** Current-month dashboard. [onOpenChat] switches to the Chat tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = viewModel {
        HomeViewModel(container.transactions, container.categories)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }
    val maxCategoryMinor = state.byCategory.maxOfOrNull { it.total.minor } ?: 0L

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                editorOpen = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add record")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("This month", style = MaterialTheme.typography.labelLarge)
                        Text(state.monthLabel, style = MaterialTheme.typography.headlineSmall)
                    }
                    FilledTonalButton(onClick = onOpenChat) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ask AI")
                    }
                }
            }

            item(key = "summary") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        SummaryStat(label = "Spent", value = state.totalExpense)
                        SummaryStat(label = "Income", value = state.totalIncome)
                        SummaryStat(label = "Net", value = state.net, signed = true)
                    }
                }
            }

            item(key = "cat-title") { SectionTitle("Spending by category") }

            if (state.byCategory.isEmpty()) {
                item(key = "cat-empty") {
                    Text(
                        "Nothing spent yet this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.byCategory, key = { "cat-${it.category.id}" }) { total ->
                    val fraction = if (maxCategoryMinor > 0L) {
                        total.total.minor.toFloat() / maxCategoryMinor.toFloat()
                    } else {
                        0f
                    }
                    CategoryBarRow(total = total, fraction = fraction)
                }
            }

            item(key = "recent-title") { SectionTitle("Recent") }

            if (state.recent.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        "No records yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.recent, key = { "recent-${it.id}" }) { tx ->
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
private fun SummaryStat(label: String, value: Money, signed: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (signed && value.minor > 0L) "+" + value.formatInr() else value.formatInr(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
