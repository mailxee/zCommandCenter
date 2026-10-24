package com.example.xcommandcenter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val tabOrderRepository: TabOrderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val order: StateFlow<List<ItemType>> = tabOrderRepository.order
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabOrder.default)

    fun move(from: Int, to: Int) {
        val updated = TabOrder.move(order.value, from, to)
        viewModelScope.launch { tabOrderRepository.setOrder(updated) }
    }

    fun reset() {
        viewModelScope.launch { tabOrderRepository.setOrder(TabOrder.default) }
    }

    fun canScheduleExactReminders(): Boolean = reminderScheduler.canScheduleExact()
}
