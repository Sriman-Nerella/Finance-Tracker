package com.example.financetracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Bottom sheet to add ([initial] = null) or edit a record. Emits a validated [TransactionInput];
 * saving is done by the caller's ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    initial: Transaction?,
    categories: List<Category>,
    onSave: (TransactionInput) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amountText by remember { mutableStateOf(initial?.let { editableAmount(it.amount) } ?: "") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: defaultCategoryFor(type, categories)) }
    var date by remember { mutableStateOf(initial?.occurredOn ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val visibleCategories = categories.filter { it.type == type }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val amountMinor = parseAmountToMinor(amountText)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initial == null) "New record" else "Edit record",
                style = MaterialTheme.typography.titleLarge,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            if (type != t) {
                                type = t
                                val stillValid = categories.any { it.id == categoryId && it.type == t }
                                if (!stillValid) categoryId = defaultCategoryFor(t, categories)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TxType.entries.size),
                        label = { Text(if (t == TxType.EXPENSE) "Expense" else "Income") },
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text("Amount (₹)") },
                singleLine = true,
                isError = submitted && amountMinor == null,
                supportingText = {
                    if (submitted && amountMinor == null) Text("Enter an amount greater than zero")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text(selectedCategory?.name ?: "What was it for?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = categoryMenuOpen,
                onExpandedChange = { categoryMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    isError = submitted && selectedCategory == null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    visibleCategories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            leadingIcon = { Icon(categoryIcon(c.icon), contentDescription = null) },
                            onClick = {
                                categoryId = c.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(date.formatDate())
            }

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
                    val category = selectedCategory
                    if (minor != null && category != null) {
                        onSave(
                            TransactionInput(
                                id = initial?.id,
                                amount = Money(minor),
                                type = type,
                                title = title.trim().ifBlank { category.name },
                                note = note.trim().ifBlank { null },
                                categoryId = category.id,
                                occurredOn = date,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (initial == null) "Save" else "Save changes")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = it.fromUtcMillis() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Plain decimal text for an amount, e.g. 20000 paise -> "200", 12345 -> "123.45". */
private fun editableAmount(money: Money): String =
    BigDecimal.valueOf(money.minor, 2).stripTrailingZeros().toPlainString()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromUtcMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
