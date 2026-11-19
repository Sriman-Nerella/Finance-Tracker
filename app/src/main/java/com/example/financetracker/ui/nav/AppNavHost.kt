package com.example.financetracker.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.ui.chat.ChatScreen
import com.example.financetracker.ui.home.HomeScreen
import com.example.financetracker.ui.intro.IntroScreen
import com.example.financetracker.ui.records.RecordsScreen
import com.example.financetracker.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Type-safe routes.
@Serializable object IntroRoute
@Serializable object HomeRoute
@Serializable object RecordsRoute
@Serializable object ChatRoute
@Serializable object SettingsRoute

/**
 * Root navigation. Shows nothing until the session is known, the intro while [SessionState.Undecided],
 * and the four-tab shell afterwards.
 */
@Composable
fun AppNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val sessionState by produceState<SessionState?>(initialValue = null, container) {
        container.session.session.collect { value = it }
    }

    when (sessionState) {
        null -> Unit
        SessionState.Undecided -> IntroHost(container = container, modifier = modifier)
        else -> MainShell(container = container, modifier = modifier)
    }
}

@Composable
private fun IntroHost(container: AppContainer, modifier: Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = IntroRoute,
        modifier = modifier,
    ) {
        composable<IntroRoute> {
            IntroScreen(container = container)
        }
    }
}

@Composable
private fun MainShell(container: AppContainer, modifier: Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination.isRoute<HomeRoute>(),
                    onClick = { navController.navigateTab(HomeRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = destination.isRoute<RecordsRoute>(),
                    onClick = { navController.navigateTab(RecordsRoute) },
                    icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                    label = { Text("Records") },
                )
                NavigationBarItem(
                    selected = destination.isRoute<ChatRoute>(),
                    onClick = { navController.navigateTab(ChatRoute) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                )
                NavigationBarItem(
                    selected = destination.isRoute<SettingsRoute>(),
                    onClick = { navController.navigateTab(SettingsRoute) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    container = container,
                    onOpenChat = { navController.navigateTab(ChatRoute) },
                )
            }
            composable<RecordsRoute> {
                RecordsScreen(container = container)
            }
            composable<ChatRoute> {
                ChatScreen(container = container)
            }
            composable<SettingsRoute> {
                SettingsScreen(container = container)
            }
        }
    }
}

/** True when [this] destination (or one of its parents) is the route [T]. */
private inline fun <reified T : Any> NavDestination?.isRoute(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true

/** Standard bottom-bar navigation: single top per tab, state saved and restored. */
private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
