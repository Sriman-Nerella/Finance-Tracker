package com.example.financetracker.auth

/**
 * Username login on top of Supabase email auth.
 *
 * Supabase Auth signs in by email, so a username maps to a hidden address on a reserved
 * domain (RFC 2606) where mail can never be delivered. The `profiles` table and its trigger
 * (supabase/migrations/0002_profiles.sql) use the same domain and username rules.
 */
object UsernameAuth {
    const val EMAIL_DOMAIN = "users.example.com"
    private val VALID = Regex("^[a-z0-9_.]{3,20}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    fun isValid(username: String): Boolean = VALID.matches(normalize(username))

    /** A requirement a new password must meet; [label] is shown in the sign-up checklist. */
    class PasswordRule(val label: String, val isMet: (String) -> Boolean)

    const val MIN_PASSWORD = 8

    val PASSWORD_RULES = listOf(
        PasswordRule("At least $MIN_PASSWORD characters") { it.length >= MIN_PASSWORD },
        PasswordRule("An uppercase letter (A–Z)") { p -> p.any { it in 'A'..'Z' } },
        PasswordRule("A lowercase letter (a–z)") { p -> p.any { it in 'a'..'z' } },
        PasswordRule("A number (0–9)") { p -> p.any { it in '0'..'9' } },
    )

    fun isStrongPassword(password: String): Boolean = PASSWORD_RULES.all { it.isMet(password) }

    /** First problem with a new account's details, or null when they can be submitted. */
    fun signUpProblem(username: String, password: String): String? {
        if (!isValid(username)) return "Usernames are 3–20 letters, numbers, _ or ."
        val missing = PASSWORD_RULES.firstOrNull { !it.isMet(password) } ?: return null
        return "Password needs: ${missing.label.replaceFirstChar { it.lowercase() }}"
    }

    fun toEmail(username: String): String = "${normalize(username)}@$EMAIL_DOMAIN"

    /** Username for a synthetic address, or null for any other email. */
    fun fromEmail(email: String?): String? =
        email?.takeIf { it.endsWith("@$EMAIL_DOMAIN", ignoreCase = true) }?.substringBefore('@')
}
