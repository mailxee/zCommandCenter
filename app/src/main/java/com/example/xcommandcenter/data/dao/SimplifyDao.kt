package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

/**
 * Settings › Simplify: turns every goal into a top-level Vision in Ideas, keeping its title,
 * strategy (as the explanation), priority, creation time, links (with their labels, read the same
 * way), tags and reminders. The goals are then gone. One transaction, so it happens whole or not at all.
 */
@Dao
abstract class SimplifyDao {
    @Query("SELECT COUNT(*) FROM goals")
    abstract fun observeGoalCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM goals ORDER BY sortOrder, createdAt")
    abstract suspend fun goals(): List<GoalEntity>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM ideas")
    abstract suspend fun nextIdeaOrder(): Int

    @Insert
    abstract suspend fun insertIdea(idea: IdeaEntity): Long

    @Query("SELECT * FROM links WHERE aType = 'GOAL' OR bType = 'GOAL'")
    abstract suspend fun goalLinks(): List<LinkEntity>

    @Query("DELETE FROM links WHERE id = :id")
    abstract suspend fun deleteLink(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLink(link: LinkEntity): Long

    @Query("UPDATE item_tags SET type = 'IDEA', itemId = :ideaId WHERE type = 'GOAL' AND itemId = :goalId")
    abstract suspend fun moveTags(goalId: Long, ideaId: Long)

    @Query("UPDATE reminders SET itemType = 'IDEA', itemId = :ideaId WHERE itemType = 'GOAL' AND itemId = :goalId")
    abstract suspend fun moveReminders(goalId: Long, ideaId: Long)

    @Query("DELETE FROM goals")
    abstract suspend fun deleteGoals()

    /** Moves every goal; returns each goal's new idea, by goal id, in the goals' order. */
    @Transaction
    open suspend fun goalsToVisions(): Map<Long, Long> {
        val goals = goals()
        if (goals.isEmpty()) return emptyMap()
        val first = nextIdeaOrder()
        val ideaOf = LinkedHashMap<Long, Long>()
        goals.forEachIndexed { i, goal ->
            ideaOf[goal.id] = insertIdea(
                IdeaEntity(
                    title = goal.title, body = goal.strategy, kind = IdeaKind.VISION, parentId = null,
                    createdAt = goal.createdAt, sortOrder = first + i, priority = goal.priority,
                ),
            )
        }
        fun moved(ref: ItemRef) = if (ref.type == ItemType.GOAL) ItemRef(ItemType.IDEA, ideaOf.getValue(ref.id)) else ref
        goalLinks().forEach { link ->
            deleteLink(link.id)
            remap(link, ::moved)?.let { insertLink(it) }
        }
        ideaOf.forEach { (goal, idea) ->
            moveTags(goal, idea)
            moveReminders(goal, idea)
        }
        deleteGoals()
        return ideaOf
    }

    companion object {
        /**
         * [link] between the items [moved] gives for its ends, normalized again (the ends may now sort
         * the other way), with its label still read from the same item and its creation time kept;
         * null if both ends became one item.
         */
        fun remap(link: LinkEntity, moved: (ItemRef) -> ItemRef): LinkEntity? {
            val a = moved(ItemRef(link.aType, link.aId))
            val b = moved(ItemRef(link.bType, link.bId))
            if (a == b) return null
            val (subject, obj) = if (link.labelFromA) a to b else b to a
            return LinkEntity.labelled(subject, obj, link.labelId).copy(createdAt = link.createdAt)
        }
    }
}
