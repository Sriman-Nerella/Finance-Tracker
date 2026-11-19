package com.example.financetracker.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.BuildConfig
import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmBackend
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(container.session, container.sync, container.llm)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    var showSignOut by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccountSection(
            session = state.session,
            signingIn = state.signingIn,
            onSignIn = { activity?.let { vm.signInWithGoogle(it) } },
            canSignIn = activity != null,
            onSignOut = { showSignOut = true },
        )

        if (state.session is SessionState.SignedIn) {
            BackupSection(status = state.sync, onBackUpNow = vm::syncNow)
        }

        AiSection(
            state = state,
            onDownload = vm::download,
            onGemmaToggle = vm::setGemmaEnabled,
        )

        AboutSection()

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showSignOut) {
        SignOutDialog(
            onDismiss = { showSignOut = false },
            onKeep = {
                showSignOut = false
                vm.signOut(wipeLocal = false)
            },
            onWipe = {
                showSignOut = false
                vm.signOut(wipeLocal = true)
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun AccountSection(
    session: SessionState,
    signingIn: Boolean,
    canSignIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    SettingsSection(title = "Account") {
        when (session) {
            is SessionState.SignedIn -> {
                Text(
                    text = session.displayName?.takeIf { it.isNotBlank() } ?: "Signed in",
                    style = MaterialTheme.typography.bodyLarge,
                )
                session.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onSignOut) { Text("Sign out") }
            }
            else -> {
                Text(
                    text = "Data stays on this device. Sign in with Google to back up.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val configured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
                if (!configured) {
                    Text(
                        text = "Google sign-in isn't configured in this build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onSignIn,
                    enabled = configured && canSignIn && !signingIn,
                ) {
                    Text(if (signingIn) "Signing in…" else "Sign in with Google")
                }
                Text(
                    text = "Your records on this device are kept and uploaded after you sign in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackupSection(status: SyncStatus, onBackUpNow: () -> Unit) {
    SettingsSection(title = "Backup") {
        when (status) {
            SyncStatus.Disabled -> Text("Backup is off", style = MaterialTheme.typography.bodyMedium)
            SyncStatus.Idle -> Text("Waiting for changes", style = MaterialTheme.typography.bodyMedium)
            SyncStatus.Running -> {
                Text("Backing up…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SyncStatus.Done -> Text(
                text = "Last backup ${relativeTime(status.at)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            is SyncStatus.Failed -> Text(
                text = "Backup failed: ${status.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = onBackUpNow,
            enabled = status != SyncStatus.Running,
        ) { Text("Back up now") }
    }
}

@Composable
private fun AiSection(
    state: SettingsUiState,
    onDownload: (LlmBackend) -> Unit,
    onGemmaToggle: (Boolean) -> Unit,
) {
    val gemmaAvailable = state.gemmaAvailability != null &&
        state.gemmaAvailability !is LlmAvailability.Unavailable

    SettingsSection(title = "On-device AI") {
        Text(
            text = "Active: ${backendLabel(state.activeBackend)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        BackendRow(
            name = backendLabel(LlmBackend.GEMINI_NANO),
            backend = LlmBackend.GEMINI_NANO,
            availability = state.geminiAvailability,
            state = state,
            onDownload = onDownload,
        )
        BackendRow(
            name = backendLabel(LlmBackend.GEMMA_LITERT),
            backend = LlmBackend.GEMMA_LITERT,
            availability = state.gemmaAvailability,
            state = state,
            onDownload = onDownload,
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Use Gemma when Gemini Nano is unavailable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(
                checked = state.gemmaEnabled,
                onCheckedChange = onGemmaToggle,
                enabled = gemmaAvailable,
            )
        }
    }
}

@Composable
private fun BackendRow(
    name: String,
    backend: LlmBackend,
    availability: LlmAvailability?,
    state: SettingsUiState,
    onDownload: (LlmBackend) -> Unit,
) {
    val isDownloadingThis = state.downloading == backend
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = availabilityText(availability, isDownloadingThis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (availability is LlmAvailability.Downloadable && state.downloading == null) {
                Button(onClick = { onDownload(backend) }) {
                    Text("Download${formatApproxSize(availability.approxBytes)}")
                }
            }
        }
        if (isDownloadingThis) {
            val progress = state.downloadProgress
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AboutSection() {
    SettingsSection(title = "About") {
        Text(text = "Finance Tracker", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignOutDialog(
    onDismiss: () -> Unit,
    onKeep: () -> Unit,
    onWipe: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = {
            Text("Keep your records on this device, or wipe them from this device before signing out.")
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onWipe) {
                    Text("Wipe data", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onKeep) { Text("Keep on device") }
            }
        },
    )
}

private fun backendLabel(backend: LlmBackend): String = when (backend) {
    LlmBackend.GEMINI_NANO -> "Gemini Nano (built-in)"
    LlmBackend.GEMMA_LITERT -> "Gemma (downloaded)"
    LlmBackend.NONE -> "Basic parser only"
}

private fun availabilityText(availability: LlmAvailability?, isDownloadingThis: Boolean): String = when {
    isDownloadingThis -> "Downloading…"
    availability == null -> "Checking…"
    availability is LlmAvailability.Available -> "Ready"
    availability is LlmAvailability.Downloadable -> "Needs download"
    availability is LlmAvailability.Downloading -> "Downloading…"
    else -> "Not supported on this device"
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

private val BACKUP_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

private fun relativeTime(at: Instant, now: Instant = Instant.now()): String {
    val elapsed = Duration.between(at, now)
    return when {
        elapsed < Duration.ofMinutes(1) -> "just now"
        elapsed < Duration.ofHours(1) -> "${elapsed.toMinutes()} min ago"
        elapsed < Duration.ofDays(1) -> "${elapsed.toHours()} h ago"
        else -> BACKUP_TIME_FORMAT.withZone(ZoneId.systemDefault()).format(at)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
