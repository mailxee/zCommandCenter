package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.domain.LinkLabels

/** A label picked for a link: [subject] is true when it reads from this page's item ("this supports that"). */
data class LabelChoice(val labelId: Long, val subject: Boolean)

/**
 * Picks the label of the link between [self] (this page's item) and [other]: "relates to" (no
 * label) or one wording of a label. A label has a wording for each end, so picking one also
 * sets the direction; a preview reads the link back as a sentence.
 */
@Composable
fun LinkLabelDialog(
    self: String,
    other: String,
    labels: List<LinkLabelEntity>,
    initial: LabelChoice?,
    /** A label's definition from the glossary, shown once it is chosen; may be blank. */
    definition: (LinkLabelEntity) -> String,
    confirmLabel: String,
    onConfirm: (LabelChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    var choiceId by rememberSaveable { mutableStateOf(initial?.labelId) }
    var subject by rememberSaveable { mutableStateOf(initial?.subject ?: true) }
    val chosen = labels.firstOrNull { it.id == choiceId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label this link") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        buildAnnotatedString {
                            append(self.short())
                            append(" — ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(chosen?.wording(subject) ?: "relates to")
                            }
                            append(" — ")
                            append(other.short())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                chosen?.let(definition)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelOption("relates to", selected = chosen == null) { choiceId = null }
                }
                labels.forEach { label ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LabelOption(label.forward, selected = chosen?.id == label.id && (subject || label.symmetric), warn = label.key == LinkLabels.CONTRADICTS) {
                            choiceId = label.id
                            subject = true
                        }
                        if (!label.symmetric) {
                            LabelOption(label.backward.orEmpty(), selected = chosen?.id == label.id && !subject) {
                                choiceId = label.id
                                subject = false
                            }
                        }
                    }
                }
                Text(
                    "Labels are only words on the link; any label works between any items. Edit them in Settings, and what they mean in Settings › Glossary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen?.let { LabelChoice(it.id, subject || it.symmetric) }) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun String.short(): String = if (length > 40) take(38).trimEnd() + "…" else this

@Composable
private fun LabelOption(text: String, selected: Boolean, warn: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
        colors = if (warn) FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.error) else FilterChipDefaults.filterChipColors(),
    )
}
