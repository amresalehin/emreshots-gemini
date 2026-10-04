package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.ScreenshotDetailScreen
import com.example.ui.screens.ScreenshotsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScreenshotsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenshotsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

                LaunchedEffect(snackbarMessage) {
                    snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackbar()
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isRootDestination = currentRoute in listOf(
                    Screen.Gallery.route,
                    Screen.Collections.route,
                    Screen.Settings.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (isRootDestination) {
                            BottomNavigationBar(navController = navController, currentRoute = currentRoute)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Gallery.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Gallery.route) {
                            ScreenshotsScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { id ->
                                    navController.navigate(Screen.ScreenshotDetail.createRoute(id))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(Screen.Collections.route) {
                            CollectionsScreen(
                                viewModel = viewModel,
                                onNavigateToCollection = { colId ->
                                    navController.navigate(Screen.CollectionDetail.createRoute(colId))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(viewModel = viewModel)
                        }

                        // Backwards compatibility mappings for deep links
                        composable(Screen.AiStudio.route) {
                            SettingsScreen(viewModel = viewModel)
                        }

                        composable(Screen.CloudProviders.route) {
                            SettingsScreen(viewModel = viewModel)
                        }

                        composable(
                            route = Screen.ScreenshotDetail.route,
                            arguments = listOf(navArgument("screenshotId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val screenshotId = backStackEntry.arguments?.getString("screenshotId") ?: ""
                            ScreenshotDetailScreen(
                                screenshotId = screenshotId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.CollectionDetail.route,
                            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                            CollectionDetailScreen(
                                collectionId = collectionId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScreenshot = { shotId ->
                                    navController.navigate(Screen.ScreenshotDetail.createRoute(shotId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun BottomNavigationBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        NavigationItem(Screen.Gallery.route, "Gallery", Icons.Filled.Collections, Icons.Outlined.Collections, "nav_gallery"),
        NavigationItem(Screen.Collections.route, "Collections", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial, "nav_collections"),
        NavigationItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    NavigationBar(
        modifier = Modifier.testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
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
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                },
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}
