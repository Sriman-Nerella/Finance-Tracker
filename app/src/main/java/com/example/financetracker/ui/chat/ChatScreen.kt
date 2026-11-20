package com.example.financetracker.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoardOff
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.ai.ParsedEntry
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.ui.common.CategoryIcon
import com.example.financetracker.ui.common.categoryIcon
import com.example.financetracker.ui.common.dayHeaderLabel
import com.example.financetracker.ui.common.signedInr
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Ask AI chat. Messages are parsed into entries that the user confirms with Save. The composer's + opens a
 * sheet with the on-device AI switch, or "No local AI model available" when the device has none.
 */
@Composable
fun ChatScreen(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Scoped to the activity so the conversation survives leaving and reopening Ask AI.
    val owner = (context as? ViewModelStoreOwner) ?: checkNotNull(LocalViewModelStoreOwner.current)
    val vm: ChatViewModel = viewModel(viewModelStoreOwner = owner) {
        ChatViewModel(
            container.chatPipeline,
            container.transactions,
            container.categories,
            container.llm,
            UiPrefs.get(context),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = FinanceTheme.colors

    val itemCount = state.messages.size + if (state.isParsing) 1 else 0
    val listState = rememberLazyListState()
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(c.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ChatTopBar(usingLocalAi = state.usingLocalAi, onBack = onBack)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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

            Composer(
                text = state.input,
                canSend = state.input.isNotBlank() && !state.isParsing,
                localModel = state.localModel,
                localAiEnabled = state.localAiEnabled,
                onLocalAiChange = vm::setLocalAiEnabled,
                onTextChange = vm::onInputChange,
                onSend = vm::send,
            )
        }
    }
}

@Composable
private fun ChatTopBar(usingLocalAi: Boolean, onBack: () -> Unit) {
    val c = FinanceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Ask AI", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(if (usingLocalAi) c.primarySoft else c.surface2)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val tint = if (usingLocalAi) c.onPrimarySoft else c.inkMuted
                Icon(
                    if (usingLocalAi) Icons.Rounded.Memory else Icons.AutoMirrored.Rounded.Rule,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (usingLocalAi) "On-device" else "Built-in parser",
                    style = MaterialTheme.typography.bodySmall,
                    color = tint,
                )
            }
        }
    }
}

@Composable
private fun TextBubble(text: String, fromUser: Boolean) {
    val c = FinanceTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = if (fromUser) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
            } else {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
            },
            color = if (fromUser) c.primary else c.surface,
            contentColor = if (fromUser) c.onPrimary else c.ink,
            border = if (fromUser) null else BorderStroke(1.dp, c.line),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val c = FinanceTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.line),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.primary)
                Spacer(Modifier.size(8.dp))
                Text("Reading your entries…", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
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
    val c = FinanceTheme.colors
    val pending = preview.status == PreviewStatus.PENDING
    var editing by remember { mutableStateOf<DraftEntry?>(null) }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(0.92f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = previewSummary(preview.entries.map { it.entry }),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (preview.refinedByLlm) {
                Text("Tidied up on device", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = c.bg,
                border = BorderStroke(1.dp, c.line),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    preview.entries.forEach { draft ->
                        DraftRow(
                            entry = draft.entry,
                            categories = categories,
                            editable = pending,
                            onClick = { editing = draft },
                            onRemove = { onRemove(draft) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when (preview.status) {
                PreviewStatus.PENDING, PreviewStatus.SAVING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSave,
                        enabled = pending && preview.entries.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (pending) "Save" else "Saving…", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(onClick = onDiscard, enabled = pending) {
                        Text("Discard", style = MaterialTheme.typography.labelLarge)
                    }
                }
                PreviewStatus.SAVED -> Text("Saved", style = MaterialTheme.typography.labelLarge, color = c.primary)
                PreviewStatus.DISCARDED -> Text("Discarded", style = MaterialTheme.typography.labelLarge, color = c.inkMuted)
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
    val c = FinanceTheme.colors
    val category = categories.firstOrNull { it.id == entry.categoryId }
    val income = entry.type == TxType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
            .padding(start = 12.dp, end = if (editable) 0.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.title, style = FinanceType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(
                category?.name ?: "Other",
                entry.occurredOn.dayHeaderLabel(),
                entry.note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(entry.amount.signedInr(income), style = FinanceType.bodyStrong, color = if (income) c.income else c.ink)
        if (editable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove entry", tint = c.inkMuted)
            }
        }
    }
}

/** "+", the message field and Send in one pill; + opens the on-device AI sheet above it. */
@Composable
private fun Composer(
    text: String,
    canSend: Boolean,
    localModel: LocalModel,
    localAiEnabled: Boolean,
    onLocalAiChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val c = FinanceTheme.colors
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (sheetOpen) 45f else 0f, label = "plus")

    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        AnimatedVisibility(
            visible = sheetOpen,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(22.dp),
                color = c.surface,
                contentColor = c.ink,
                border = BorderStroke(1.dp, c.line),
                shadowElevation = 4.dp,
            ) {
                LocalAiSheet(localModel, localAiEnabled, onLocalAiChange)
            }
        }
        Surface(
            shape = CircleShape,
            color = c.surface,
            border = BorderStroke(1.dp, c.line),
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { sheetOpen = !sheetOpen },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (sheetOpen) c.surface2 else c.surface),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = if (sheetOpen) "Close options" else "More options",
                        modifier = Modifier.rotate(rotation),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                ) {
                    if (text.isEmpty()) {
                        Text("Enter message", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                        cursorBrush = SolidColor(c.primary),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) c.primary else c.primary.copy(alpha = 0.38f)),
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", tint = c.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun LocalAiSheet(localModel: LocalModel, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = FinanceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (localModel is LocalModel.Available) Modifier.clickable { onChange(!enabled) } else Modifier,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val available = localModel is LocalModel.Available
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (available && enabled) c.primarySoft else c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (localModel == LocalModel.Unavailable) Icons.Rounded.DeveloperBoardOff else Icons.Rounded.Memory,
                contentDescription = null,
                tint = if (available && enabled) c.onPrimarySoft else c.inkMuted,
            )
        }
        Column(Modifier.weight(1f)) {
            when (localModel) {
                LocalModel.Checking -> {
                    Text("On-device AI", style = FinanceType.bodyStrong)
                    Text("Checking this device…", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
                is LocalModel.Available -> {
                    Text("On-device AI", style = FinanceType.bodyStrong)
                    Text(
                        "${localModel.name} · chats stay on this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                }
                LocalModel.Unavailable -> {
                    Text("No local AI model available", style = FinanceType.bodyStrong)
                    Text(
                        "This device can't run an on-device model. Entries are read by the built-in parser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                }
            }
        }
        if (localModel is LocalModel.Available) {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.primary,
                    checkedThumbColor = c.onPrimary,
                    uncheckedTrackColor = c.surface2,
                    uncheckedBorderColor = c.outline,
                    uncheckedThumbColor = c.outline,
                ),
            )
        } else if (localModel == LocalModel.Checking) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.primary)
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
