package com.amresalehin.emreshots.ui.navigation

sealed class Screen(val route: String) {
    data object Gallery : Screen("gallery")
    data object Settings : Screen("settings")
    data object Processing : Screen("processing")
    data object AiStudio : Screen("ai_studio")
    data object CloudProviders : Screen("cloud_providers")
    data object ScreenshotDetail : Screen("screenshot_detail/{screenshotId}") {
        fun createRoute(screenshotId: String) = "screenshot_detail/$screenshotId"
    }
}
