package com.riftrecall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.riftrecall.app.ui.CollectionScreen
import com.riftrecall.app.ui.HomeScreen
import com.riftrecall.app.ui.RiftTheme
import com.riftrecall.app.ui.SessionScreen
import com.riftrecall.app.ui.SettingsScreen
import com.riftrecall.app.ui.StudyScreen
import com.riftrecall.app.ui.SummaryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RiftTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onStart = { mode -> if (vm.startSession(mode)) nav.navigate("session") },
                onStudy = { nav.navigate("study") },
                onCollection = { nav.navigate("collection") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("session") {
            SessionScreen(
                vm = vm,
                onDone = { nav.navigate("summary") { popUpTo("session") { inclusive = true } } },
                onExit = { nav.popBackStack("home", false) },
            )
        }
        composable("summary") {
            SummaryScreen(
                vm = vm,
                onHome = {
                    vm.endSession()
                    nav.popBackStack("home", false)
                },
                onAgain = {
                    val mode = vm.session.value?.mode
                    vm.endSession()
                    if (mode != null && vm.startSession(mode)) {
                        nav.navigate("session") { popUpTo("home") }
                    } else {
                        nav.popBackStack("home", false)
                    }
                },
            )
        }
        composable("study") { StudyScreen(vm) { nav.popBackStack() } }
        composable("collection") { CollectionScreen(vm) { nav.popBackStack() } }
        composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
    }
}
