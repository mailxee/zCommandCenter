package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.LinkAnalysis.Finding
import com.example.xcommandcenter.domain.LinkAnalysis.Kind
import com.example.xcommandcenter.domain.LinkAnalysis.Link

/**
 * Tidy links for Think mode. Loops between ideas are normal there (ideas cross-reference each
 * other), so none are reported; instead, suggestions that help the thinking hold together:
 *
 * - [Kind.AUTHOR_IS_PERSON]: a quote whose author is someone in People it isn't linked to; the fix
 *   links the person as its source.
 * - [Kind.UNPLACED_QUOTE]: a quote linked to no idea.
 * - [Kind.UNGROUNDED_IDEA]: a belief, principle or idea with no quote, book, person or note behind it.
 * - [Kind.BELIEF_NOT_ACTED]: a belief or principle that leads to nothing in Do mode (no goal, plan,
 *   task or habit), not even through the ideas under it.
 * - [Kind.CONTRADICTION]: each "contradicts" link, as a note to come back to.
 *
 * And one for Do mode, [Kind.GOAL_WITHOUT_WHY] ([goalsWithoutWhy]): a goal no idea links to.
 */
object ThinkAnalysis {
    /** An idea's place in the tree and its kind. */
    data class Idea(val id: Long, val parentId: Long?, val kind: IdeaKind)

    private val sources = setOf(ItemType.QUOTE, ItemType.BOOK, ItemType.CHAPTER, ItemType.PERSON, ItemType.NOTE)
    private val doing = setOf(ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT)

    /**
     * [authors] maps each quote to its author's name (quotes without one are left out); [people]
     * maps each person to their name.
     */
    fun analyze(
        links: List<Link>,
        ideas: List<Idea>,
        quotes: Set<Long>,
        authors: Map<Long, String>,
        people: Map<Long, String>,
        kept: Set<String> = emptySet(),
    ): LinkAnalysis.Result {
        val neighbours = HashMap<ItemRef, MutableSet<ItemRef>>()
        links.forEach {
            neighbours.getOrPut(it.a) { mutableSetOf() } += it.b
            neighbours.getOrPut(it.b) { mutableSetOf() } += it.a
        }
        fun linked(ref: ItemRef) = neighbours[ref].orEmpty()
        val found = mutableListOf<Finding>()

        val personByName = people.entries.groupBy({ it.value.trim().lowercase() }, { it.key })
        authors.forEach { (quoteId, author) ->
            val quote = ItemRef(ItemType.QUOTE, quoteId)
            val person = personByName[author.trim().lowercase()]?.singleOrNull()?.let { ItemRef(ItemType.PERSON, it) } ?: return@forEach
            if (person !in linked(quote)) {
                found += Finding("author:$quoteId:${person.id}", Kind.AUTHOR_IS_PERSON, null, endPoint = quote, add = person to quote)
            }
        }
        quotes.sorted().forEach { id ->
            val quote = ItemRef(ItemType.QUOTE, id)
            if (linked(quote).none { it.type == ItemType.IDEA }) found += Finding("quote:$id", Kind.UNPLACED_QUOTE, null, endPoint = quote)
        }
        val children = ideas.groupBy { it.parentId }
        fun below(id: Long): List<Idea> = children[id].orEmpty().flatMap { listOf(it) + below(it.id) }
        ideas.sortedBy { it.id }.forEach { idea ->
            val ref = ItemRef(ItemType.IDEA, idea.id)
            if (idea.kind in setOf(IdeaKind.BELIEF, IdeaKind.PRINCIPLE, IdeaKind.IDEA) && linked(ref).none { it.type in sources }) {
                found += Finding("ground:${idea.id}", Kind.UNGROUNDED_IDEA, null, endPoint = ref)
            }
            if (idea.kind in setOf(IdeaKind.BELIEF, IdeaKind.PRINCIPLE)) {
                val acted = (listOf(idea) + below(idea.id)).any { i -> linked(ItemRef(ItemType.IDEA, i.id)).any { it.type in doing } }
                if (!acted) found += Finding("act:${idea.id}", Kind.BELIEF_NOT_ACTED, null, endPoint = ref)
            }
        }
        links.filter { it.contradiction }.forEach { found += Finding("contra:${it.id}", Kind.CONTRADICTION, it) }

        val order = compareBy<Finding>({ it.kind.ordinal }, { it.key })
        val (open, keptFindings) = found.distinctBy { it.key }.sortedWith(order).partition { it.key !in kept }
        return LinkAnalysis.Result(open, keptFindings)
    }

    /** Do mode's check: goals that no idea links to, so nothing says why they matter. */
    fun goalsWithoutWhy(links: List<Link>, goals: Set<Long>): List<Finding> {
        val withWhy = links.flatMap { listOf(it.a to it.b, it.b to it.a) }
            .filter { (goal, other) -> goal.type == ItemType.GOAL && other.type == ItemType.IDEA }
            .map { it.first.id }.toSet()
        return goals.sorted().filter { it !in withWhy }.map { Finding("why:$it", Kind.GOAL_WITHOUT_WHY, null, endPoint = ItemRef(ItemType.GOAL, it)) }
    }
}
