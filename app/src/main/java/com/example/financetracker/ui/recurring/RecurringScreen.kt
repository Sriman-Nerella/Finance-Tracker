package com.example.financetracker.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringFrequency
import com.example.financetracker.domain.model.RecurringInput
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.recurring.occurrencesBetween
import com.example.financetracker.ui.common.CardHeader
import com.example.financetracker.ui.common.CategoryIcon
import com.example.financetracker.ui.common.DividedRows
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.ScreenTitleBar
import com.example.financetracker.ui.common.categoryIcon
import com.example.financetracker.ui.common.defaultCategoryFor
import com.example.financetracker.ui.common.formatDate
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.common.parseAmountToMinor
import com.example.financetracker.ui.common.signedInr
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Recurring bills, subscriptions and income (opened from Profile). Entries stay here; each one is
 * added to Transactions only once its due date arrives.
 */
@Composable
fun RecurringScreen(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: RecurringViewModel = viewModel { RecurringViewModel(container.recurring, container.categories) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = state.rows.firstOrNull { it.item.id == editingId }?.item
    val c = FinanceTheme.colors
    val openNew = { editingId = null; editorOpen = true }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTitleBar("Recurring budgets", onBack = onBack, actions = {
                IconButton(onClick = openNew) { Icon(Icons.Rounded.Add, contentDescription = "Add recurring entry") }
            })
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Bills, subscriptions and income that repeat. Each one is added to Transactions on its due date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkMuted,
                )
                if (state.rows.isEmpty()) {
                    EmptyState(loaded = state.loaded, onAdd = openNew)
                } else {
                    Summary(state)
                    FinanceCard {
                        CardHeader("Upcoming")
                        DividedRows(state.rows) { row ->
                            RecurringRow(row) { editingId = row.item.id; editorOpen = true }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        RecurringEditorSheet(
            initial = editing,
            categories = state.categories,
            today = state.today,
            onSave = { input ->
                vm.save(editing?.id, input)
                editorOpen = false
            },
            onDelete = editing?.let { e -> { vm.delete(e.id); editorOpen = false } },
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun EmptyState(loaded: Boolean, onAdd: () -> Unit) {
    val c = FinanceTheme.colors
    FinanceCard {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.EventRepeat, contentDescription = null, tint = c.inkMuted)
            Text(
                if (loaded) "Nothing recurring yet. Add rent, subscriptions like Netflix, EMIs or your salary." else "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkMuted,
                textAlign = TextAlign.Center,
            )
            if (loaded) {
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add recurring entry")
                }
            }
        }
    }
}

@Composable
private fun Summary(state: RecurringUiState) {
    val c = FinanceTheme.colors
    FinanceCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Going out / month", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Text(state.monthlyExpenses.formatInr(), style = FinanceType.bodyStrong)
            }
            Column(Modifier.weight(1f)) {
                Text("Coming in / month", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Text(state.monthlyIncome.formatInr(), style = FinanceType.bodyStrong, color = c.income)
            }
        }
    }
}

@Composable
private fun RecurringRow(row: RecurringRowUi, onClick: () -> Unit) {
    val c = FinanceTheme.colors
    val item = row.item
    val income = item.type == TxType.INCOME
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(row.category)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = FinanceType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.frequency.label} · next ${row.next.formatDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
            )
        }
        Text(item.amount.signedInr(income), style = FinanceType.bodyStrong, color = if (income) c.income else c.ink)
    }
}

/** Create/edit sheet: type, amount, title, category, how often, first date, note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorSheet(
    initial: Recurring?,
    categories: List<Category>,
    today: LocalDate,
    onSave: (RecurringInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = FinanceTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(initial?.let { BigDecimal.valueOf(it.amount.minor, 2).stripTrailingZeros().toPlainString() } ?: "")
    }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: defaultCategoryFor(type, categories)) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: RecurringFrequency.MONTHLY) }
    var startOn by remember { mutableStateOf(initial?.startOn ?: today) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var categoryMenu by remember { mutableStateOf(false) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val amountMinor = parseAmountToMinor(amountText)
    val visibleCategories = categories.filter { it.type == type }
    val category = categories.firstOrNull { it.id == categoryId && it.type == type }
    // New entries with a past start date add those past occurrences straight away.
    val backfill = if (initial == null && startOn < today) frequency.occurrencesBetween(startOn, startOn, today).size else 0

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
            Text(if (initial == null) "New recurring entry" else "Edit recurring entry", style = MaterialTheme.typography.titleLarge)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            if (type != t) {
                                type = t
                                if (categories.none { it.id == categoryId && it.type == t }) categoryId = defaultCategoryFor(t, categories)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = TxType.entries.size),
                        label = { Text(if (t == TxType.EXPENSE) "Expense" else "Income") },
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("Title") },
                placeholder = { Text(if (type == TxType.EXPENSE) "e.g. Netflix, Rent" else "e.g. Salary") },
                singleLine = true,
                isError = submitted && title.isBlank(),
                supportingText = { if (submitted && title.isBlank()) Text("Give it a name") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text("Amount (₹)") },
                singleLine = true,
                isError = submitted && amountMinor == null,
                supportingText = { if (submitted && amountMinor == null) Text("Enter an amount greater than zero") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(expanded = frequencyMenu, onExpandedChange = { frequencyMenu = it }) {
                OutlinedTextField(
                    value = frequency.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeats") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                )
                ExposedDropdownMenu(expanded = frequencyMenu, onDismissRequest = { frequencyMenu = false }) {
                    RecurringFrequency.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { frequency = f; frequencyMenu = false })
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = it }) {
                OutlinedTextField(
                    value = category?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    isError = submitted && category == null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                )
                ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    visibleCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            leadingIcon = { Icon(categoryIcon(cat.icon), contentDescription = null) },
                            onClick = { categoryId = cat.id; categoryMenu = false },
                        )
                    }
                }
            }

            OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (initial == null) "First on ${startOn.formatDate()}" else "Started ${startOn.formatDate()}")
            }
            Text(
                when {
                    backfill > 0 -> "Past dates count too: $backfill ${if (backfill == 1) "entry" else "entries"} from ${startOn.formatDate()} to today will be added to Transactions now."
                    initial == null && startOn == today -> "It's added to Transactions today, then repeats ${frequency.label.lowercase()}."
                    initial == null -> "It shows up in Transactions on ${startOn.formatDate()}, then repeats ${frequency.label.lowercase()}."
                    else -> "Changes apply to future dates. Entries already in Transactions stay as they are."
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    submitted = true
                    val minor = amountMinor
                    val cat = category
                    if (minor != null && cat != null && title.isNotBlank()) {
                        onSave(RecurringInput(title.trim(), Money(minor), type, cat.id, note.trim().ifBlank { null }, frequency, startOn))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (initial == null) "Add" else "Save changes") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = c.danger)
                }
            }
        }
    }

    if (datePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startOn = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    datePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}
