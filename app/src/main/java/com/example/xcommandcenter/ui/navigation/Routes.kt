package com.example.xcommandcenter.ui.navigation

import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

/** Every item of every type opens through one route, `item/{type}/{id}`; id 0 creates a new one. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"

    const val ARG_TYPE = "type"
    const val ARG_ID = "id"
    /** Optional parent for a new plan, or the book of a chapter; 0 means none. */
    const val ARG_PARENT = "parent"

    const val ITEM = "item/{$ARG_TYPE}/{$ARG_ID}?$ARG_PARENT={$ARG_PARENT}"

    fun item(ref: ItemRef): String = "item/${ref.type.name}/${ref.id}"

    fun newItem(type: ItemType, parentId: Long? = null): String =
        "item/${type.name}/0" + (parentId?.let { "?$ARG_PARENT=$it" } ?: "")
}
