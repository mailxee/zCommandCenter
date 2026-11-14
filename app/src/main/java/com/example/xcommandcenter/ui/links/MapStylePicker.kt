package com.example.xcommandcenter.ui.links

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.MapStyle

/**
 * A layout to choose (§2.70): a button naming [current], opening every [MapStyle] with what it
 * does. With [inherited] set, the first choice is to follow the item above ([current] null), whose
 * layout that is.
 */
@Composable
internal fun MapStylePicker(label: String, current: MapStyle?, inherited: MapStyle?, onPick: (MapStyle?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text((current?.label ?: inherited?.let { "As above (${it.label})" }.orEmpty()) + "  ▾", maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (inherited != null) {
                    Choice("As above", "Like the item above it: ${inherited.label}.", current == null) { open = false; onPick(null) }
                }
                MapStyle.entries.forEach { style ->
                    Choice(style.label, style.description, current == style) { open = false; onPick(style) }
                }
            }
        }
    }
}

@Composable
private fun Choice(name: String, description: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column(Modifier.widthIn(max = 280.dp)) {
                Text((if (selected) "✓ " else "") + name, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onClick,
    )
}
