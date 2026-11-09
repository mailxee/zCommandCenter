package com.example.xcommandcenter.ui

import com.example.xcommandcenter.ui.person.PersonDetailViewModel
import com.example.xcommandcenter.ui.idea.IdeaDetailViewModel
import com.example.xcommandcenter.ui.settings.GlossaryViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.xcommandcenter.AppContainer
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.ui.block.BlockDetailViewModel
import com.example.xcommandcenter.ui.book.BookDetailViewModel
import com.example.xcommandcenter.ui.book.ChapterDetailViewModel
import com.example.xcommandcenter.ui.goal.GoalDetailViewModel
import com.example.xcommandcenter.ui.habit.HabitDetailViewModel
import com.example.xcommandcenter.ui.home.HomeViewModel
import com.example.xcommandcenter.ui.links.MapViewModel
import com.example.xcommandcenter.ui.links.TidyViewModel
import com.example.xcommandcenter.ui.note.NoteDetailViewModel
import com.example.xcommandcenter.ui.plan.PlanDetailViewModel
import com.example.xcommandcenter.ui.quote.QuoteDetailViewModel
import com.example.xcommandcenter.ui.settings.SettingsViewModel
import com.example.xcommandcenter.ui.task.TaskDetailViewModel

/** Builds every ViewModel from the [AppContainer]; pass to `viewModel(factory = ...)`. */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { with(container()) { HomeViewModel(this) } }
        initializer { with(container()) { GlossaryViewModel(glossaryRepository, linkRepository) } }
        initializer { with(container()) { SettingsViewModel(tabOrderRepository, itemStyleRepository, reminderScheduler, backupRepository, tagRepository, reminderPrefsRepository, routePrefsRepository, linkRepository, modeRepository) } }
        initializer { with(container()) { MapViewModel(createSavedStateHandle(), linkRepository) } }
        initializer { with(container()) { TidyViewModel(createSavedStateHandle(), linkRepository) } }
        initializer { with(container()) { TaskDetailViewModel(createSavedStateHandle(), taskRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { GoalDetailViewModel(createSavedStateHandle(), goalRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { HabitDetailViewModel(createSavedStateHandle(), habitRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { PlanDetailViewModel(createSavedStateHandle(), planRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { NoteDetailViewModel(createSavedStateHandle(), noteRepository, ideaRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { IdeaDetailViewModel(createSavedStateHandle(), ideaRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { BookDetailViewModel(createSavedStateHandle(), bookRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { ChapterDetailViewModel(createSavedStateHandle(), bookRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { PersonDetailViewModel(createSavedStateHandle(), personRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { QuoteDetailViewModel(createSavedStateHandle(), quoteRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
        initializer { with(container()) { BlockDetailViewModel(createSavedStateHandle(), blockRepository, scheduleRepository, linkRepository, tagRepository, itemCardSource, reminderRepository) } }
    }

    private fun CreationExtras.container(): AppContainer =
        (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as XCommandCenterApp).container
}
