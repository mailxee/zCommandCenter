package com.example.xcommandcenter.domain

/** Parsing and editing of the user's tab order, kept independent of how it is stored. */
object TabOrder {
    /** Until the user reorders: the day's plan first, then what it serves and what fills it. */
    val default: List<ItemType> = listOf(
        ItemType.BLOCK, ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE,
    )

    /** Unknown names are dropped and tabs missing from [stored] are appended in default order. */
    fun parse(stored: String?): List<ItemType> {
        val known = stored.orEmpty().split(',')
            .mapNotNull { name -> ItemType.tabs.firstOrNull { it.name == name } }
            .distinct()
        return known + default.filterNot { it in known }
    }

    fun serialize(order: List<ItemType>): String = order.joinToString(",") { it.name }

    fun move(order: List<ItemType>, from: Int, to: Int): List<ItemType> {
        if (from !in order.indices || to !in order.indices) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
