package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.AppViewModelProvider

@Composable
fun HomeScreen(
    onOpen: (ItemRef) -> Unit,
    onCreate: (ItemType) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.tabOrder.collectAsStateWithLifecycle()
    // Remember the tab itself, not its index, so reordering in Settings keeps the selection.
    // Null until the user picks one: the first tab of the stored order is then shown, even though
    // that order loads a moment after the first frame.
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = order.firstOrNull { it.name == selectedName } ?: order.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("xCommandCenter") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onCreate(selected) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "New ${selected.singular.lowercase()}")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = order.indexOf(selected), edgePadding = 8.dp) {
                order.forEach { tab ->
                    Tab(
                        selected = tab == selected,
                        onClick = { selectedName = tab.name },
                        text = { Text(tab.tabLabel) },
                    )
                }
            }
            when (selected) {
                ItemType.TASK -> TasksTab(viewModel, onOpen)
                ItemType.GOAL -> GoalsTab(viewModel, onOpen)
                ItemType.HABIT -> HabitsTab(viewModel, onOpen)
                ItemType.PROJECT -> ProjectsTab(viewModel, onOpen)
                ItemType.NOTE -> NotesTab(viewModel, onOpen)
                ItemType.BOOK -> BooksTab(viewModel, onOpen)
                ItemType.QUOTE -> QuotesTab(viewModel, onOpen)
                ItemType.CHAPTER -> Unit // Not a tab.
            }
        }
    }
}
