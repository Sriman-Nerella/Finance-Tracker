package com.example.financetracker.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.ui.chat.ChatScreen
import com.example.financetracker.ui.common.WidthClass
import com.example.financetracker.ui.common.currentWidthClass
import com.example.financetracker.ui.home.HomeScreen
import com.example.financetracker.ui.intro.IntroScreen
import com.example.financetracker.ui.profile.ProfileScreen
import com.example.financetracker.ui.records.TransactionsScreen
import com.example.financetracker.ui.settings.SettingsScreen
import com.example.financetracker.ui.theme.FinanceTheme
import kotlinx.serialization.Serializable

// Type-safe routes.
@Serializable object IntroRoute
@Serializable object HomeRoute
@Serializable object TransactionsRoute
@Serializable object ProfileRoute
@Serializable object SettingsRoute
@Serializable object ChatRoute

private data class Tab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val matches: (NavDestination?) -> Boolean,
)

private val TABS = listOf(
    Tab(HomeRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home) { it.isRoute<HomeRoute>() },
    Tab(TransactionsRoute, "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong) {
        it.isRoute<TransactionsRoute>()
    },
    Tab(ProfileRoute, "Profile", Icons.Outlined.Person, Icons.Rounded.Person) { it.isRoute<ProfileRoute>() },
    Tab(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings) { it.isRoute<SettingsRoute>() },
)

/**
 * Root navigation. Shows nothing until the session is known, the intro while [SessionState.Undecided],
 * and the main shell afterwards.
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

/**
 * Four destinations (Home, Transactions, Profile, Settings) in a bottom bar on phones and a
 * navigation rail with the Ask AI button on tablets. Chat opens full screen from Ask AI.
 */
@Composable
private fun MainShell(container: AppContainer, modifier: Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val widthClass = currentWidthClass()
    val inChat = destination.isRoute<ChatRoute>()
    val openChat = { navController.navigate(ChatRoute) { launchSingleTop = true } }
    // Close Chat before switching tabs; otherwise its entry is saved with Home's state and restored on the next tab tap.
    val selectTab: (Any) -> Unit = { route ->
        if (inChat) navController.popBackStack()
        navController.navigateTab(route)
    }

    val host: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = hostModifier,
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    container = container,
                    widthClass = widthClass,
                    onOpenChat = openChat,
                    onSeeAll = { selectTab(TransactionsRoute) },
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreen(container = container, widthClass = widthClass)
            }
            composable<ProfileRoute> {
                ProfileScreen(container = container)
            }
            composable<SettingsRoute> {
                SettingsScreen(container = container, widthClass = widthClass)
            }
            composable<ChatRoute> {
                ChatScreen(container = container, onBack = { navController.popBackStack() })
            }
        }
    }

    val c = FinanceTheme.colors
    if (widthClass == WidthClass.COMPACT) {
        Scaffold(
            modifier = modifier,
            containerColor = c.bg,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!inChat) {
                    Column {
                        HorizontalDivider(color = c.line)
                        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                            TABS.forEach { tab ->
                                val selected = tab.matches(destination)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectTab(tab.route) },
                                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = c.onPrimarySoft,
                                        selectedTextColor = c.ink,
                                        indicatorColor = c.primarySoft,
                                        unselectedIconColor = c.inkMuted,
                                        unselectedTextColor = c.inkMuted,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            host(Modifier.padding(padding).fillMaxSize())
        }
    } else {
        Row(modifier = modifier.background(c.bg)) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = c.surface,
                header = {
                    Spacer(Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = openChat,
                        containerColor = c.primary,
                        contentColor = c.onPrimary,
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Ask AI")
                    }
                    Spacer(Modifier.height(24.dp))
                },
            ) {
                TABS.forEach { tab ->
                    val selected = tab.matches(destination)
                    NavigationRailItem(
                        selected = selected,
                        onClick = { selectTab(tab.route) },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.bodySmall) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = c.onPrimarySoft,
                            selectedTextColor = c.ink,
                            indicatorColor = c.primarySoft,
                            unselectedIconColor = c.inkMuted,
                            unselectedTextColor = c.inkMuted,
                        ),
                    )
                }
            }
            VerticalDivider(color = c.line)
            host(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** True when [this] destination (or one of its parents) is the route [T]. */
private inline fun <reified T : Any> NavDestination?.isRoute(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true

/** Standard tab navigation: single top per tab, state saved and restored. */
private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
