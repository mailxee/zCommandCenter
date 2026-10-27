package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import java.time.LocalTime

/**
 * One reminder on any item; an item can have any number. For a habit, [time] is the minute of the
 * day (0..1439) it reminds on each scheduled day; for any other item, the epoch millis it fires at,
 * once. Reminders go with their item: deleting the item removes them (see `ReminderRepository`).
 */
@Entity(tableName = "reminders", indices = [Index("itemType", "itemId")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: ItemType,
    val itemId: Long,
    val time: Long,
) {
    val ref: ItemRef get() = ItemRef(itemType, itemId)

    /** For a habit's reminder. */
    val timeOfDay: LocalTime get() = LocalTime.of((time / 60).toInt(), (time % 60).toInt())
}
