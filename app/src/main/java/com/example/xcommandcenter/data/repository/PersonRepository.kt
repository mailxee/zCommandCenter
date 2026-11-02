package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.PersonDao
import com.example.xcommandcenter.data.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A person's profile addresses and item texts, in order. */
data class PersonLists(val profiles: List<String> = emptyList(), val items: List<String> = emptyList())

class PersonRepository(private val dao: PersonDao, private val reminders: ReminderRepository) {
    fun observeAll(): Flow<List<PersonEntity>> = dao.observeAll()

    suspend fun get(id: Long): PersonEntity? = dao.get(id)

    suspend fun lists(personId: Long): PersonLists =
        PersonLists(dao.profiles(personId).map { it.url }, dao.items(personId).map { it.text })

    /** Every person's profiles and items by person id, for the cards and the search. */
    fun observeLists(): Flow<Map<Long, PersonLists>> = combine(dao.observeAllProfiles(), dao.observeAllItems()) { profiles, items ->
        val byPersonProfiles = profiles.groupBy({ it.personId }, { it.url })
        val byPersonItems = items.groupBy({ it.personId }, { it.text })
        (byPersonProfiles.keys + byPersonItems.keys).associateWith {
            PersonLists(byPersonProfiles[it].orEmpty(), byPersonItems[it].orEmpty())
        }
    }

    /** A new person gets a place in the list; an edited one keeps its place. [lists] become the person's lists. */
    suspend fun save(person: PersonEntity, lists: PersonLists): Long {
        val id = if (person.id == 0L) {
            dao.insert(person.copy(sortOrder = dao.newSortOrder()))
        } else {
            person.id.also { dao.update(person.copy(sortOrder = dao.get(person.id)?.sortOrder ?: person.sortOrder)) }
        }
        dao.replaceLists(id, lists.profiles, lists.items)
        return id
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Also removes the reminders of what was deleted. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }
}
