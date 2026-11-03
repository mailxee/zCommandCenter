package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import java.time.LocalTime

/**
 * One reminder on any item; an item can have any number. For a habit, [time] is the minute of the
 * day (0..1439) it reminds on each scheduled day, and for a schedule block the minute it reminds
 * every day; for any other item, the epoch millis it fires at, once. Reminders go with their item: deleting the item removes them (see `ReminderRepository`).
 */
@Entity(tableName = "reminders", indices = [Index("itemType", "itemId")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: ItemType,
    val itemId: Long,
    val time: Long,
    /**
     * Added by carry-over (see `ReminderRepository.carryOver`): when a task's or plan's reminder went
     * off and the item is still open with nothing later, this one repeats it some days on. An item
     * has at most one; it moves forward each time it goes off, and goes once the item is completed.
     */
    @ColumnInfo(defaultValue = "0") val carried: Boolean = false,
) {
    val ref: ItemRef get() = ItemRef(itemType, itemId)

    /** For a habit's or a block's reminder. */
    val timeOfDay: LocalTime get() = LocalTime.of((time / 60).toInt(), (time % 60).toInt())
}
