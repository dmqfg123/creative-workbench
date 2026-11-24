package com.creativeworkbench.writer.ui

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.creativeworkbench.writer.ui.screens.DirectoryScreen
import com.creativeworkbench.writer.ui.screens.EditorScreen
import com.creativeworkbench.writer.ui.screens.LibraryScreen
import com.creativeworkbench.writer.ui.screens.MoreScreen
import com.creativeworkbench.writer.ui.screens.NotesScreen
import com.creativeworkbench.writer.ui.theme.CreativeWorkbenchTheme

@Composable
fun CreativeWorkbenchApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    LaunchedEffect(settings.notesEnabled) {
        if (!settings.notesEnabled && route == "notes") {
            navController.navigate("library") {
                popUpTo("library") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AppEvent.OpenEditor -> navController.navigate("editor/" + event.workId + "/" + event.chapterId)
                is AppEvent.Toast -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    CreativeWorkbenchTheme(settings) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (route == "library" || route == "notes" || route == "more") {
                    BottomBar(
                        navController = navController,
                        route = route,
                        notesEnabled = settings.notesEnabled
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "library",
                modifier = Modifier.padding(padding)
            ) {
                composable("library") {
                    LibraryScreen(
                        works = viewModel.works.collectAsStateWithLifecycle().value,
                        onCreateWork = viewModel::createWork,
                        onOpenWork = viewModel::openWork,
                        onOpenDirectory = { workId -> navController.navigate("directory/" + workId) },
                        onDeleteWork = viewModel::deleteWork
                    )
                }
                composable("notes") {
                    NotesScreen()
                }
                composable("more") {
                    MoreScreen(viewModel = viewModel, settings = settings)
                }
                composable("directory/{workId}") { entry ->
                    val workId = entry.arguments?.getString("workId").orEmpty()
                    DirectoryScreen(
                        workId = workId,
                        onOpenChapter = { chapterId ->
                            navController.navigate("editor/" + workId + "/" + chapterId)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("editor/{workId}/{chapterId}") { entry ->
                    val workId = entry.arguments?.getString("workId").orEmpty()
                    val chapterId = entry.arguments?.getString("chapterId").orEmpty()
                    EditorScreen(
                        workId = workId,
                        chapterId = chapterId,
                        onBack = { navController.popBackStack() },
                        onOpenDirectory = { navController.navigate("directory/" + workId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, route: String?, notesEnabled: Boolean) {
    NavigationBar {
        NavigationBarItem(
            selected = route == "library",
            onClick = { navController.navigateSingleTop("library") },
            icon = { Text("书", fontWeight = FontWeight.Bold) },
            label = { Text("书架") }
        )
        if (notesEnabled) {
            NavigationBarItem(
                selected = route == "notes",
                onClick = { navController.navigateSingleTop("notes") },
                icon = { Text("记", fontWeight = FontWeight.Bold) },
                label = { Text("笔记") }
            )
        }
        NavigationBarItem(
            selected = route == "more",
            onClick = { navController.navigateSingleTop("more") },
            icon = { Text("…", fontWeight = FontWeight.Bold) },
            label = { Text("更多") }
        )
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo("library")
        launchSingleTop = true
    }
}
