package com.example.xcommandcenter.ui.person

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.PersonEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.PersonLists
import com.example.xcommandcenter.data.repository.PersonRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Profiles
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.common.ListLine
import com.example.xcommandcenter.ui.common.ListLines
import kotlinx.coroutines.launch

class PersonDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PersonRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
    reminders: ReminderRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.PERSON, links, tags, cards, reminders) {

    var person by mutableStateOf(PersonEntity())
        private set

    /** The person's profile addresses, edited here and saved with the person. */
    var profiles by mutableStateOf<List<ListLine>>(emptyList())
        private set

    /** Their ideas, videos and posts, edited here and saved with the person. */
    var items by mutableStateOf<List<ListLine>>(emptyList())
        private set

    private val lines = ListLines()

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            person = repository.get(id) ?: return@launch onMissing()
            val stored = repository.lists(id)
            profiles = stored.profiles.map { lines.line(it) }
            items = stored.items.map { lines.line(it) }
            markClean()
        }
        markClean()
    }

    override fun formState(): Any = Triple(person, profiles, items)

    fun update(transform: (PersonEntity) -> PersonEntity) {
        person = transform(person)
    }

    /** "@name" and "youtube @name" become profile addresses as they are added. */
    fun addProfile(text: String) {
        profiles = lines.add(profiles, Profiles.normalize(text))
    }

    fun updateProfile(key: Long, transform: (ListLine) -> ListLine) {
        profiles = lines.update(profiles, key, transform)
    }

    fun removeProfile(key: Long) {
        profiles = lines.remove(profiles, key)
    }

    fun moveProfile(from: Int, to: Int) {
        profiles = lines.move(profiles, from, to)
    }

    fun addItem(text: String) {
        items = lines.add(items, text)
    }

    fun updateItem(key: Long, transform: (ListLine) -> ListLine) {
        items = lines.update(items, key, transform)
    }

    fun removeItem(key: Long) {
        items = lines.remove(items, key)
    }

    fun moveItem(from: Int, to: Int) {
        items = lines.move(items, from, to)
    }

    fun save(close: Boolean) {
        if (person.name.isBlank()) {
            message = "The name is required"
            return
        }
        val toSave = person.copy(id = itemId.value, name = person.name.trim(), bio = person.bio.trim())
        val lists = PersonLists(
            profiles = profiles.map { Profiles.normalize(it.text) }.filter { it.isNotEmpty() },
            items = items.map { it.text.trim() }.filter { it.isNotEmpty() },
        )
        viewModelScope.launch { onSaved(repository.save(toSave, lists), close) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
