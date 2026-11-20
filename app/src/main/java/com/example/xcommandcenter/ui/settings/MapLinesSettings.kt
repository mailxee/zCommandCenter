package com.example.xcommandcenter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.links.ContradictColor
import com.example.xcommandcenter.ui.links.LineChoices

/**
 * How the map draws each kind of line: Under, links with no label, and each label, with a pattern
 * (solid, dashes, dots…) and a width. The same choices as the map's Show panel, but for every kind,
 * not only those on the map in view. Folded away until opened, as the list grows with the labels.
 */
@Composable
internal fun MapLinesSettings(viewModel: SettingsViewModel) {
    val filter by viewModel.mapFilter.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SectionTitle("Map lines") }
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Change") }
    }
    Text(
        "How the map draws the lines between boxes: solid, dashed, long dashes, dotted or dash-dot, thin, normal or thick. " +
            "Pick for the Under lines, for links without a label and for each label.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!open) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val outline = MaterialTheme.colorScheme.outline
            val kinds = buildList {
                add(Kind(MapFilter.UNDER, "Under", "From an idea or plan to the one it is under", null))
                add(Kind(MapFilter.PLAIN, "No label", "Links without a label", null))
                labels.forEach { label ->
                    add(Kind(MapFilter.label(label.id), listOfNotNull(label.forward, label.backward).joinToString(" · "), null, label.key))
                }
            }
            kinds.forEachIndexed { i, kind ->
                if (i > 0) HorizontalDivider()
                Text(kind.name, style = MaterialTheme.typography.bodyLarge)
                kind.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                LineChoices(
                    name = kind.name,
                    color = if (kind.labelKey == LinkLabels.CONTRADICTS) ContradictColor else outline,
                    thick = kind.key == MapFilter.UNDER,
                    style = filter.styleOf(kind.key, kind.labelKey),
                    width = filter.widthOf(kind.key),
                    onStyle = { viewModel.setLineStyle(kind.key, it) },
                    onWidth = { viewModel.setLineWidth(kind.key, it) },
                )
            }
            TextButton(onClick = viewModel::resetLines, modifier = Modifier.align(Alignment.End)) { Text("Reset all lines") }
        }
    }
}

private data class Kind(val key: String, val name: String, val help: String?, val labelKey: String?)
