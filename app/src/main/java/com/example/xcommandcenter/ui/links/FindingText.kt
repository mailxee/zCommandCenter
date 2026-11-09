package com.example.xcommandcenter.ui.links

import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkAnalysis.Finding
import com.example.xcommandcenter.domain.LinkAnalysis.Kind
import com.example.xcommandcenter.domain.LinkRoute

/** A finding in words, for Tidy links and the map's panel: a label, what is found, and the suggestion. */
data class FindingText(val label: String, val text: String, val suggestion: String, val action: String?)

fun findingText(f: Finding, route: LinkRoute, itemTitle: (ItemRef) -> String): FindingText {
    // A quote reads as a quote, so the sentence around it stays clear.
    val title: (ItemRef) -> String = { ref -> itemTitle(ref).let { if (ref.type == ItemType.QUOTE) "“$it”" else it } }
    fun names(refs: List<ItemRef>): String {
        val shown = refs.take(3).map(title)
        val more = if (refs.size > 3) " and ${refs.size - 3} more" else ""
        return when (shown.size) {
            0 -> ""
            1 -> shown[0] + more
            else -> if (more.isEmpty()) shown.dropLast(1).joinToString(", ") + " and " + shown.last() else shown.joinToString(", ") + more
        }
    }
    val link = f.link
    // The upper item first: "the goal is linked to the task".
    val (upper, lower) = if (link == null) null to null else
        if (route.layer(link.a.type) <= route.layer(link.b.type)) link.a to link.b else link.b to link.a
    return when (f.kind) {
        Kind.SHORTCUT -> FindingText(
            "Shortcut",
            "${title(upper!!)} is linked to ${title(lower!!)} directly, and also through ${names(f.via)}.",
            "Suggestion: remove the direct link.",
            "Remove link",
        )
        Kind.OFF_ROUTE_LOOP -> {
            val sideways = route.kind(upper!!.type, lower!!.type) == LinkRoute.Kind.SIDEWAYS
            FindingText(
                "Off route, in a loop",
                "${title(upper)} and ${title(lower)} are linked ${if (sideways) "${upper.type.singular.lowercase()} to ${lower.type.singular.lowercase()}" else "across a layer"}, " +
                    "and are also connected through ${names(f.via)}.",
                "Suggestion: remove this link.",
                "Remove link",
            )
        }
        Kind.EXTRA_ROUTE -> FindingText(
            "Two routes",
            "${title(upper!!)} and ${title(lower!!)} are linked, and are also connected through ${names(f.via)}.",
            "Suggestion: remove this link, the newer of the two routes.",
            "Remove link",
        )
        Kind.SKIPS_LAYER -> {
            val skipped = layerName(route, f.missingLayer ?: (route.layer(lower!!.type) - 1))
            val bridge = f.add?.second
            if (bridge != null) {
                FindingText(
                    "Skips a layer",
                    "${title(lower!!)} links straight to ${title(upper!!)}, skipping $skipped. ${title(bridge)} is linked to ${title(upper)}.",
                    "Suggestion: link ${title(lower)} to ${title(bridge)} and remove the direct link.",
                    "Apply",
                )
            } else {
                FindingText(
                    "Skips a layer",
                    "${title(lower!!)} links straight to ${title(upper!!)}, skipping $skipped.",
                    "Nothing in $skipped is linked to ${title(upper)} yet: add one between them, or keep this as is.",
                    null,
                )
            }
        }
        Kind.SHARED_END_POINT -> FindingText(
            "Shared end point",
            "${title(f.endPoint!!)} is linked to ${f.sharedBy.size} items in one network: ${names(f.sharedBy)}. " +
                "A shared ${f.endPoint.type.singular.lowercase()} is usually intended.",
            "No change suggested.",
            null,
        )
        Kind.GOAL_WITHOUT_WHY -> FindingText(
            "Goal without a why",
            "No vision, belief, principle or idea links to ${title(f.endPoint!!)}, so nothing says why it matters.",
            "Suggestion: link it to the idea behind it, or keep it as is. This check can be turned off in Settings.",
            null,
        )
        Kind.AUTHOR_IS_PERSON -> FindingText(
            "Author is in People",
            "${title(f.endPoint!!)} is by ${title(f.add!!.first)}, who is in People but isn't linked to it.",
            "Suggestion: link ${title(f.add.first)} as its source.",
            "Link",
        )
        Kind.UNPLACED_QUOTE -> FindingText(
            "Unplaced quote",
            "${title(f.endPoint!!)} isn't linked to any idea.",
            "Suggestion: link it to the idea it supports, inspired or contradicts.",
            null,
        )
        Kind.UNGROUNDED_IDEA -> FindingText(
            "Ungrounded idea",
            "${title(f.endPoint!!)} has no quote, book, person or note behind it.",
            "Suggestion: link where it came from, or keep it as your own.",
            null,
        )
        Kind.BELIEF_NOT_ACTED -> FindingText(
            "Not acted on",
            "${title(f.endPoint!!)} doesn't lead to any goal, plan, task or habit, nor do the ideas under it.",
            "Suggestion: link it to what it should change in Do mode.",
            null,
        )
        Kind.CONTRADICTION -> FindingText(
            "Contradiction",
            "${title(link!!.a)} and ${title(link.b)} contradict each other.",
            "Just a note: worth coming back to now and then.",
            null,
        )
    }
}

/** The types in a layer, as a name: "Plans", or "Tasks or Habits". */
fun layerName(route: LinkRoute, layer: Int): String =
    LinkRoute.types.filter { route.layer(it) == layer }.joinToString(" or ") { it.tabLabel }.ifEmpty { "a layer" }
