package com.miaohui.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.miaohui.app.ui.screens.*
import com.miaohui.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Generate : Screen("generate", "创作", Icons.Filled.AutoAwesome)
    data object History : Screen("history", "历史", Icons.Filled.History)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
    data object Detail : Screen("detail/{recordId}", "详情", Icons.Filled.AutoAwesome)
    data object Edit : Screen("edit/{recordId}", "编辑", Icons.Filled.AutoAwesome)
}

private val screens = listOf(Screen.Generate, Screen.History, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in screens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(if (selected) 26.dp else 24.dp)
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Generate.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Generate.route) {
                GenerateScreen(
                    viewModel = viewModel,
                    onNavigateToEdit = { recordId ->
                        navController.navigate("edit/$recordId")
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { recordId ->
                        navController.navigate("detail/$recordId")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                Screen.Detail.route,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: 0L
                DetailScreen(
                    viewModel = viewModel,
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate("edit/$id") }
                )
            }
            composable(
                Screen.Edit.route,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: 0L
                EditScreen(
                    viewModel = viewModel,
                    recordId = recordId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
