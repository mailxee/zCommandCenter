package com.example.xcommandcenter.domain

/** Colors an item style can use; the UI maps each to a shade that reads well in light and dark themes. */
enum class StyleColor(val label: String) {
    DEFAULT("Default"),
    RED("Red"),
    ORANGE("Orange"),
    AMBER("Amber"),
    GREEN("Green"),
    TEAL("Teal"),
    BLUE("Blue"),
    INDIGO("Indigo"),
    PURPLE("Purple"),
    PINK("Pink"),
    BROWN("Brown"),
    GREY("Grey"),
}

enum class StyleFont(val label: String) { DEFAULT("Default"), SERIF("Serif"), MONOSPACE("Mono"), CURSIVE("Cursive") }

/**
 * How the titles of one tab's items look, set per tab in Settings and used wherever those items
 * are listed: in their own tab, in "Linked:" lines and linked-item lists of other tabs, and in the
 * link picker.
 */
data class ItemStyle(
    val color: StyleColor = StyleColor.DEFAULT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val font: StyleFont = StyleFont.DEFAULT,
) {
    /** For a list of styles, e.g. "Blue · Bold · Serif"; "Default" when nothing is set. */
    val summary: String
        get() = listOfNotNull(
            color.label.takeIf { color != StyleColor.DEFAULT },
            "Bold".takeIf { bold },
            "Italic".takeIf { italic },
            "Underline".takeIf { underline },
            font.label.takeIf { font != StyleFont.DEFAULT },
        ).joinToString(" · ").ifEmpty { "Default" }

    /** Stored as "COLOR|FONT|bold|italic|underline", e.g. "BLUE|SERIF|1|0|0". */
    fun serialize(): String = listOf(color.name, font.name, flag(bold), flag(italic), flag(underline)).joinToString("|")

    companion object {
        /** Quotes were always shown in italics, so that stays their default. */
        fun default(tab: ItemType): ItemStyle = if (tab == ItemType.QUOTE) ItemStyle(italic = true) else ItemStyle()

        /** Unreadable or missing parts fall back to [default]'s. */
        fun parse(stored: String?, default: ItemStyle): ItemStyle {
            val parts = stored?.split('|') ?: return default
            fun bool(index: Int, fallback: Boolean) = when (parts.getOrNull(index)) {
                "1" -> true
                "0" -> false
                else -> fallback
            }
            return ItemStyle(
                color = StyleColor.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: default.color,
                font = StyleFont.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: default.font,
                bold = bool(2, default.bold),
                italic = bool(3, default.italic),
                underline = bool(4, default.underline),
            )
        }

        private fun flag(on: Boolean) = if (on) "1" else "0"
    }
}

/** The style of every tab; a chapter is styled like the Books tab it belongs to. */
class ItemStyles(private val byTab: Map<ItemType, ItemStyle> = emptyMap()) {
    operator fun get(type: ItemType): ItemStyle = byTab[type.tab] ?: ItemStyle.default(type.tab)
}
