package com.example.financetracker.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.CategoryTotal
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType

/** One record row: icon, title, category/date/note, signed amount. */
@Composable
fun TransactionRow(
    tx: Transaction,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = tx.type == TxType.INCOME
    val sign = if (isIncome) "+" else "-"
    val supporting = listOfNotNull(
        category?.name,
        tx.occurredOn.formatShort(),
        tx.note?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { Icon(categoryIcon(category?.icon ?: ""), contentDescription = null) },
        headlineContent = { Text(tx.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text(
                text = sign + tx.amount.formatInr(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

/** Category name, icon and a proportional bar. [fraction] is 0..1. */
@Composable
fun CategoryBarRow(
    total: CategoryTotal,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = categoryIcon(total.category.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = total.category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = total.total.formatInr(), style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Default category for a type (first match), or null if none loaded. */
fun defaultCategoryFor(type: TxType, categories: List<Category>): String? =
    categories.firstOrNull { it.type == type }?.id
