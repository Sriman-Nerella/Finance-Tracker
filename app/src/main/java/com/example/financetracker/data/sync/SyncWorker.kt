package com.example.financetracker.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Holder the orchestrator must populate in FinanceApp.onCreate():
 *   SyncDeps.engine = SyncEngine(db, RemoteDataSource(supabaseProvider), sessionStore, Clock.systemUTC())
 * (default WorkManager initialisation is used, so no custom WorkerFactory is needed).
 */
object SyncDeps {
    @Volatile
    var engine: SyncEngine? = null
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = SyncDeps.engine ?: return Result.retry()
        val outcome = engine.sync()
        return when {
            outcome.isSuccess -> Result.success()
            outcome.exceptionOrNull() is IllegalStateException &&
                outcome.exceptionOrNull()?.message == "Not signed in" -> Result.success() // nothing to do
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
