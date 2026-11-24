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
import com.creativeworkbench.writer.ui.screens.AiHistoryScreen
import com.creativeworkbench.writer.ui.screens.AiSettingsScreen
import com.creativeworkbench.writer.ui.screens.DataSettingsScreen
import com.creativeworkbench.writer.ui.screens.DirectoryScreen
import com.creativeworkbench.writer.ui.screens.ModulesScreen
import com.creativeworkbench.writer.ui.screens.EditorScreen
import com.creativeworkbench.writer.ui.screens.LibraryScreen
import com.creativeworkbench.writer.ui.screens.MoreScreen
import com.creativeworkbench.writer.ui.screens.NoteDetailScreen
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

    // 崩溃日志要记录「当时在哪个页面」
    LaunchedEffect(route) {
        com.creativeworkbench.writer.core.common.Breadcrumbs.page(
            when {
                route == null -> "启动中"
                route == "library" -> "书架"
                route == "notes" -> "笔记列表"
                route == "more" -> "更多"
                route?.startsWith("note/") == true -> "笔记详情"
                route?.startsWith("directory/") == true -> "作品目录"
                route?.startsWith("editor/") == true -> "编辑器"
                route == "more/ai" -> "AI 设置"
                route == "more/ai-history" -> "AI 调用历史"
                route == "more/modules" -> "模块与扩展"
                route == "more/data" -> "数据与备份"
                else -> route.orEmpty()
            }
        )
    }

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
                        sort = settings.librarySort,
                        onCreateWork = viewModel::createWork,
                        onOpenWork = viewModel::openWork,
                        onOpenDirectory = { workId -> navController.navigate("directory/" + workId) },
                        onRenameWork = viewModel::renameWork,
                        onScheduleDelete = viewModel::scheduleDeleteWork,
                        onCancelDelete = viewModel::cancelScheduledDelete,
                        onSaveQuickNote = { kind, content, tag ->
                            viewModel.saveQuickNote(kind, null, content, appState.currentWorkId)
                        },
                        onOpenNotes = { filter ->
                            viewModel.requestNotesFilter(filter)
                            navController.navigateSingleTop("notes")
                        },
                        onOpenLibrary = { navController.navigateSingleTop("library") },
                        onOpenSettings = { navController.navigateSingleTop("more") },
                        onSortChange = viewModel::setLibrarySort
                    )
                }
                composable("notes") {
                    NotesScreen(
                        notes = viewModel.notes.collectAsStateWithLifecycle().value,
                        works = viewModel.works.collectAsStateWithLifecycle().value,
                        currentWorkId = viewModel.appState.collectAsStateWithLifecycle().value.currentWorkId,
                        pendingFilter = viewModel.pendingNotesFilter.collectAsStateWithLifecycle().value,
                        onFilterConsumed = viewModel::consumeNotesFilter,
                        onOpenNote = { noteId -> navController.navigate("note/" + noteId + "/" + "") },
                        onNewNote = { kind -> navController.navigate("note/new/" + kind) },
                        onOpenLibrary = { navController.navigateSingleTop("library") }
                    )
                }
                composable("note/{noteId}/{kind}") { entry ->
                    val noteId = entry.arguments?.getString("noteId").orEmpty()
                    val kind = entry.arguments?.getString("kind").orEmpty().ifBlank { "idea" }
                    NoteDetailScreen(
                        noteId = noteId,
                        workId = viewModel.appState.collectAsStateWithLifecycle().value.currentWorkId,
                        kind = kind,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("more") {
                    MoreScreen(
                        viewModel = viewModel,
                        settings = settings,
                        onOpenAi = { navController.navigate("more/ai") },
                        onOpenAiHistory = { navController.navigate("more/ai-history") },
                        onOpenModules = { navController.navigate("more/modules") },
                        onOpenData = { navController.navigate("more/data") }
                    )
                }
                composable("more/ai") { AiSettingsScreen(onBack = { navController.popBackStack() }) }
                composable("more/ai-history") { AiHistoryScreen(onBack = { navController.popBackStack() }) }
                composable("more/modules") {
                    ModulesScreen(
                        notesEnabled = settings.notesEnabled,
                        onToggleNotes = viewModel::setNotesEnabled,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("more/data") { DataSettingsScreen(onBack = { navController.popBackStack() }) }
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
                        notes = viewModel.notes.collectAsStateWithLifecycle().value,
                        notesEnabled = settings.notesEnabled,
                        onBack = { navController.popBackStack() },
                        onOpenDirectory = {
                            // 从编辑器去目录：把编辑器从返回栈里换掉，避免同一章在栈里留两份
                            navController.navigate("directory/" + workId) {
                                popUpTo("editor/{workId}/{chapterId}") { inclusive = true }
                            }
                        }
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