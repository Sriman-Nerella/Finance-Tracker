package com.example.financetracker.ui.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financetracker.di.AppContainer
import com.example.financetracker.ui.common.AuthActions
import com.example.financetracker.ui.common.AuthMode
import com.example.financetracker.ui.common.SignInForm

/**
 * First-run screen. Starts with three choices: sign in, create an account (both for cloud backup),
 * or continue fully offline as a guest. Picking sign in / create account shows that form with a back button.
 */
@Composable
fun IntroScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: IntroViewModel = viewModel { IntroViewModel(session = container.session) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    /** Null = the three-way choice; otherwise the form being shown. */
    var form by rememberSaveable { mutableStateOf<AuthMode?>(null) }
    val actions = remember(vm) { AuthActions(vm::signIn, vm::signUp, onModeChange = { vm.clearSignInError() }) }
    val open: (AuthMode?) -> Unit = {
        vm.clearSignInError()
        form = it
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.errorShown()
    }
    BackHandler(enabled = form != null && !state.busy) { open(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = form,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "intro",
                    ) { current ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (current == null) {
                                Choices(
                                    busy = state.busy,
                                    onSignIn = { open(AuthMode.SIGN_IN) },
                                    onSignUp = { open(AuthMode.SIGN_UP) },
                                    onGuest = vm::continueAsGuest,
                                )
                            } else {
                                FormHeader(current)
                                Spacer(Modifier.height(24.dp))
                                SignInForm(
                                    mode = current,
                                    busy = state.busy,
                                    error = state.signInError,
                                    actions = actions,
                                    onSwitchMode = {
                                        open(if (current == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            if (form != null) {
                IconButton(
                    onClick = { open(null) },
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        }
    }
}

@Composable
private fun AppMark() {
    Icon(
        imageVector = Icons.Filled.AccountBalanceWallet,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Choices(busy: Boolean, onSignIn: () -> Unit, onSignUp: () -> Unit, onGuest: () -> Unit) {
    AppMark()
    Spacer(Modifier.height(16.dp))
    Text(text = "Finance Tracker", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Track every rupee in seconds. Works fully offline; an account adds cloud backup and sync.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(40.dp))

    Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Sign in")
    }
    Spacer(Modifier.height(12.dp))
    FilledTonalButton(onClick = onSignUp, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Create account")
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onGuest, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Continue as guest")
    }
    Text(
        "Guest data stays on this phone. You can create an account later from Profile and keep it.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Google sign-in parked until GOOGLE_WEB_CLIENT_ID is configured:
    // Button(onClick = { activity?.let { vm.signInWithGoogle(it) } }) { Text("Continue with Google") }
}

@Composable
private fun FormHeader(mode: AuthMode) {
    AppMark()
    Spacer(Modifier.height(16.dp))
    Text(
        if (mode == AuthMode.SIGN_IN) "Welcome back" else "Create your account",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (mode == AuthMode.SIGN_IN) {
            "Sign in to back up and sync your entries."
        } else {
            "Pick a username and password. Your entries back up to the cloud and sync across devices."
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
