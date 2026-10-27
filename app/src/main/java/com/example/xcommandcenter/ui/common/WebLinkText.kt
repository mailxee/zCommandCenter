package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.WebLinks

/** [text] with every web address in it underlined and opening in the browser when tapped. */
@Composable
fun linkified(text: String): AnnotatedString {
    val links = WebLinks.find(text)
    if (links.isEmpty()) return AnnotatedString(text)
    val uriHandler = LocalUriHandler.current
    val styles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var at = 0
        links.forEach { link ->
            append(text.substring(at, link.range.first))
            // Opening can fail when no app handles web links; then the tap does nothing.
            withLink(LinkAnnotation.Url(link.url, styles) { runCatching { uriHandler.openUri(link.url) } }) {
                append(text.substring(link.range))
            }
            at = link.range.last + 1
        }
        append(text.substring(at))
    }
}

/**
 * The web addresses in an editable field's [text], as tappable links shown under the field (the
 * field itself is for typing, so tapping it edits). Shows nothing when there are none.
 */
@Composable
fun LinksInText(text: String) {
    val links = WebLinks.find(text)
    if (links.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        links.distinctBy { it.url }.forEach { link ->
            Text(linkified(text.substring(link.range)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
