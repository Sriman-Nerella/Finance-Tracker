package com.example.financetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financetracker.data.prefs.ThemeMode
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.ui.nav.AppNavHost
import com.example.financetracker.ui.theme.FinanceTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FinanceApp).container
        val prefs = UiPrefs.get(this)
        setContent {
            val mode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            FinanceTrackerTheme(mode = mode) {
                AppNavHost(container = container, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
