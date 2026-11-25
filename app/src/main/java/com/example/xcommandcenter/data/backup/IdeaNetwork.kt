package com.example.xcommandcenter.data.backup

import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import java.time.ZoneId

/**
 * A network of ideas as a file of its own (§2.78): an idea and everything under it, or every idea,
 * with their lists, tags, and the links between them and their labels. It is a workbook with the
 * backup's sheets for these (Ideas, Idea lists, Links, Link labels, Tags), so it can be edited in
 * Excel, and a full backup can be imported as one too (its ideas only). Importing adds copies with
 * new ids and never changes what is already there; see [com.example.xcommandcenter.data.dao.BackupDao.addIdeas].
 */
object IdeaNetwork {
    /** The sheets a network file has. */
    val SHEETS: List<String> = listOf(ItemType.IDEA.tabLabel, BackupFormat.IDEA_LISTS, BackupFormat.LINKS, BackupFormat.LINK_LABELS, BackupFormat.TAGS)

    /**
     * [rootId] and every idea under it (with null, every idea), [rootId] made top level; their
     * lists and tags, and the links that join two of them with the labels those use. Links to
     * anything else are left out.
     */
    fun select(data: BackupData, rootId: Long?): BackupData {
        val ids = if (rootId == null) data.ideas.map { it.id }.toSet() else PlanTree.subtreeIds(rootId, data.ideas, { it.id }, { it.parentId })
        val ideas = data.ideas.filter { it.id in ids }.map { if (it.id == rootId) it.copy(parentId = null) else it }
        val known = ideas.map { it.id }.toSet()
        val refs = known.map { ItemRef(ItemType.IDEA, it) }.toSet()
        val links = data.links.filter { ItemRef(it.aType, it.aId) in refs && ItemRef(it.bType, it.bId) in refs }
        val labelIds = links.mapNotNull { it.labelId }.toSet()
        val itemTags = data.itemTags.filter { it.type == ItemType.IDEA && it.itemId in known }
        val tagIds = itemTags.map { it.tagId }.toSet()
        return BackupData(
            ideas = ideas,
            ideaListItems = data.ideaListItems.filter { it.ideaId in known },
            links = links,
            linkLabels = data.linkLabels.filter { it.id in labelIds },
            tags = data.tags.filter { it.id in tagIds },
            itemTags = itemTags,
        )
    }

    /** [network] (from [select]) as the file's sheets. */
    fun toSheets(network: BackupData, zone: ZoneId = ZoneId.systemDefault()): List<Sheet> =
        BackupFormat.toSheets(network, listOf(ItemType.IDEA), zone).filter { it.name in SHEETS }

    /** What a file brings: its network, and how many rows were left out (links to items it doesn't have, and the like). */
    data class Read(val network: BackupData, val skipped: Int)

    /**
     * Reads a network file, or the ideas of a full backup. Only its [SHEETS] are read; links to
     * anything but its own ideas are left out and counted. An [ImportException] says what is
     * wrong with a file that can't be read.
     */
    fun fromSheets(given: List<Sheet>, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Read {
        val wanted = SHEETS.map { it.lowercase() }.toSet()
        val ours = given.filter { it.name.trim().lowercase() in wanted }
        if (ours.none { it.name.trim().equals(ItemType.IDEA.tabLabel, ignoreCase = true) }) {
            throw ImportException("This file has no \"${ItemType.IDEA.tabLabel}\" sheet, so it holds no ideas. Nothing was imported.")
        }
        // The backup reader needs every tab's sheet: the others come in empty.
        val have = ours.map { it.name.trim().lowercase() }.toSet()
        val tabNames = ItemType.tabs.map { it.tabLabel }.toSet()
        val blanks = BackupFormat.toSheets(BackupData(), ItemType.tabs, zone)
            .filter { it.name in tabNames && it.name.lowercase() !in have }
        val result = BackupFormat.fromSheets(ours + blanks, zone, now)
        val network = select(result.data, null)
        if (network.ideas.isEmpty()) throw ImportException("This file's \"${ItemType.IDEA.tabLabel}\" sheet has no ideas. Nothing was imported.")
        val dropped = (result.data.links.size - network.links.size) + (result.data.itemTags.size - network.itemTags.size)
        return Read(network, result.skipped + dropped)
    }

    /**
     * Which of [existing] labels an imported [label] is: the same key (a starting label such as
     * "contradicts"), or else the same wording both ways, ignoring case. Null means it is new.
     */
    fun sameLabel(label: LinkLabelEntity, existing: List<LinkLabelEntity>): LinkLabelEntity? {
        label.key?.let { key -> existing.firstOrNull { it.key == key }?.let { return it } }
        fun words(s: String?) = s?.trim()?.lowercase().orEmpty()
        return existing.firstOrNull { words(it.forward) == words(label.forward) && words(it.backward) == words(label.backward) }
    }

    /** What an import added, for the message after it. */
    data class Added(val ideas: Int, val links: Int, val newLabels: Int, val skipped: Int = 0) {
        val message: String
            get() = buildString {
                append(count(ideas, "idea"))
                append(" and ")
                append(count(links, "link"))
                append(" added")
                if (newLabels > 0) append(", with ${count(newLabels, "new link label")}")
                append(".")
                if (skipped > 0) append(" ${count(skipped, "row")} pointing at items outside the network ${if (skipped == 1) "was" else "were"} left out.")
            }

        private fun count(n: Int, what: String) = if (n == 1) "1 $what" else "$n ${what}s"
    }
}

/** File names for network files. */
object IdeaNetworkFiles {
    /** "Ideas-<title>-<date>.xlsx" with characters file systems refuse taken out; "Ideas-<date>.xlsx" for every idea. */
    fun name(title: String?, date: java.time.LocalDate = java.time.LocalDate.now()): String {
        val safe = title.orEmpty().replace(Regex("[\\\\/:*?\"<>|\\s]+"), " ").trim().take(40).replace(' ', '-')
        return if (safe.isEmpty()) "Ideas-$date.xlsx" else "Ideas-$safe-$date.xlsx"
    }
}
