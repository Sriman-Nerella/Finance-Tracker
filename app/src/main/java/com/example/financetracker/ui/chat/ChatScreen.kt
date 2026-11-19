package com.example.financetracker.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.ai.ParsedEntry
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.ui.common.categoryIcon
import com.example.financetracker.ui.common.formatInr
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun ChatScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: ChatViewModel = viewModel {
        ChatViewModel(container.chatPipeline, container.transactions, container.categories)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    val itemCount = state.messages.size + if (state.isParsing) 1 else 0
    val listState = rememberLazyListState()
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                when (message) {
                    is ChatMessage.User -> TextBubble(text = message.text, fromUser = true)
                    is ChatMessage.Assistant -> TextBubble(text = message.text, fromUser = false)
                    is ChatMessage.Preview -> PreviewCard(
                        preview = message,
                        categories = state.categories,
                        onEdit = { draft, updated -> vm.updateDraft(message.id, draft.key, updated) },
                        onRemove = { draft -> vm.removeDraft(message.id, draft.key) },
                        onSave = { vm.save(message.id) },
                        onDiscard = { vm.discard(message.id) },
                    )
                }
            }
            if (state.isParsing) {
                item(key = "typing") { TypingIndicator() }
            }
        }

        InputBar(
            text = state.input,
            canSend = state.input.isNotBlank() && !state.isParsing,
            onTextChange = vm::onInputChange,
            onSend = vm::send,
        )
    }
}

@Composable
private fun TextBubble(text: String, fromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Reading your entries…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: ChatMessage.Preview,
    categories: List<Category>,
    onEdit: (DraftEntry, ParsedEntry) -> Unit,
    onRemove: (DraftEntry) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val pending = preview.status == PreviewStatus.PENDING
    var editing by remember { mutableStateOf<DraftEntry?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = previewSummary(preview.entries.map { it.entry }),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (preview.refinedByLlm) {
                Text(
                    text = "✨ AI refined",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))

            preview.entries.forEach { draft ->
                DraftRow(
                    entry = draft.entry,
                    categories = categories,
                    editable = pending,
                    onClick = { editing = draft },
                    onRemove = { onRemove(draft) },
                )
            }

            Spacer(Modifier.height(8.dp))
            when (preview.status) {
                PreviewStatus.PENDING, PreviewStatus.SAVING -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        enabled = pending,
                    ) { Text("Discard") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = pending && preview.entries.isNotEmpty(),
                    ) { Text(if (pending) "Save" else "Saving…") }
                }
                PreviewStatus.SAVED -> Text(
                    text = "Saved",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                PreviewStatus.DISCARDED -> Text(
                    text = "Discarded",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { draft ->
        EditEntryDialog(
            entry = draft.entry,
            categories = categories,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                onEdit(draft, updated)
                editing = null
            },
        )
    }
}

@Composable
private fun DraftRow(
    entry: ParsedEntry,
    categories: List<Category>,
    editable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val category = categories.firstOrNull { it.id == entry.categoryId }
    val categoryName = category?.name ?: "Other"
    val sign = if (entry.type == TxType.INCOME) "+" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = categoryIcon(category?.icon ?: "more_horiz"),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                entry.note?.takeIf { it.isNotBlank() },
                categoryName,
                entry.occurredOn.format(ROW_DATE_FORMAT),
            ).joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$sign${entry.amount.formatInr()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (editable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove record")
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: ParsedEntry,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (ParsedEntry) -> Unit,
) {
    var title by remember { mutableStateOf(entry.title) }
    var note by remember { mutableStateOf(entry.note.orEmpty()) }
    var amountText by remember { mutableStateOf(entry.amount.toEditableString()) }
    var dateText by remember { mutableStateOf(entry.occurredOn.toString()) }
    var type by remember { mutableStateOf(entry.type) }
    var categoryId by remember { mutableStateOf(entry.categoryId) }

    val amount = parseAmount(amountText)
    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val options = categories.filter { it.type == type }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit record") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date (yyyy-mm-dd)") },
                    singleLine = true,
                    isError = date == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(TxType.EXPENSE, TxType.INCOME).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                if (type != t) {
                                    type = t
                                    val newOptions = categories.filter { it.type == t }
                                    if (newOptions.none { it.id == categoryId }) {
                                        categoryId = newOptions.firstOrNull()?.id ?: categoryId
                                    }
                                }
                            },
                            label = { Text(if (t == TxType.EXPENSE) "Expense" else "Income") },
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = cat.id },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(categoryIcon(cat.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amount != null && date != null,
                onClick = {
                    if (amount != null && date != null) {
                        onConfirm(
                            entry.copy(
                                title = title.trim(),
                                note = note.trim().ifEmpty { null },
                                amount = amount,
                                type = type,
                                categoryId = categoryId,
                                occurredOn = date,
                            ),
                        )
                    }
                },
            ) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InputBar(
    text: String,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("nov 19\ntea 15rs, lunch 200rs") },
            maxLines = 5,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        FilledIconButton(onClick = onSend, enabled = canSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

/** Editable representation of an amount in major units, e.g. 15.5 -> "15.5", 200 -> "200". */
private fun Money.toEditableString(): String =
    BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

private fun parseAmount(text: String): Money? {
    val value = text.trim().toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return runCatching {
        Money(value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())
    }.getOrNull()
}
