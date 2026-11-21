package com.example.financetracker.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetInput
import com.example.financetracker.domain.model.BudgetPeriod
import com.example.financetracker.domain.model.BudgetStatus
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.ui.common.CardHeader
import com.example.financetracker.ui.common.CategoryIcon
import com.example.financetracker.ui.common.DividedRows
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.categoryIcon
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.common.formatShort
import com.example.financetracker.ui.common.parseAmountToMinor
import com.example.financetracker.ui.common.toneFor
import com.example.financetracker.ui.theme.FinanceTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val OVERALL_LABEL = "All spending"

private fun nameFor(categoryId: String?, categories: Map<String, Category>): String =
    if (categoryId == null) OVERALL_LABEL else categories[categoryId]?.name ?: "Unknown category"

/**
 * Card listing every budget with this period's progress, plus an add button.
 * Tapping a row or "Add" opens [BudgetEditorSheet] (owned by this composable).
 */
@Composable
fun BudgetsCard(
    state: BudgetsUiState,
    onSave: (id: String?, BudgetInput) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Budgets",
) {
    val c = FinanceTheme.colors
    val byId = remember(state.categories) { state.categories.associateBy { it.id } }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }

    FinanceCard(modifier) {
        CardHeader(title) {
            TextButton(onClick = { editing = null; editorOpen = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.statuses.isEmpty()) {
            Text(
                if (state.loaded) "Set a weekly, monthly or yearly limit for a category or for all spending." else "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        } else {
            DividedRows(state.statuses) { status ->
                BudgetStatusRow(status, byId, state.today) { editing = status.budget; editorOpen = true }
            }
        }
    }

    if (editorOpen) {
        BudgetEditorSheet(
            initial = editing,
            categories = state.categories,
            onSave = { input ->
                onSave(editing?.id, input)
                editorOpen = false
            },
            onDelete = editing?.let { b -> { onDelete(b.id); editorOpen = false } },
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun BudgetStatusRow(
    status: BudgetStatus,
    categories: Map<String, Category>,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val c = FinanceTheme.colors
    val budget = status.budget
    val category = budget.categoryId?.let { categories[it] }
    val daysLeft = ChronoUnit.DAYS.between(today, status.periodEnd) + 1
    val barColor = when {
        status.isOver -> c.danger
        status.progress >= 0.85f -> c.accent
        else -> c.cat[toneFor(category)]
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (budget.categoryId == null) {
                OverallIcon()
            } else {
                CategoryIcon(category)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nameFor(budget.categoryId, categories),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(budget.period.adjective)
                        append(" · ")
                        append(if (daysLeft == 1L) "resets tomorrow" else "resets in $daysLeft days")
                        if (budget.rollover && status.carry.minor != 0L) {
                            append(" · ")
                            append(if (status.carry.minor > 0) "+" else "−")
                            append(Money(kotlin.math.abs(status.carry.minor)).formatInr())
                            append(" carried")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (status.isOver) "${Money(-status.left.minor).formatInr()} over" else "${status.left.formatInr()} left",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (status.isOver) c.danger else c.ink,
                )
                Text(
                    "of ${status.limit.formatInr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
            }
        }
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = barColor,
            trackColor = c.surface2,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun OverallIcon() {
    val c = FinanceTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.primarySoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = c.onPrimarySoft, modifier = Modifier.size(22.dp))
    }
}

/** Create/edit sheet: target (category or all spending), amount, period, carry-over switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorSheet(
    initial: Budget?,
    categories: List<Category>,
    onSave: (BudgetInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = FinanceTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var amountText by remember {
        mutableStateOf(initial?.let { BigDecimal.valueOf(it.amount.minor, 2).stripTrailingZeros().toPlainString() } ?: "")
    }
    var period by remember { mutableStateOf(initial?.period ?: BudgetPeriod.MONTHLY) }
    var rollover by remember { mutableStateOf(initial?.rollover ?: false) }
    var menuOpen by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val amountMinor = parseAmountToMinor(amountText)
    val byId = categories.associateBy { it.id }
    val historyResets = initial != null && (period != initial.period || categoryId != initial.categoryId) && initial.rollover

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "New budget" else "Edit budget", style = MaterialTheme.typography.titleLarge)

            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = nameFor(categoryId, byId),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Applies to") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(OVERALL_LABEL) },
                        leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
                        onClick = { categoryId = null; menuOpen = false },
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            leadingIcon = { Icon(categoryIcon(cat.icon), contentDescription = null) },
                            onClick = { categoryId = cat.id; menuOpen = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text("Limit per ${period.label.lowercase()} (₹)") },
                singleLine = true,
                isError = submitted && amountMinor == null,
                supportingText = { if (submitted && amountMinor == null) Text("Enter an amount greater than zero") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BudgetPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { period = p },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = BudgetPeriod.entries.size),
                        label = { Text(p.adjective) },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { rollover = !rollover }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Carry over", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Unspent money adds to the next ${period.label.lowercase()}; overspending takes away from it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                }
                Switch(
                    checked = rollover,
                    onCheckedChange = { rollover = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.primary,
                        checkedThumbColor = c.onPrimary,
                        uncheckedTrackColor = c.surface2,
                        uncheckedBorderColor = c.outline,
                        uncheckedThumbColor = c.outline,
                    ),
                )
            }
            if (historyResets) {
                Text(
                    "Changing what it applies to or how often it resets starts the carry-over fresh from this ${period.label.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
            }
            initial?.let {
                Text(
                    "Tracking since ${it.startOn.formatShort()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    submitted = true
                    val minor = amountMinor
                    if (minor != null) onSave(BudgetInput(categoryId, Money(minor), period, rollover))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (initial == null) "Add budget" else "Save") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete budget", color = c.danger)
                }
            }
        }
    }
}
