package com.example.financetracker.di

import android.content.Context
import com.example.financetracker.ai.ChatEntryPipeline
import com.example.financetracker.ai.DefaultChatEntryPipeline
import com.example.financetracker.ai.llm.DefaultLlmManager
import com.example.financetracker.ai.parse.RuleBasedEntryParser
import com.example.financetracker.auth.DefaultSessionRepository
// import com.example.financetracker.auth.GoogleSignInHelper  // Google sign-in parked
import com.example.financetracker.data.db.AppDatabase
import com.example.financetracker.data.prefs.SessionStore
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.data.remote.RemoteDataSource
import com.example.financetracker.data.remote.SupabaseProvider
import com.example.financetracker.data.repo.OfflineCategoryRepository
import com.example.financetracker.data.repo.OfflineTransactionRepository
import com.example.financetracker.data.sync.SyncDeps
import com.example.financetracker.data.sync.SyncEngine
import com.example.financetracker.data.sync.WorkManagerSyncController
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase = AppDatabase.build(appContext)
    private val sessionStore = SessionStore(appContext)
    private val supabase = SupabaseProvider()

    // Constructing the controller starts watching session mode (schedules/cancels periodic sync).
    override val sync: SyncController = WorkManagerSyncController(appContext, sessionStore)

    override val transactions: TransactionRepository =
        OfflineTransactionRepository(db.transactionDao(), db.categoryDao(), onLocalWrite = { sync.requestSync() })

    override val categories: CategoryRepository = OfflineCategoryRepository(db.categoryDao())

    val llmManager = DefaultLlmManager(appContext, appScope)
    override val llm = llmManager

    override val chatPipeline: ChatEntryPipeline =
        DefaultChatEntryPipeline(
            parser = RuleBasedEntryParser(),
            llm = llm,
            categories = categories,
            localAiEnabled = { UiPrefs.get(appContext).localAiEnabled.first() },
        )

    override val session: SessionRepository =
        DefaultSessionRepository(sessionStore, supabase, /* GoogleSignInHelper(), */ db, sync)

    init {
        SyncDeps.engine = SyncEngine(db, RemoteDataSource(supabase), sessionStore)
        appScope.launch { categories.seedDefaults() }
    }
}
