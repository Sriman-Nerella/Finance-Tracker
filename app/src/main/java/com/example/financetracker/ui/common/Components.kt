package com.example.financetracker.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Colour slot for a category: 0 = income, 1..5 = category hues (cat-1 … cat-5). */
fun toneFor(category: Category?): Int = when (category?.id) {
    DefaultCategories.FOOD -> 1
    DefaultCategories.GROCERIES -> 2
    DefaultCategories.TRANSPORT, DefaultCategories.HEALTH -> 3
    DefaultCategories.ENTERTAINMENT, DefaultCategories.SHOPPING -> 4
    DefaultCategories.BILLS, DefaultCategories.OTHER_EXPENSE -> 5
    else -> if (category?.type == TxType.INCOME) 0 else 5
}

/** 40dp (or [size]) rounded icon well tinted with the category's hue. */
@Composable
fun CategoryIcon(category: Category?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = FinanceTheme.colors
    val tone = toneFor(category)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(c.catSoft[tone]),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon(category?.icon ?: ""),
            contentDescription = null,
            tint = c.cat[tone],
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** One entry: icon well, title, "Category · Day", signed amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    tx: Transaction,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showDate: Boolean = true,
    today: LocalDate = LocalDate.now(),
    /** Long press handler (starts multi-select on the Transactions screen). */
    onLongClick: (() -> Unit)? = null,
    /** Non-null while multi-selecting: true shows a check in place of the category icon. */
    checked: Boolean? = null,
) {
    val c = FinanceTheme.colors
    val isIncome = tx.type == TxType.INCOME
    val subtitle = buildString {
        append(category?.name ?: "Other")
        if (showDate) append(" · ").append(tx.occurredOn.dayHeaderLabel(today))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected || checked == true) c.primarySoft else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (checked != null) SelectionMark(checked) else CategoryIcon(category)
        Column(Modifier.weight(1f)) {
            Text(
                text = tx.title,
                style = FinanceType.bodyStrong,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) c.onPrimarySoft else c.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tx.source == TxSource.CHAT) {
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "Added from chat",
                        tint = c.inkMuted,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = tx.amount.signedInr(isIncome),
            style = FinanceType.bodyStrong,
            color = if (isIncome) c.income else c.ink,
        )
    }
}

/** 40dp check well shown instead of the category icon while multi-selecting. */
@Composable
private fun SelectionMark(checked: Boolean) {
    val c = FinanceTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) c.primary else c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (checked) "Selected" else "Not selected",
            tint = if (checked) c.onPrimary else c.inkMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Surface card with a hairline border, the default container for lists. */
@Composable
fun FinanceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = FinanceTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(1.dp, c.line),
    ) {
        Column(content = content)
    }
}

/** Rows inside a card, separated by soft dividers. */
@Composable
fun <T> DividedRows(items: List<T>, row: @Composable (T) -> Unit) {
    val c = FinanceTheme.colors
    items.forEachIndexed { i, item ->
        if (i > 0) HorizontalDivider(color = c.lineSoft, thickness = 1.dp)
        row(item)
    }
}

/** Card heading row: title on the left, optional trailing content. */
@Composable
fun CardHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Uppercase overline above a settings group. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FinanceTheme.colors.inkMuted,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Settings/profile row: icon well, title, optional caption, one trailing element. */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = FinanceTheme.colors
    val tint = if (danger) c.danger else c.inkMuted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = FinanceType.bodyStrong,
                color = if (danger) c.danger else c.ink.copy(alpha = if (enabled) 1f else 0.38f),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
        }
        trailing()
    }
}

/** Trailing value text for a [SettingsItem]. */
@Composable
fun ItemValue(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = FinanceTheme.colors.inkMuted)
}

/** Screen title bar used by the top-level screens. */
@Composable
fun ScreenTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Dialog to set (or clear) the monthly allowance. */
@Composable
fun AllowanceDialog(
    current: Money?,
    onDismiss: () -> Unit,
    onSave: (Money?) -> Unit,
) {
    var text by remember {
        mutableStateOf(current?.let { BigDecimal.valueOf(it.minor, 2).stripTrailingZeros().toPlainString() } ?: "")
    }
    val parsed = text.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let {
        runCatching { Money(it.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly allowance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "What you plan to spend each month. Home shows how much of it is left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.inkMuted,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { onSave(parsed) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Remove", color = FinanceTheme.colors.danger)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Default category for a type (first match), or null if none loaded. */
fun defaultCategoryFor(type: TxType, categories: List<Category>): String? =
    categories.firstOrNull { it.type == type }?.id
