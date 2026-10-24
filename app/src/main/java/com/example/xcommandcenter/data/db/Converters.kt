package com.example.xcommandcenter.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/** Enums need no converter: Room stores them by name. */
class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
