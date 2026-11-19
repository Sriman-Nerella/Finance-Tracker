package com.example.financetracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.ui.common.TransactionRow
import com.example.financetracker.ui.common.dayHeaderLabel
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The month at a glance: spent, allowance meter, left over, per day, income, and this week's total with
 * a View more button that expands the week's entries in place.
 */
@Composable
fun DashboardCard(
    state: HomeUiState,
    categoryById: Map<String, Category>,
    onSetAllowance: () -> Unit,
    onEntryClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    expandedByDefault: Boolean = false,
) {
    val c = FinanceTheme.colors
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    val allowance = state.allowance

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = c.hero,
        contentColor = c.onHero,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                text = "${state.month.month.name} ${state.month.year} · SPENT",
                style = MaterialTheme.typography.labelSmall,
                color = c.onHeroMuted,
            )
            Text(
                text = state.spent.formatInr(),
                style = FinanceType.amountXl,
                color = c.onHero,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (allowance != null) {
                val fraction = (state.spent.minor.toFloat() / allowance.minor.toFloat()).coerceAtLeast(0f)
                val over = state.spent.minor > allowance.minor
                AllowanceMeter(fraction = fraction.coerceAtMost(1f), over = over)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = if (over) {
                            "Over by ${Money(state.spent.minor - allowance.minor).formatInr()}"
                        } else {
                            "${(fraction * 100).roundToInt()}% of ${allowance.formatInr()} allowance"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.daysLeft == 1) "Last day" else "${state.daysLeft} days left",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "No monthly allowance yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                        modifier = Modifier.weight(1f),
                    )
                    HeroPill(text = "Set allowance", onClick = onSetAllowance)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val left = state.left
                StatTile(
                    label = "Left over",
                    value = when {
                        left == null -> "—"
                        left.minor < 0 -> "−" + Money(abs(left.minor)).formatInr()
                        else -> left.formatInr()
                    },
                    modifier = Modifier.weight(1f),
                    onClick = if (allowance == null) onSetAllowance else null,
                )
                StatTile("Per day", state.perDay?.formatInr() ?: "—", Modifier.weight(1f))
                StatTile("Income", state.income.formatInr(), Modifier.weight(1f))
            }

            HorizontalDivider(color = c.heroLine, modifier = Modifier.padding(top = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val n = state.week.size
                    Text(
                        "This week · $n ${if (n == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                    )
                    Text(state.weekSpent.formatInr(), style = FinanceType.bodyStrong, color = c.onHero)
                }
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                HeroPill(
                    text = if (expanded) "Show less" else "View more",
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                    trailing = {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).rotate(rotation),
                        )
                    },
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                WeekList(state, categoryById, onEntryClick)
            }
        }
    }
}

@Composable
private fun AllowanceMeter(fraction: Float, over: Boolean) {
    val c = FinanceTheme.colors
    val animated by animateFloatAsState(fraction, label = "meter")
    Box(
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(c.heroTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (over) c.danger else c.accent),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = FinanceTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.heroRaised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.onHeroMuted)
        Text(
            value,
            style = FinanceType.bodyStrong,
            color = c.onHero,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun HeroPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = FinanceTheme.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(c.heroRaised)
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(start = 12.dp, end = if (trailing != null) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.onHero)
        trailing?.invoke()
    }
}

@Composable
private fun WeekList(
    state: HomeUiState,
    categoryById: Map<String, Category>,
    onEntryClick: (Transaction) -> Unit,
) {
    val c = FinanceTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        contentColor = c.ink,
    ) {
        Column {
            if (state.week.isEmpty()) {
                Text(
                    "Nothing logged this week yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkMuted,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                state.week.groupBy { it.occurredOn }.forEach { (date, items) ->
                    Text(
                        text = date.dayHeaderLabel(state.today).uppercase(Locale.ENGLISH),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkMuted,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                    items.forEach { tx ->
                        TransactionRow(
                            tx = tx,
                            category = categoryById[tx.categoryId],
                            onClick = { onEntryClick(tx) },
                            showDate = false,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
