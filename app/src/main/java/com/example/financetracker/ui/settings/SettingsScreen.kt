package com.example.financetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.BuildConfig
import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmBackend
import com.example.financetracker.data.prefs.ThemeMode
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncStatus
import com.example.financetracker.ui.budget.BudgetsCard
import com.example.financetracker.ui.budget.BudgetsViewModel
import com.example.financetracker.ui.common.AllowanceDialog
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.GroupTitle
import com.example.financetracker.ui.common.ItemValue
import com.example.financetracker.ui.common.ScreenTitleBar
import com.example.financetracker.ui.common.SettingsItem
import com.example.financetracker.ui.common.WidthClass
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.theme.FinanceTheme
import java.time.Duration
import java.time.Instant
import java.util.Locale

private enum class Section(val title: String, val icon: ImageVector) {
    BUDGET("Budget", Icons.Rounded.Savings),
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    ASK_AI("Ask AI", Icons.Rounded.AutoAwesome),
    DATA("Data", Icons.Rounded.Cloud),
    ABOUT("About", Icons.Rounded.Info),
}

/** Settings as grouped lists. Tablets show the section list on the left and the chosen section on the right. */
@Composable
fun SettingsScreen(container: AppContainer, widthClass: WidthClass, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(container.session, container.sync, container.llm, UiPrefs.get(context))
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val budgetsVm: BudgetsViewModel = viewModel { BudgetsViewModel(container.budgets, container.categories) }
    val budgets by budgetsVm.state.collectAsStateWithLifecycle()
    var allowanceOpen by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(Section.BUDGET) }
    val c = FinanceTheme.colors

    val sectionContent: @Composable ColumnScope.(Section) -> Unit = { section ->
        when (section) {
            Section.BUDGET -> {
                BudgetGroup(state) { allowanceOpen = true }
                Spacer(Modifier.height(12.dp))
                BudgetsCard(budgets, onSave = budgetsVm::save, onDelete = budgetsVm::delete, title = "Recurring budgets")
            }
            Section.APPEARANCE -> AppearanceGroup(state.themeMode, vm::setThemeMode)
            Section.ASK_AI -> AiGroup(state, vm::setLocalAiEnabled, vm::download, vm::setGemmaEnabled)
            Section.DATA -> DataGroup(state.session, state.sync, vm::syncNow)
            Section.ABOUT -> AboutGroup()
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        if (widthClass == WidthClass.EXPANDED) {
            Row(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Column(Modifier.width(300.dp).fillMaxHeight().padding(horizontal = 12.dp)) {
                    ScreenTitleBar("Settings", modifier = Modifier.padding(start = 0.dp))
                    Section.entries.forEach { s ->
                        val active = s == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(if (active) c.primarySoft else c.bg)
                                .clickable { selected = s }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(s.icon, contentDescription = null, tint = if (active) c.onPrimarySoft else c.inkMuted)
                            Text(s.title, style = MaterialTheme.typography.labelLarge, color = if (active) c.onPrimarySoft else c.ink)
                        }
                    }
                }
                VerticalDivider(color = c.line)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp)
                        .widthIn(max = 640.dp),
                ) {
                    Text(selected.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                    sectionContent(selected)
                    state.message?.let { Message(it) }
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (widthClass == WidthClass.MEDIUM) Modifier.navigationBarsPadding() else Modifier)
                    .verticalScroll(rememberScrollState()),
            ) {
                ScreenTitleBar("Settings")
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                    Section.entries.forEach { s ->
                        GroupTitle(s.title)
                        sectionContent(s)
                    }
                    state.message?.let { Message(it) }
                }
            }
        }
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
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = FinanceTheme.colors.danger, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun Divider() = HorizontalDivider(color = FinanceTheme.colors.lineSoft)

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = FinanceTheme.colors.inkMuted)

@Composable
private fun BudgetGroup(state: SettingsUiState, onEditAllowance: () -> Unit) {
    FinanceCard {
        SettingsItem(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = "Monthly allowance",
            subtitle = "What you plan to spend each month",
            onClick = onEditAllowance,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemValue(state.allowance?.formatInr() ?: "Not set")
                    Chevron()
                }
            },
        )
    }
}

@Composable
private fun AppearanceGroup(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val c = FinanceTheme.colors
    FinanceCard {
        SettingsItem(icon = Icons.Rounded.Contrast, title = "Theme")
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            val options = ThemeMode.entries
            options.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onChange(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = c.primarySoft,
                        activeContentColor = c.onPrimarySoft,
                        inactiveContainerColor = c.surface,
                        inactiveContentColor = c.ink,
                        activeBorderColor = c.outline,
                        inactiveBorderColor = c.outline,
                    ),
                ) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AiGroup(
    state: SettingsUiState,
    onLocalAiChange: (Boolean) -> Unit,
    onDownload: (LlmBackend) -> Unit,
    onGemmaToggle: (Boolean) -> Unit,
) {
    val c = FinanceTheme.colors
    val nanoReady = state.geminiAvailability is LlmAvailability.Available
    val gemmaReady = state.gemmaAvailability is LlmAvailability.Available
    val anyModel = nanoReady || (gemmaReady && state.gemmaEnabled)
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = c.primary,
        checkedThumbColor = c.onPrimary,
        uncheckedTrackColor = c.surface2,
        uncheckedBorderColor = c.outline,
        uncheckedThumbColor = c.outline,
    )
    FinanceCard {
        SettingsItem(
            icon = Icons.Rounded.Memory,
            title = "On-device AI",
            subtitle = when {
                state.geminiAvailability == null -> "Checking this device…"
                anyModel -> "Reads chat messages without sending them anywhere"
                else -> "No local AI model available"
            },
            enabled = anyModel,
            onClick = if (anyModel) ({ onLocalAiChange(!state.localAiEnabled) }) else null,
            trailing = {
                Switch(
                    checked = anyModel && state.localAiEnabled,
                    onCheckedChange = onLocalAiChange,
                    enabled = anyModel,
                    colors = switchColors,
                )
            },
        )
        Divider()
        ModelRow("Gemini Nano", "Built into supported phones", LlmBackend.GEMINI_NANO, state.geminiAvailability, state, onDownload)
        Divider()
        ModelRow("Gemma", "Downloaded model for other phones", LlmBackend.GEMMA_LITERT, state.gemmaAvailability, state, onDownload)
        Divider()
        SettingsItem(
            icon = Icons.Rounded.SdStorage,
            title = "Use Gemma when Gemini Nano is missing",
            enabled = gemmaReady,
            trailing = {
                Switch(checked = state.gemmaEnabled, onCheckedChange = onGemmaToggle, enabled = gemmaReady, colors = switchColors)
            },
        )
    }
}

@Composable
private fun ModelRow(
    name: String,
    description: String,
    backend: LlmBackend,
    availability: LlmAvailability?,
    state: SettingsUiState,
    onDownload: (LlmBackend) -> Unit,
) {
    val downloadingThis = state.downloading == backend
    Column {
        SettingsItem(
            icon = Icons.Rounded.Download,
            title = name,
            subtitle = "$description · " + when {
                downloadingThis -> "Downloading…"
                availability == null -> "Checking…"
                availability is LlmAvailability.Available -> "Ready"
                availability is LlmAvailability.Downloadable -> "Needs download"
                availability is LlmAvailability.Downloading -> "Downloading…"
                else -> "Not supported here"
            },
            trailing = {
                if (availability is LlmAvailability.Downloadable && state.downloading == null) {
                    TextButton(onClick = { onDownload(backend) }) {
                        Text("Download${formatApproxSize(availability.approxBytes)}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        )
        if (downloadingThis) {
            val progress = state.downloadProgress
            val mod = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            if (progress == null) LinearProgressIndicator(mod) else LinearProgressIndicator(progress = { progress }, modifier = mod)
        }
    }
}

@Composable
private fun DataGroup(session: SessionState, sync: SyncStatus, onBackUpNow: () -> Unit) {
    FinanceCard {
        if (session is SessionState.SignedIn) {
            SettingsItem(
                icon = Icons.Rounded.CloudSync,
                title = "Cloud backup",
                subtitle = backupText(sync),
                trailing = {
                    TextButton(onClick = onBackUpNow, enabled = sync != SyncStatus.Running) {
                        Text("Back up now", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        } else {
            SettingsItem(
                icon = Icons.Rounded.CloudSync,
                title = "Cloud backup is off",
                subtitle = "Sign in from Profile to back up your entries",
            )
        }
    }
}

@Composable
private fun AboutGroup() {
    FinanceCard {
        SettingsItem(icon = Icons.Rounded.Info, title = "Version", subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}

private fun backupText(status: SyncStatus, now: Instant = Instant.now()): String = when (status) {
    SyncStatus.Disabled -> "Backup is off"
    SyncStatus.Idle -> "Waiting for changes"
    SyncStatus.Running -> "Backing up…"
    is SyncStatus.Done -> {
        val ago = Duration.between(status.at, now)
        when {
            ago < Duration.ofMinutes(1) -> "Backed up just now"
            ago < Duration.ofHours(1) -> "Backed up ${ago.toMinutes()} min ago"
            ago < Duration.ofDays(1) -> "Backed up ${ago.toHours()} h ago"
            else -> "Backed up ${ago.toDays()} days ago"
        }
    }
    is SyncStatus.Failed -> "Backup failed: ${status.message}"
}

/** " (~1.2 GB)" or "" when the size is unknown. */
private fun formatApproxSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) {
        " (~${String.format(Locale.ENGLISH, "%.1f", gb)} GB)"
    } else {
        " (~${String.format(Locale.ENGLISH, "%.0f", bytes / 1_000_000.0)} MB)"
    }
}
