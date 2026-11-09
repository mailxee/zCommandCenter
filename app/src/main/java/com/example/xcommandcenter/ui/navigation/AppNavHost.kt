package com.example.xcommandcenter.ui.navigation

import com.example.xcommandcenter.ui.person.PersonDetailScreen
import com.example.xcommandcenter.ui.idea.IdeaDetailScreen
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
import com.example.xcommandcenter.ui.block.BlockDetailScreen
import com.example.xcommandcenter.ui.book.BookDetailScreen
import com.example.xcommandcenter.ui.book.ChapterDetailScreen
import com.example.xcommandcenter.ui.goal.GoalDetailScreen
import com.example.xcommandcenter.ui.habit.HabitDetailScreen
import com.example.xcommandcenter.ui.home.HomeScreen
import com.example.xcommandcenter.ui.links.MapScreen
import com.example.xcommandcenter.ui.links.TidyScreen
import com.example.xcommandcenter.ui.note.NoteDetailScreen
import com.example.xcommandcenter.ui.plan.PlanDetailScreen
import com.example.xcommandcenter.ui.quote.QuoteDetailScreen
import com.example.xcommandcenter.ui.settings.SettingsScreen
import com.example.xcommandcenter.ui.task.TaskDetailScreen

/**
 * @param openItem an item to open, e.g. from a tapped reminder; [onItemOpened] consumes it.
 */
@Composable
fun AppNavHost(
    openItem: ItemRef?,
    onItemOpened: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val open: (ItemRef) -> Unit = { navController.navigate(Routes.item(it)) }
    val back: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(openItem) {
        if (openItem != null) {
            navController.navigate(Routes.item(openItem)) { launchSingleTop = true }
            onItemOpened()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = open,
                onCreate = { type -> navController.navigate(Routes.newItem(type)) },
                onCreateBlock = { minute -> navController.navigate(Routes.newBlock(minute)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onMap = { root -> navController.navigate(Routes.map(root)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onTidy = { navController.navigate(Routes.tidy(null)) })
        }
        composable(
            Routes.MAP,
            arguments = listOf(
                navArgument(Routes.ARG_ROOT) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_FOCUS) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            MapScreen(onBack = back, onOpen = open, onTidy = { root -> navController.navigate(Routes.tidy(root)) })
        }
        composable(
            Routes.TIDY,
            arguments = listOf(navArgument(Routes.ARG_ROOT) { type = NavType.StringType; defaultValue = "" }),
        ) {
            TidyScreen(onBack = back, onShowOnMap = { root, focus -> navController.navigate(Routes.map(root, focus)) })
        }
        composable(
            Routes.ITEM,
            arguments = listOf(
                navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                navArgument(Routes.ARG_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_PARENT) { type = NavType.LongType; defaultValue = 0L },
                navArgument(Routes.ARG_START) { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val type = ItemType.valueOf(entry.arguments?.getString(Routes.ARG_TYPE) ?: ItemType.TASK.name)
            when (type) {
                ItemType.TASK -> TaskDetailScreen(onBack = back, onOpen = open)
                ItemType.GOAL -> GoalDetailScreen(onBack = back, onOpen = open)
                ItemType.HABIT -> HabitDetailScreen(onBack = back, onOpen = open)
                ItemType.PLAN -> PlanDetailScreen(
                    onBack = back,
                    onOpen = open,
                    onCreateChild = { parentId -> navController.navigate(Routes.newItem(ItemType.PLAN, parentId)) },
                )
                ItemType.NOTE -> NoteDetailScreen(
                    onBack = back,
                    onOpen = open,
                    onTurnedIntoIdea = { ref ->
                        // The note is gone: its page gives way to the idea's.
                        navController.popBackStack()
                        open(ref)
                    },
                )
                ItemType.BOOK -> BookDetailScreen(onBack = back, onOpen = open)
                ItemType.CHAPTER -> ChapterDetailScreen(onBack = back, onOpen = open)
                ItemType.QUOTE -> QuoteDetailScreen(onBack = back, onOpen = open)
                ItemType.BLOCK -> BlockDetailScreen(onBack = back, onOpen = open)
                ItemType.PERSON -> PersonDetailScreen(onBack = back, onOpen = open)
                ItemType.IDEA -> IdeaDetailScreen(
                    onBack = back,
                    onOpen = open,
                    onCreateChild = { parentId -> navController.navigate(Routes.newItem(ItemType.IDEA, parentId)) },
                )
            }
        }
    }
}
