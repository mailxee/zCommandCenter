package com.example.xcommandcenter.domain

/**
 * The app's terms and what they mean: a starting definition for every idea kind, item type and
 * starting link label, which the user can rewrite in Settings › Glossary. Only rewritten
 * definitions are stored, by [kindKey], [typeKey] or [labelKey]; [Terms] puts the two together.
 */
object Glossary {
    fun kindKey(kind: IdeaKind) = "KIND:${kind.name}"
    fun typeKey(type: ItemType) = "TYPE:${type.name}"
    fun labelKey(labelId: Long) = "LABEL:$labelId"

    fun default(kind: IdeaKind): String = when (kind) {
        IdeaKind.VISION -> "A picture of the life or world you are aiming for. Long term; never \"done\"."
        IdeaKind.BELIEF -> "Something you hold to be true about how things work. It shapes your choices."
        IdeaKind.PRINCIPLE -> "A rule you choose to act by, usually drawn from a belief."
        IdeaKind.IDEA -> "A specific thought, approach or insight worth keeping and developing."
        IdeaKind.QUESTION -> "Something you are still thinking through, with no settled answer yet."
    }

    fun default(type: ItemType): String = when (type) {
        ItemType.GOAL -> "An outcome you want to reach and can say is achieved."
        ItemType.PLAN -> "A structured way to get somewhere, broken into subplans and tasks."
        ItemType.TASK -> "A single action that can be done and ticked off."
        ItemType.HABIT -> "Something done repeatedly to build or keep a practice, or to break one."
        ItemType.BLOCK -> "Time set aside in the day's schedule."
        ItemType.NOTE -> "Working text: drafts, lists and raw thoughts."
        ItemType.BOOK -> "A book you are reading or have read, with its chapters and progress."
        ItemType.CHAPTER -> "Part of a book, which can be linked on its own."
        ItemType.QUOTE -> "Words worth keeping, with their author and source."
        ItemType.PERSON -> "Someone whose thinking, work or company matters to you."
        ItemType.IDEA -> "A thought worth keeping: a vision, belief, principle, idea or question, nested under broader ones."
    }

    /** The starting labels' definitions, by their key; labels the user adds start with none. */
    fun defaultForLabel(key: String?): String = when (key) {
        "SUPPORTS" -> "Gives reason or evidence for the other item."
        LinkLabels.CONTRADICTS -> "The two can't both hold as they stand; worth thinking through."
        "INSPIRED" -> "Gave rise to the other item, or shaped it."
        LinkLabels.LEADS_TO -> "Turns into the other item in practice: a principle into a plan or habit."
        "EXAMPLE" -> "A concrete case of the other item."
        LinkLabels.SOURCE -> "Where the other item comes from: its author, book or note."
        "DEPENDS" -> "Can't be done or hold without the other item."
        else -> ""
    }

    /** The definitions in use: rewritten ones from [edited], the starting ones otherwise. */
    data class Terms(val edited: Map<String, String> = emptyMap()) {
        fun of(kind: IdeaKind): String = edited[kindKey(kind)] ?: default(kind)
        fun of(type: ItemType): String = edited[typeKey(type)] ?: default(type)
        fun ofLabel(labelId: Long, key: String?): String = edited[labelKey(labelId)] ?: defaultForLabel(key)
        fun isEdited(key: String): Boolean = key in edited
    }
}
