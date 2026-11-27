package com.example.xcommandcenter.ui.links

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The map's Save as PDF (§2.81): a button that asks how the map goes on paper (on one page, or
 * across pages at a readable size; which paper), then where to save the file, and writes it.
 * [view] is what the map shows now; [title] heads each page.
 */
@Composable
internal fun MapPdfButton(view: MapPrintView?, title: String, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The light theme's colours, whatever the screen shows: paper is white.
    val colors = remember { arrayOfNulls<PrintColors>(1) }
    AppTheme(darkTheme = false) { colors[0] = printColors() }
    var asking by rememberSaveable { mutableStateOf(false) }
    var fit by rememberSaveable { mutableStateOf(PdfFit.ONE_PAGE) }
    var paper by rememberSaveable { mutableStateOf(Paper.usual(Locale.getDefault().country)) }
    var saving by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val shown = view
        val palette = colors[0]
        if (uri == null || shown == null || palette == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val text = try {
                val pages = withContext(Dispatchers.Default) {
                    val plan = MapPdf.plan(context, shown, paper, fit)
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
                    out.use { MapPdf.write(context, plan, palette, title, it) }
                    plan.pages
                }
                if (pages == 1) "Saved the map as a PDF." else "Saved the map as a PDF of $pages pages."
            } catch (e: Exception) {
                "Couldn't save the PDF: ${e.message ?: e.javaClass.simpleName}"
            }
            saving = false
            snackbar.showSnackbar(text)
        }
    }

    IconButton(onClick = { asking = true }, enabled = view != null && !saving) {
        Icon(painterResource(R.drawable.ic_print), contentDescription = "Save as PDF")
    }

    val shown = view
    if (asking && shown != null) {
        // How many pages the map takes across pages on this paper, worked out off the main thread.
        var pages by remember(shown, paper) { mutableStateOf<Int?>(null) }
        LaunchedEffect(shown, paper) {
            pages = withContext(Dispatchers.Default) { runCatching { MapPdf.plan(context, shown, paper, PdfFit.PAGES).pages }.getOrNull() }
        }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Save map as PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The map as it shows now, sharp at any size for printing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.selectableGroup()) {
                        FitChoice("Fit on one page", "Everything on one sheet, as large as it fits", fit == PdfFit.ONE_PAGE) { fit = PdfFit.ONE_PAGE }
                        FitChoice(
                            "Across pages",
                            "At a readable size, to join together" + (pages?.let { " · ${if (it == 1) "fits on 1 page" else "$it pages"}" } ?: ""),
                            fit == PdfFit.PAGES,
                        ) { fit = PdfFit.PAGES }
                    }
                    Text("Paper", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Paper.entries.forEach { p -> FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(p.label) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { asking = false; save.launch(MapPdf.fileName(title)) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FitChoice(label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
