package com.example.xcommandcenter.domain

/**
 * What kind of thought an idea is. Stored by name, so renaming an entry needs a migration; the
 * order is the order of the Ideas tab's chips and the idea page's picker, broadest first.
 */
enum class IdeaKind(val label: String, val plural: String) {
    VISION("Vision", "Visions"),
    BELIEF("Belief", "Beliefs"),
    PRINCIPLE("Principle", "Principles"),
    IDEA("Idea", "Ideas"),
    QUESTION("Question", "Questions"),
}
