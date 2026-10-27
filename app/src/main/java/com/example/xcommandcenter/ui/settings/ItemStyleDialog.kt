package com.example.xcommandcenter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.ItemStyle
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.StyleColor
import com.example.xcommandcenter.domain.StyleFont
import com.example.xcommandcenter.ui.common.family
import com.example.xcommandcenter.ui.common.isDarkSurface
import com.example.xcommandcenter.ui.common.toColor
import com.example.xcommandcenter.ui.common.toSpanStyle

/** Edits one tab's item style; every change is applied at once, with a preview on top. */
@Composable
fun ItemStyleDialog(
    tab: ItemType,
    style: ItemStyle,
    onChange: (ItemStyle) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isDarkSurface()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tab.tabLabel} style") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A ${tab.singular.lowercase()} looks like this",
                    style = MaterialTheme.typography.titleMedium.merge(style.toSpanStyle(dark)),
                )
                Label("Color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleColor.entries.forEach { color ->
                        FilterChip(
                            selected = style.color == color,
                            onClick = { onChange(style.copy(color = color)) },
                            label = { Text(color.label) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(color.toColor(dark).takeOrElse { MaterialTheme.colorScheme.onSurface }),
                                )
                            },
                        )
                    }
                }
                Label("Style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = style.bold,
                        onClick = { onChange(style.copy(bold = !style.bold)) },
                        label = { Text("Bold", fontWeight = FontWeight.Bold) },
                    )
                    FilterChip(
                        selected = style.italic,
                        onClick = { onChange(style.copy(italic = !style.italic)) },
                        label = { Text("Italic", fontStyle = FontStyle.Italic) },
                    )
                    FilterChip(
                        selected = style.underline,
                        onClick = { onChange(style.copy(underline = !style.underline)) },
                        label = { Text("Underline", textDecoration = TextDecoration.Underline) },
                    )
                }
                Label("Font")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleFont.entries.forEach { font ->
                        FilterChip(
                            selected = style.font == font,
                            onClick = { onChange(style.copy(font = font)) },
                            label = { Text(font.label, fontFamily = font.family) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
