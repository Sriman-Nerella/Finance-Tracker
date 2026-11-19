package com.example.financetracker

import android.app.Application
import com.example.financetracker.di.AppContainer
import com.example.financetracker.di.DefaultAppContainer

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            (container as? DefaultAppContainer)?.llmManager?.trimMemory()
        }
    }
}
