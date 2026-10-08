@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("ExperimentalMaterial3Api")

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amresalehin.emreshots.ui.navigation.Screen
import com.amresalehin.emreshots.ui.screens.AiStudioScreen
import com.amresalehin.emreshots.ui.screens.CloudProvidersScreen
import com.amresalehin.emreshots.ui.screens.ScreenshotDetailScreen
import com.amresalehin.emreshots.ui.screens.ScreenshotsScreen
import com.amresalehin.emreshots.ui.screens.SettingsScreen
import com.amresalehin.emreshots.ui.screens.ProcessingScreen
import com.amresalehin.emreshots.ui.screens.OnDeviceVisionScreen
import com.amresalehin.emreshots.ui.screens.OcrSettingsScreen
import com.amresalehin.emreshots.ui.screens.BackupRestoreScreen
import com.amresalehin.emreshots.ui.screens.CollectionsScreen
import com.amresalehin.emreshots.ui.theme.EmreShotsTheme
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import com.amresalehin.emreshots.service.media.BackgroundSyncScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenshotsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
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

                        composable(Screen.GalleryCollection.route, arguments = listOf(navArgument("collectionId") { type = NavType.StringType })) { backStackEntry ->
                            ScreenshotsScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { id ->
                                    navController.navigate(Screen.ScreenshotDetail.createRoute(id))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                initialCollectionId = backStackEntry.arguments?.getString("collectionId")
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenProcessing = { navController.navigate(Screen.Processing.route) },
                                onOpenAiStudio = { navController.navigate(Screen.AiStudio.route) },
                                onOpenCloudProviders = { navController.navigate(Screen.CloudProviders.route) },
                                onOpenOnDeviceVision = { navController.navigate(Screen.OnDeviceVision.route) },
                                onOpenOcr = { navController.navigate(Screen.OcrSettings.route) },
                                onOpenBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                                onOpenCollections = { navController.navigate(Screen.Collections.route) }
                            )
                        }

                        composable(Screen.Processing.route) {
                            ProcessingScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.OnDeviceVision.route) {
                            OnDeviceVisionScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.OcrSettings.route) {
                            OcrSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.BackupRestore.route) {
                            BackupRestoreScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Collections.route) {
                            CollectionsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenCollection = { collectionId ->
                                    navController.navigate(Screen.GalleryCollection.createRoute(collectionId))
                                }
                            )
                        }

                        composable(Screen.AiStudio.route) {
                            AiStudioScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
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