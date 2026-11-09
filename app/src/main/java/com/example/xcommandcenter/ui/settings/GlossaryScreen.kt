package com.example.xcommandcenter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.data.repository.GlossaryRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.domain.Glossary
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.kindColor
import com.example.xcommandcenter.ui.common.kindIcon
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlossaryViewModel(private val glossary: GlossaryRepository, links: LinkRepository) : ViewModel() {
    val terms: StateFlow<Glossary.Terms> = glossary.terms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Glossary.Terms())
    val labels: StateFlow<List<LinkLabelEntity>> = links.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(term: Term, definition: String) {
        viewModelScope.launch { glossary.set(term.key, definition, term.default) }
    }

    fun reset(term: Term) {
        viewModelScope.launch { glossary.reset(term.key) }
    }
}

/** One entry of the glossary: its stored [key], name, starting definition, and icon and colour. */
data class Term(val key: String, val name: String, val default: String, val icon: Int, val tint: Color?)

/** The tabs on the managing side, then the sources and notes; ideas have their own section. */
private val doingTypes = listOf(ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT, ItemType.BLOCK)
private val sourceTypes = listOf(ItemType.IDEA, ItemType.NOTE, ItemType.BOOK, ItemType.CHAPTER, ItemType.QUOTE, ItemType.PERSON)

/**
 * Settings › Glossary: what each term means, editable. Idea kinds, the tabs (Doing, then Thinking
 * and sources) and the link labels, each with its definition; tap one to rewrite it or put the
 * starting one back. Definitions show on an idea's page, in the label picker and on empty tabs.
 */
@Composable
fun GlossaryScreen(onBack: () -> Unit, viewModel: GlossaryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val terms by viewModel.terms.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Term?>(null) }
    val kinds = IdeaKind.entries.map { Term(Glossary.kindKey(it), it.label, Glossary.default(it), kindIcon(it), kindColor(it)) }
    val doing = doingTypes.map { t -> typeTerm(t, tabAccent(t)) }
    val sources = sourceTypes.map { t -> typeTerm(t, tabAccent(t)) }
    val labelTerms = labels.map {
        Term(
            Glossary.labelKey(it.id), listOfNotNull(it.forward, it.backward).joinToString(" · "), Glossary.defaultForLabel(it.key),
            R.drawable.ic_label, if (it.key == LinkLabels.CONTRADICTS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    fun current(term: Term) = term.edited(terms) ?: term.default

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glossary") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "What each term means to you. Tap one to rewrite it. Definitions show on an idea's page, in the label picker and on empty tabs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            listOf("Ideas" to kinds, "Doing" to doing, "Thinking and sources" to sources, "Link labels" to labelTerms).forEach { (title, group) ->
                item { SectionTitle(title) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            group.forEach { term -> TermRow(term, current(term), terms.isEdited(term.key)) { editing = term } }
                        }
                    }
                }
            }
        }
    }

    editing?.let { term ->
        var text by remember(term) { mutableStateOf(current(term)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(term.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Definition") }, modifier = Modifier.fillMaxWidth())
                    if (term.default.isNotBlank() && text.trim() != term.default) {
                        Text("Starting definition: ${term.default}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.save(term, text); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (terms.isEdited(term.key)) TextButton(onClick = { viewModel.reset(term); editing = null }) { Text("Reset to default") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun typeTerm(t: ItemType, tint: Color) =
    Term(Glossary.typeKey(t), if (t == ItemType.BLOCK) "Schedule block" else t.singular, Glossary.default(t), tabIcon(t), tint)

private fun Term.edited(terms: Glossary.Terms): String? = terms.edited[key]

@Composable
private fun TermRow(term: Term, definition: String, edited: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit", onClick = onClick).padding(vertical = 8.dp),
    ) {
        Icon(painterResource(term.icon), contentDescription = null, tint = term.tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(term.name, style = MaterialTheme.typography.titleSmall)
                if (edited) Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(
                definition.ifBlank { "No definition yet." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(R.drawable.ic_edit), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(18.dp),
        )
    }
}
