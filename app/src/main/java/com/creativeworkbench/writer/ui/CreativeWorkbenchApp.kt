package com.creativeworkbench.writer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import com.creativeworkbench.writer.ui.theme.Motion

@Composable
fun CreativeWorkbenchApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appState by viewModel.appState.collectAsStateWithLifecycle()
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
                modifier = Modifier.padding(padding),
                // 同级页面之间（书架/笔记/更多）用淡入+微缩放，不假装有前后层级
                enterTransition = {
                    fadeIn(tween(Motion.Medium, easing = Motion.Standard)) +
                        scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(Motion.Medium, easing = Motion.Standard)
                        )
                },
                exitTransition = {
                    fadeOut(tween(Motion.Fast)) +
                        scaleOut(targetScale = 0.99f, animationSpec = tween(Motion.Fast))
                },
                popEnterTransition = {
                    fadeIn(tween(Motion.Medium)) +
                        scaleIn(initialScale = 0.99f, animationSpec = tween(Motion.Medium))
                },
                popExitTransition = { fadeOut(tween(Motion.Fast)) }
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
                composable(
                    "directory/{workId}",
                    // 进入下一层：从右侧滑入，旧页面轻微左移，符合"推开一层"的直觉
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(Motion.Slow, easing = Motion.EmphasizedDecelerate)
                        ) { full -> full } + fadeIn(tween(200))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)
                        ) { full -> -full / 4 } + fadeOut(tween(200))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                        ) { full -> -full / 4 } + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(Motion.Slow, easing = Motion.EmphasizedAccelerate)
                        ) { full -> full } + fadeOut(tween(200))
                    }
                ) { entry ->
                    val workId = entry.arguments?.getString("workId").orEmpty()
                    DirectoryScreen(
                        workId = workId,
                        currentChapterId = appState.currentChapterId,
                        onOpenChapter = { chapterId ->
                            viewModel.setCurrentChapter(workId, chapterId)
                            navController.navigate("editor/" + workId + "/" + chapterId)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "editor/{workId}/{chapterId}",
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(Motion.Slow, easing = Motion.EmphasizedDecelerate)
                        ) { full -> full } + fadeIn(tween(200))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)
                        ) { full -> -full / 4 } + fadeOut(tween(200))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                        ) { full -> -full / 4 } + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(Motion.Slow, easing = Motion.EmphasizedAccelerate)
                        ) { full -> full } + fadeOut(tween(200))
                    }
                ) { entry ->
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
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("书架") }
        )
        if (notesEnabled) {
            NavigationBarItem(
                selected = route == "notes",
                onClick = { navController.navigateSingleTop("notes") },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                label = { Text("笔记") }
            )
        }
        NavigationBarItem(
            selected = route == "more",
            onClick = { navController.navigateSingleTop("more") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
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
