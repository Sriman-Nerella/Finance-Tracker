package com.example.financetracker.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.auth.UsernameAuth
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncStatus
import com.example.financetracker.ui.common.AllowanceDialog
import com.example.financetracker.ui.common.AuthActions
import com.example.financetracker.ui.common.SignInDialog
import com.example.financetracker.ui.common.FinanceCard
import com.example.financetracker.ui.common.GroupTitle
import com.example.financetracker.ui.common.ItemValue
import com.example.financetracker.ui.common.ScreenTitleBar
import com.example.financetracker.ui.common.SettingsItem
import com.example.financetracker.ui.common.formatInr
import com.example.financetracker.ui.theme.FinanceTheme
import com.example.financetracker.ui.theme.FinanceType
import androidx.compose.material3.HorizontalDivider
import java.time.Duration
import java.time.Instant

/** Profile: who you are, a few stats, your account and backup, and your money settings. */
@Composable
fun ProfileScreen(container: AppContainer, modifier: Modifier = Modifier, onOpenRecurring: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: ProfileViewModel = viewModel {
        ProfileViewModel(container.transactions, container.categories, container.session, container.sync, UiPrefs.get(context))
    }
    val state by vm.state.collectAsStateWithLifecycle()
    // val activity = context.findActivity()  // needed again with Google sign-in
    var allowanceOpen by rememberSaveable { mutableStateOf(false) }
    var signOutOpen by rememberSaveable { mutableStateOf(false) }
    var signInOpen by rememberSaveable { mutableStateOf(false) }
    val c = FinanceTheme.colors
    val signedIn = state.session as? SessionState.SignedIn

    Box(modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTitleBar("Profile")
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                // Header
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(c.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initials = signedIn?.displayName?.split(' ')?.filter { it.isNotBlank() }?.take(2)
                            ?.joinToString("") { it.take(1).uppercase() }
                        if (!initials.isNullOrEmpty()) {
                            Text(initials, style = MaterialTheme.typography.titleLarge, color = c.onPrimarySoft)
                        } else {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = c.onPrimarySoft, modifier = Modifier.size(36.dp))
                        }
                    }
                    Text(
                        signedIn?.displayName?.takeIf { it.isNotBlank() } ?: if (signedIn != null) "Signed in" else "Guest",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        signedIn?.let { s -> UsernameAuth.fromEmail(s.email)?.let { "@$it" } ?: s.email }
                            ?: "Not signed in · entries stay on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${state.totalEntries}", "Entries", Modifier.weight(1f))
                    Stat("${state.monthEntries}", "This month", Modifier.weight(1f))
                    Stat("${state.streak}", if (state.streak == 1) "Day streak" else "Day streak", Modifier.weight(1f))
                }

                GroupTitle("Account")
                FinanceCard {
                    if (signedIn != null) {
                        SettingsItem(
                            Icons.Rounded.AccountCircle,
                            "Account",
                            subtitle = UsernameAuth.fromEmail(signedIn.email)?.let { "@$it" } ?: signedIn.email ?: "Signed in",
                        )
                        HorizontalDivider(color = c.lineSoft)
                        SettingsItem(
                            icon = if (state.sync is SyncStatus.Failed) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                            title = "Backup",
                            subtitle = syncText(state.sync),
                            trailing = {
                                TextButton(onClick = vm::syncNow, enabled = state.sync != SyncStatus.Running) {
                                    Text("Back up now", style = MaterialTheme.typography.labelLarge)
                                }
                            },
                        )
                    } else {
                        // Google sign-in parked until GOOGLE_WEB_CLIENT_ID is configured.
                        SettingsItem(
                            icon = Icons.AutoMirrored.Rounded.Login,
                            title = if (state.signingIn) "Signing in…" else "Sign in or create account",
                            subtitle = "Back up your entries and use them on other devices",
                            enabled = !state.signingIn,
                            onClick = {
                                vm.clearSignInError()
                                signInOpen = true
                            },
                            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkMuted) },
                        )
                    }
                }

                GroupTitle("Money")
                FinanceCard {
                    SettingsItem(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "Monthly allowance",
                        subtitle = if (state.allowance == null) "Not set" else null,
                        onClick = { allowanceOpen = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                state.allowance?.let { ItemValue(it.formatInr()) }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkMuted)
                            }
                        },
                    )
                    HorizontalDivider(color = c.lineSoft)
                    SettingsItem(
                        icon = Icons.Rounded.EventRepeat,
                        title = "Recurring budgets",
                        subtitle = "Bills, subscriptions and income that repeat",
                        onClick = onOpenRecurring,
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkMuted) },
                    )
                    HorizontalDivider(color = c.lineSoft)
                    SettingsItem(Icons.Rounded.Category, "Categories", subtitle = "${state.categoryCount} categories")
                }

                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger, modifier = Modifier.padding(top = 16.dp))
                }

                if (signedIn != null) {
                    OutlinedButton(
                        onClick = { signOutOpen = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
                        border = BorderStroke(1.dp, c.outline),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // Close the dialog once sign-in succeeds.
    LaunchedEffect(signedIn != null) { if (signedIn != null) signInOpen = false }
    if (signInOpen && signedIn == null) {
        SignInDialog(
            busy = state.signingIn,
            error = state.signInError,
            actions = remember(vm) { AuthActions(vm::signIn, vm::signUp, onModeChange = { vm.clearSignInError() }) },
            onDismiss = { signInOpen = false; vm.clearSignInError() },
        )
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
    if (signOutOpen) {
        AlertDialog(
            onDismissRequest = { signOutOpen = false },
            title = { Text("Sign out?") },
            text = { Text("Keep your entries on this device, or remove them from this device before signing out.") },
            dismissButton = { TextButton(onClick = { signOutOpen = false }) { Text("Cancel") } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        signOutOpen = false
                        vm.signOut(wipeLocal = true)
                    }) { Text("Remove entries", color = c.danger) }
                    TextButton(onClick = {
                        signOutOpen = false
                        vm.signOut(wipeLocal = false)
                    }) { Text("Keep on device") }
                }
            },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = FinanceType.amountLg)
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

private fun syncText(status: SyncStatus, now: Instant = Instant.now()): String = when (status) {
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

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
