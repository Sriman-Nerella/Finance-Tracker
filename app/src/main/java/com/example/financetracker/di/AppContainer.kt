package com.example.financetracker.di

import com.example.financetracker.ai.ChatEntryPipeline
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.RecurringRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SyncController

/**
 * Manual DI. FinanceApp holds the single instance; UI gets it via
 * (LocalContext.current.applicationContext as FinanceApp).container
 * and AppFunctions via the same Application cast.
 * The implementation (DefaultAppContainer) is wired by the orchestrator.
 */
interface AppContainer {
    val transactions: TransactionRepository
    val categories: CategoryRepository
    val recurring: RecurringRepository
    val chatPipeline: ChatEntryPipeline
    val llm: LlmManager
    val session: SessionRepository
    val sync: SyncController
}
