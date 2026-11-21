package com.example.financetracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.financetracker.auth.UsernameAuth

enum class AuthMode { SIGN_IN, SIGN_UP }

/** Callbacks shared by the inline form and the dialog. Usernames arrive normalised. */
class AuthActions(
    val onSignIn: (username: String, password: String) -> Unit,
    val onSignUp: (username: String, password: String, displayName: String?) -> Unit,
    /** Called when the user switches between sign-in and sign-up, so callers can clear old errors. */
    val onModeChange: (AuthMode) -> Unit = {},
)

/** Everything the form holds. Immutable so it can live in one saveable state. */
private data class AuthValues(
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val confirm: String = "",
) {
    fun canSubmit(mode: AuthMode, busy: Boolean): Boolean = !busy && when (mode) {
        AuthMode.SIGN_IN -> UsernameAuth.isValid(username) && password.isNotEmpty()
        AuthMode.SIGN_UP -> UsernameAuth.signUpProblem(username, password) == null && confirm == password
    }

    fun submit(mode: AuthMode, actions: AuthActions) {
        val u = UsernameAuth.normalize(username)
        when (mode) {
            AuthMode.SIGN_IN -> actions.onSignIn(u, password)
            AuthMode.SIGN_UP -> actions.onSignUp(u, password, name.trim().takeIf { it.isNotEmpty() })
        }
    }

    fun toList() = listOf(name, username, password, confirm)

    companion object {
        fun fromList(l: List<String>) = AuthValues(l[0], l[1], l[2], l[3])
    }
}

private val AuthValuesSaver = androidx.compose.runtime.saveable.Saver<AuthValues, List<String>>(
    save = { it.toList() },
    restore = { AuthValues.fromList(it) },
)

/** Stateless fields for both modes. */
@Composable
private fun AuthFieldsUi(
    mode: AuthMode,
    values: AuthValues,
    onChange: (AuthValues) -> Unit,
    busy: Boolean,
    error: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val signUp = mode == AuthMode.SIGN_UP
    val usernameError = values.username.isNotEmpty() && !UsernameAuth.isValid(values.username)
    val passwordWeak = signUp && values.password.isNotEmpty() && !UsernameAuth.isStrongPassword(values.password)
    val mismatch = signUp && values.confirm.isNotEmpty() && values.confirm != values.password
    val visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (signUp) {
            OutlinedTextField(
                value = values.name,
                onValueChange = { onChange(values.copy(name = it.take(40))) },
                label = { Text("Your name (optional)") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = values.username,
            onValueChange = { onChange(values.copy(username = it.trim())) },
            label = { Text("Username") },
            singleLine = true,
            enabled = !busy,
            isError = usernameError,
            supportingText = when {
                usernameError -> ({ Text("3–20 letters, numbers, _ or .") })
                signUp -> ({ Text("You'll use this to sign in. 3–20 letters, numbers, _ or .") })
                else -> null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = values.password,
            onValueChange = { onChange(values.copy(password = it)) },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            isError = passwordWeak,
            visualTransformation = visual,
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (signUp) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (signUp) {
            PasswordChecklist(values.password)
            OutlinedTextField(
                value = values.confirm,
                onValueChange = { onChange(values.copy(confirm = it)) },
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = !busy,
                isError = mismatch,
                supportingText = if (mismatch) {
                    { Text("Passwords don't match") }
                } else null,
                visualTransformation = visual,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModeSwitch(mode: AuthMode, busy: Boolean, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onSwitch, enabled = !busy, modifier = modifier) {
        Text(if (mode == AuthMode.SIGN_IN) "New here? Create an account" else "Already have an account? Sign in")
    }
}

/** Live list of password rules, ticked as each one is met. */
@Composable
private fun PasswordChecklist(password: String) {
    val ok = MaterialTheme.colorScheme.primary
    val pending = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UsernameAuth.PASSWORD_RULES.forEach { rule ->
            val met = rule.isMet(password)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (met) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (met) "Done" else "Not yet",
                    tint = if (met) ok else pending,
                    modifier = Modifier.size(16.dp),
                )
                Text(rule.label, style = MaterialTheme.typography.bodySmall, color = if (met) ok else pending)
            }
        }
    }
}

/**
 * Inline form for one [mode] (intro screen). The caller decides which mode to show;
 * [onSwitchMode], when given, adds a link to the other mode.
 */
@Composable
fun SignInForm(
    mode: AuthMode,
    busy: Boolean,
    error: String?,
    actions: AuthActions,
    modifier: Modifier = Modifier,
    onSwitchMode: (() -> Unit)? = null,
) {
    var values by rememberSaveable(stateSaver = AuthValuesSaver) { mutableStateOf(AuthValues()) }
    val focus = LocalFocusManager.current
    val submit = {
        if (values.canSubmit(mode, busy)) {
            focus.clearFocus()
            values.submit(mode, actions)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthFieldsUi(mode, values, { values = it }, busy, error, onDone = submit, modifier = Modifier.fillMaxWidth())
        Button(onClick = submit, enabled = values.canSubmit(mode, busy), modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(if (mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
            }
        }
        if (onSwitchMode != null) {
            ModeSwitch(mode, busy, onSwitch = {
                values = values.copy(confirm = "")
                onSwitchMode()
            })
        }
    }
}

/** Sign-in / create-account dialog for screens already inside the app (Profile). */
@Composable
fun SignInDialog(
    busy: Boolean,
    error: String?,
    actions: AuthActions,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var values by rememberSaveable(stateSaver = AuthValuesSaver) { mutableStateOf(AuthValues()) }
    val focus = LocalFocusManager.current
    val submit = {
        if (values.canSubmit(mode, busy)) {
            focus.clearFocus()
            values.submit(mode, actions)
        }
    }
    val signUp = mode == AuthMode.SIGN_UP
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (signUp) "Create an account" else "Sign in to back up") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AuthFieldsUi(mode, values, { values = it }, busy, error, onDone = submit)
                ModeSwitch(mode, busy, onSwitch = {
                    mode = if (signUp) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                    values = values.copy(confirm = "")
                    actions.onModeChange(mode)
                })
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = values.canSubmit(mode, busy)) {
                Text(
                    when {
                        busy && signUp -> "Creating…"
                        busy -> "Signing in…"
                        signUp -> "Create account"
                        else -> "Sign in"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
