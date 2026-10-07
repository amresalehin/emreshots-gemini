package com.amresalehin.emreshots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amresalehin.emreshots.ui.navigation.Screen
import com.amresalehin.emreshots.ui.screens.AiStudioScreen
import com.amresalehin.emreshots.ui.screens.CloudProvidersScreen
import com.amresalehin.emreshots.ui.screens.ScreenshotDetailScreen
import com.amresalehin.emreshots.ui.screens.ScreenshotsScreen
import com.amresalehin.emreshots.ui.screens.SettingsScreen
import com.amresalehin.emreshots.ui.screens.ProcessingScreen
import com.amresalehin.emreshots.ui.theme.EmreShotsTheme
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import com.amresalehin.emreshots.service.media.BackgroundSyncScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenshotsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackgroundSyncScheduler.schedule(this)
        enableEdgeToEdge()
        setContent {
            EmreShotsTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

                LaunchedEffect(snackbarMessage) {
                    snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackbar()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenProcessing = { navController.navigate(Screen.Processing.route) },
                                onOpenAiStudio = { navController.navigate(Screen.AiStudio.route) },
                                onOpenProviders = { navController.navigate(Screen.CloudProviders.route) }
                            )
                        }

                        composable(Screen.Processing.route) {
                            ProcessingScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.AiStudio.route) {
                            AiStudioScreen(
                                viewModel = viewModel,
                                onNavigateToProviders = {
                                    navController.navigate(Screen.CloudProviders.route)
                                }
                            )
                        }

                        composable(Screen.CloudProviders.route) {
                            CloudProvidersScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.ScreenshotDetail.route,
                            arguments = listOf(navArgument("screenshotId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val screenshotId = backStackEntry.arguments?.getString("screenshotId") ?: ""
                            ScreenshotDetailScreen(
                                screenshotId = screenshotId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScreenshot = { targetId ->
                                    navController.navigate(Screen.ScreenshotDetail.createRoute(targetId)) {
                                        popUpTo(Screen.ScreenshotDetail.route) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
