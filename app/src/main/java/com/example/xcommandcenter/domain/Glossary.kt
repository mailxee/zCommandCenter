package com.example.xcommandcenter.domain

/** The starting definitions of the app's terms; the user can edit them in Settings › Glossary. */
object Glossary {
    fun default(kind: IdeaKind): String = when (kind) {
        IdeaKind.VISION -> "A picture of the life or world you are aiming for. Long term; never \"done\"."
        IdeaKind.BELIEF -> "Something you hold to be true about how things work. It shapes your choices."
        IdeaKind.PRINCIPLE -> "A rule you choose to act by, usually drawn from a belief."
        IdeaKind.IDEA -> "A specific thought, approach or insight worth keeping and developing."
        IdeaKind.QUESTION -> "Something you are still thinking through, with no settled answer yet."
    }
}
