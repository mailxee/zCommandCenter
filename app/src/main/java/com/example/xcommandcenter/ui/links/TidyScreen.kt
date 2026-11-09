package com.example.xcommandcenter.ui.links

import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.FlowRow
import com.example.xcommandcenter.domain.AppMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.ui.AppViewModelProvider

/** Tidy links (§2.57): one card per finding, most important first, with its fix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidyScreen(
    onBack: () -> Unit,
    onShowOnMap: (root: ItemRef?, focus: String) -> Unit,
    onOpen: (ItemRef) -> Unit = {},
    viewModel: TidyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        viewModel.undoShown()
        if (snackbar.showSnackbar(action.message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
            viewModel.runUndo(action)
        }
    }
    var showKept by rememberSaveable { mutableStateOf(false) }
    val s = state
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tidy links")
                        if (s != null) {
                            val routes = if (s.mode == AppMode.THINK) {
                                if (s.findings.isEmpty()) "nothing to look at" else "${s.findings.size} to look at"
                            } else {
                                when (s.extraRoutes) {
                                    0 -> "no extra routes"
                                    1 -> "1 extra route"
                                    else -> "${s.extraRoutes} extra routes"
                                }
                            }
                            Text(
                                (s.root?.title ?: "All networks") + " · $routes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (s == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AppMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.mode == m,
                            onClick = { viewModel.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, AppMode.entries.size),
                        ) { Text("${m.label} · ${s.counts[m] ?: 0}", maxLines = 1) }
                    }
                }
            }
            if (s.findings.isEmpty()) {
                item {
                    Text(
                        if (s.mode == AppMode.THINK) "Nothing to look at: quotes are placed, ideas grounded and beliefs acted on."
                        else "Nothing to tidy: every link follows a single route.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(s.findings, key = { it.key }) { finding ->
                FindingCard(
                    finding, s,
                    onFix = { viewModel.fix(finding) },
                    onKeep = { viewModel.keep(finding) },
                    onShow = { onShowOnMap(s.root?.ref ?: finding.items.first(), finding.key) },
                    onOpen = finding.endPoint?.let { ref -> { onOpen(ref) } },
                )
            }
            if (s.kept.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showKept = !showKept }.padding(vertical = 8.dp),
                    ) {
                        Text("Kept as is (${s.kept.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Icon(painterResource(if (showKept) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = if (showKept) "Hide" else "Show")
                    }
                }
                if (showKept) {
                    items(s.kept, key = { "kept-" + it.key }) { finding ->
                        val text = findingText(finding, s.route, s::title)
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                                Text(text.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                                TextButton(onClick = { viewModel.showAgain(finding) }) { Text("Show again") }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    if (s.mode == AppMode.THINK) {
                        "In Think mode, loops between ideas are normal and aren't reported. Labelled links are never suggested for removal " +
                            "before plain ones, and a contradiction never is. Kept findings stay hidden until you show them again."
                    } else {
                        "Suggestions follow the suggested route in Settings. Every change can be undone from the message at the bottom; " +
                            "kept findings stay hidden until the links involved change."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The colour that marks a finding: red for a loop's shortcut, amber for off route, grey for information. */
@Composable
fun findingColor(kind: LinkAnalysis.Kind): Color = when (kind) {
    LinkAnalysis.Kind.SHORTCUT, LinkAnalysis.Kind.EXTRA_ROUTE -> RemoveColor
    LinkAnalysis.Kind.OFF_ROUTE_LOOP, LinkAnalysis.Kind.SKIPS_LAYER -> OffRouteColor
    LinkAnalysis.Kind.SHARED_END_POINT -> MaterialTheme.colorScheme.outline
    LinkAnalysis.Kind.GOAL_WITHOUT_WHY, LinkAnalysis.Kind.BELIEF_NOT_ACTED -> Color(0xFFC2185B)
    LinkAnalysis.Kind.AUTHOR_IS_PERSON -> Color(0xFF1565C0)
    LinkAnalysis.Kind.UNPLACED_QUOTE -> Color(0xFFB26A00)
    LinkAnalysis.Kind.UNGROUNDED_IDEA -> Color(0xFF00796B)
    LinkAnalysis.Kind.CONTRADICTION -> ContradictColor
}

@Composable
private fun FindingCard(
    finding: LinkAnalysis.Finding,
    state: TidyState,
    onFix: () -> Unit,
    onKeep: () -> Unit,
    onShow: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val text = findingText(finding, state.route, state::title)
    val color = findingColor(finding.kind)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                Text(text.text, style = MaterialTheme.typography.bodyMedium)
                Text(text.suggestion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (text.action != null) FilledTonalButton(onClick = onFix) { Text(text.action) }
                    else if (onOpen != null) FilledTonalButton(onClick = onOpen) { Text("Open") }
                    TextButton(onClick = onKeep) { Text(if (finding.kind == LinkAnalysis.Kind.CONTRADICTION) "Hide" else "Keep as is") }
                    TextButton(onClick = onShow) { Text("Show on map") }
                }
            }
        }
    }
}
