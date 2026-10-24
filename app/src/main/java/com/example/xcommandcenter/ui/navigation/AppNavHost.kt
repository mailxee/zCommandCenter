package com.example.xcommandcenter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.book.BookDetailScreen
import com.example.xcommandcenter.ui.book.ChapterDetailScreen
import com.example.xcommandcenter.ui.goal.GoalDetailScreen
import com.example.xcommandcenter.ui.habit.HabitDetailScreen
import com.example.xcommandcenter.ui.home.HomeScreen
import com.example.xcommandcenter.ui.note.NoteDetailScreen
import com.example.xcommandcenter.ui.project.ProjectDetailScreen
import com.example.xcommandcenter.ui.quote.QuoteDetailScreen
import com.example.xcommandcenter.ui.settings.SettingsScreen
import com.example.xcommandcenter.ui.task.TaskDetailScreen

/**
 * @param openTaskId a task to open, e.g. from a tapped reminder; [onTaskOpened] consumes it.
 */
@Composable
fun AppNavHost(
    openTaskId: Long?,
    onTaskOpened: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val open: (ItemRef) -> Unit = { navController.navigate(Routes.item(it)) }
    val back: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            navController.navigate(Routes.item(ItemRef(ItemType.TASK, openTaskId))) { launchSingleTop = true }
            onTaskOpened()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = open,
                onCreate = { type -> navController.navigate(Routes.newItem(type)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back)
        }
        composable(
            Routes.ITEM,
            arguments = listOf(
                navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                navArgument(Routes.ARG_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_PARENT) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val type = ItemType.valueOf(entry.arguments?.getString(Routes.ARG_TYPE) ?: ItemType.TASK.name)
            when (type) {
                ItemType.TASK -> TaskDetailScreen(onBack = back, onOpen = open)
                ItemType.GOAL -> GoalDetailScreen(onBack = back, onOpen = open)
                ItemType.HABIT -> HabitDetailScreen(onBack = back, onOpen = open)
                ItemType.PROJECT -> ProjectDetailScreen(
                    onBack = back,
                    onOpen = open,
                    onCreateChild = { parentId -> navController.navigate(Routes.newItem(ItemType.PROJECT, parentId)) },
                )
                ItemType.NOTE -> NoteDetailScreen(onBack = back, onOpen = open)
                ItemType.BOOK -> BookDetailScreen(onBack = back, onOpen = open)
                ItemType.CHAPTER -> ChapterDetailScreen(onBack = back, onOpen = open)
                ItemType.QUOTE -> QuoteDetailScreen(onBack = back, onOpen = open)
            }
        }
    }
}
