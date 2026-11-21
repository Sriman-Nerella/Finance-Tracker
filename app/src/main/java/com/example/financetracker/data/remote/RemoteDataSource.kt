package com.example.financetracker.data.remote

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/** Thin PostgREST wrapper. RLS scopes every query to the signed-in user. */
class RemoteDataSource(private val supabase: SupabaseProvider) {

    private val postgrest get() = supabase.client.postgrest

    suspend fun upsertTransactions(rows: List<TransactionRow>) {
        if (rows.isEmpty()) return
        postgrest.from("transactions").upsert(rows) // PK (user_id, id) is the conflict target
    }

    suspend fun upsertCategories(rows: List<CategoryRow>) {
        if (rows.isEmpty()) return
        postgrest.from("categories").upsert(rows)
    }

    suspend fun upsertRecurring(rows: List<RecurringRow>) {
        if (rows.isEmpty()) return
        postgrest.from(RECURRING_TABLE).upsert(rows)
    }

    /** All rows with server_updated_at > [since] (null = everything), ascending, fetched page by page. */
    suspend fun fetchTransactions(since: String?, pageSize: Int = PAGE): List<TransactionPull> {
        val out = ArrayList<TransactionPull>()
        var from = 0L
        while (true) {
            val page = postgrest.from("transactions").select {
                filter { if (since != null) gt("server_updated_at", since) }
                order("server_updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + pageSize - 1)
            }.decodeList<TransactionPull>()
            out += page
            if (page.size < pageSize) break
            from += pageSize
        }
        return out
    }

    suspend fun fetchCategories(since: String?, pageSize: Int = PAGE): List<CategoryPull> {
        val out = ArrayList<CategoryPull>()
        var from = 0L
        while (true) {
            val page = postgrest.from("categories").select {
                filter { if (since != null) gt("server_updated_at", since) }
                order("server_updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + pageSize - 1)
            }.decodeList<CategoryPull>()
            out += page
            if (page.size < pageSize) break
            from += pageSize
        }
        return out
    }

    suspend fun fetchRecurring(since: String?, pageSize: Int = PAGE): List<RecurringPull> {
        val out = ArrayList<RecurringPull>()
        var from = 0L
        while (true) {
            val page = postgrest.from(RECURRING_TABLE).select {
                filter { if (since != null) gt("server_updated_at", since) }
                order("server_updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + pageSize - 1)
            }.decodeList<RecurringPull>()
            out += page
            if (page.size < pageSize) break
            from += pageSize
        }
        return out
    }

    private companion object {
        const val PAGE = 500
        const val RECURRING_TABLE = "recurring_transactions"
    }
}
