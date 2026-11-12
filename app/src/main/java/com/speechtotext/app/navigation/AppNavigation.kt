package com.speechtotext.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.speechtotext.app.ui.history.HistoryScreen
import com.speechtotext.app.ui.history.HistoryViewModel
import com.speechtotext.app.ui.home.HomeScreen
import com.speechtotext.app.ui.home.HomeViewModel
import com.speechtotext.app.ui.languages.LanguageManagerScreen
import com.speechtotext.app.ui.languages.LanguageViewModel
import com.speechtotext.app.ui.record.RecordScreen
import com.speechtotext.app.ui.record.RecordViewModel
import com.speechtotext.app.ui.result.ResultScreen
import com.speechtotext.app.ui.result.ResultViewModel
import com.speechtotext.app.ui.settings.SettingsScreen
import com.speechtotext.app.ui.settings.SettingsViewModel
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Record : Screen("record", "Record", Icons.Filled.Mic, Icons.Outlined.Mic)
    object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

const val ROUTE_LANGUAGES = "languages"
const val ROUTE_RESULT = "result/{transcriptId}"

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarScreens = listOf(
        Screen.Home,
        Screen.Record,
        Screen.History,
        Screen.Settings
    )

    val showBottomBar = currentRoute in bottomBarScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary
                ) {
                    bottomBarScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BackgroundDark,
                                selectedTextColor = CyanNeon,
                                indicatorColor = CyanNeon,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_tab_${screen.route}")
                        )
                    }
                }
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundDark)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel()
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToResult = { id ->
                            navController.navigate("result/$id")
                        },
                        onNavigateToLanguages = {
                            navController.navigate(ROUTE_LANGUAGES)
                        }
                    )
                }

                composable(Screen.Record.route) {
                    val recordViewModel: RecordViewModel = viewModel()
                    RecordScreen(
                        viewModel = recordViewModel,
                        onNavigateToResult = { id ->
                            navController.navigate("result/$id")
                        },
                        onNavigateToLanguages = {
                            navController.navigate(ROUTE_LANGUAGES)
                        }
                    )
                }

                composable(Screen.History.route) {
                    val historyViewModel: HistoryViewModel = viewModel()
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateToResult = { id ->
                            navController.navigate("result/$id")
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel()
                    SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }

                composable(ROUTE_LANGUAGES) {
                    val languageViewModel: LanguageViewModel = viewModel()
                    LanguageManagerScreen(
                        viewModel = languageViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = ROUTE_RESULT,
                    arguments = listOf(navArgument("transcriptId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val transcriptId = backStackEntry.arguments?.getLong("transcriptId") ?: 0L
                    val resultViewModel: ResultViewModel = viewModel()
                    ResultScreen(
                        transcriptId = transcriptId,
                        viewModel = resultViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
