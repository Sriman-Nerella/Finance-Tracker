package com.example.financetracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.financetracker.auth.UsernameAuth

private fun canSubmit(busy: Boolean, username: String, password: String) =
    !busy && UsernameAuth.isValid(username) && password.isNotEmpty()

/** Username + password fields. Stateless: callers hold the values. */
@Composable
private fun SignInFields(
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val usernameError = username.isNotEmpty() && !UsernameAuth.isValid(username)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { onUsername(it.trim()) },
            label = { Text("Username") },
            singleLine = true,
            enabled = !busy,
            isError = usernameError,
            supportingText = if (usernameError) {
                { Text("3–20 letters, numbers, _ or .") }
            } else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Inline sign-in form with its own button (intro screen). */
@Composable
fun SignInForm(
    busy: Boolean,
    error: String?,
    onSubmit: (username: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val enabled = canSubmit(busy, username, password)
    val submit = {
        if (canSubmit(busy, username, password)) {
            focus.clearFocus()
            onSubmit(UsernameAuth.normalize(username), password)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SignInFields(username, { username = it }, password, { password = it }, busy, error, onDone = submit)
        Button(onClick = submit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text("Sign in")
            }
        }
    }
}

/** Sign-in dialog for screens already inside the app (Profile). */
@Composable
fun SignInDialog(
    busy: Boolean,
    error: String?,
    onSubmit: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = {
        if (canSubmit(busy, username, password)) {
            focus.clearFocus()
            onSubmit(UsernameAuth.normalize(username), password)
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Sign in to back up") },
        text = { SignInFields(username, { username = it }, password, { password = it }, busy, error, onDone = submit) },
        confirmButton = {
            TextButton(onClick = submit, enabled = canSubmit(busy, username, password)) {
                Text(if (busy) "Signing in…" else "Sign in")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
