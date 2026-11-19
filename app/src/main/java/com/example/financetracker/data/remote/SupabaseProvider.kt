package com.example.financetracker.data.remote

import com.example.financetracker.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Lazily builds the Supabase client. Never touch [client] unless [isConfigured]. */
class SupabaseProvider(
    private val url: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) { "Supabase is not configured" }
        createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
        }
    }
}
