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

    fun toEmail(username: String): String = "${normalize(username)}@$EMAIL_DOMAIN"

    /** Username for a synthetic address, or null for any other email. */
    fun fromEmail(email: String?): String? =
        email?.takeIf { it.endsWith("@$EMAIL_DOMAIN", ignoreCase = true) }?.substringBefore('@')
}
