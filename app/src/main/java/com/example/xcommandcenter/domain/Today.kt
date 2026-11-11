package com.example.xcommandcenter.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * Today's date, checked each minute, for anything shown that depends on the day (past due tasks,
 * habits due today, today's reminders, the active schedule), so it moves on at midnight even with
 * the app left open and nothing edited.
 */
val today: Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000)
    }
}.distinctUntilChanged()
