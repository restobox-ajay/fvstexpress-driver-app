package com.fvstexpress.driver.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.ui.analytics.AnalyticsScreen
import com.fvstexpress.driver.ui.components.Loading
import com.fvstexpress.driver.ui.camera.CameraScreen
import com.fvstexpress.driver.ui.failed.FailedScreen
import com.fvstexpress.driver.ui.login.LoginScreen
import com.fvstexpress.driver.ui.messages.MessagesScreen
import com.fvstexpress.driver.ui.profile.ProfileScreen
import com.fvstexpress.driver.ui.stop.StopScreen
import com.fvstexpress.driver.ui.tasks.TasksScreen

object Routes {
    const val LOGIN = "login"
    const val TASKS = "tasks"
    const val ANALYTICS = "analytics"
    const val PROFILE = "profile"
    const val MESSAGES = "messages"
    const val CAMERA = "camera?address={address}"
    const val STOP = "stop/{key}"
    const val FAILED = "failed/{key}/{ref}"

    fun stop(key: String) = "stop/${Uri.encode(key)}"
    fun failed(key: String, ref: String) = "failed/${Uri.encode(key)}/${Uri.encode(ref)}"
    fun camera(address: String) = "camera?address=${Uri.encode(address)}"
}

@Composable
fun AppNav(container: AppContainer) {
    val loaded by container.session.loaded.collectAsState()
    val session by container.session.session.collectAsState()
    if (!loaded) { Loading(); return }

    val nav = rememberNavController()
    val loggedIn = session != null
    // Decided once: changing NavHost's start destination later would reset the back stack.
    val start = remember { if (loggedIn) Routes.TASKS else Routes.LOGIN }

    // Session ended (log out, or refresh token rejected) -> back to login.
    LaunchedEffect(loggedIn) {
        val current = nav.currentDestination?.route
        if (!loggedIn && current != null && current != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(container) {
                nav.navigate(Routes.TASKS) { popUpTo(Routes.LOGIN) { inclusive = true } }
            }
        }
        composable(Routes.TASKS) { TasksScreen(nav, container) }
        composable(Routes.ANALYTICS) { AnalyticsScreen(nav, container) }
        composable(Routes.PROFILE) { ProfileScreen(nav, container) }
        composable(Routes.MESSAGES) { MessagesScreen(nav, container) }
        composable(Routes.CAMERA, arguments = listOf(navArgument("address") { type = NavType.StringType; defaultValue = "" })) { entry ->
            CameraScreen(nav, entry.arguments?.getString("address").orEmpty())
        }
        composable(Routes.STOP, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            StopScreen(nav, container, entry.arguments?.getString("key").orEmpty())
        }
        composable(
            Routes.FAILED,
            arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("ref") { type = NavType.StringType }),
        ) { entry ->
            FailedScreen(nav, container, entry.arguments?.getString("key").orEmpty(), entry.arguments?.getString("ref").orEmpty())
        }
    }
}
