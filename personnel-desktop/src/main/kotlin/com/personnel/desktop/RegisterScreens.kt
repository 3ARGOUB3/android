package com.personnel.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personnel.core.ColumnKind
import com.personnel.core.Register
import com.personnel.core.Row as DataRow
import com.personnel.core.search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, actions: AppActions) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { actions.vm.open(Screen.Workers) }) {
                        Icon(Icons.Outlined.People, contentDescription = stringResource(R.string.workers))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_workbook)) },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                            onClick = { menu = false; actions.importInto(null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_all)) },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            onClick = { menu = false; actions.exportTo(null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_switch)) },
                            leadingIcon = { Icon(Icons.Outlined.Translate, null) },
                            onClick = { menu = false; actions.vm.toggleLanguage() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.data_folder, dataDir.path),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                            onClick = {
                                menu = false
                                runCatching { java.awt.Desktop.getDesktop().open(dataDir.apply { mkdirs() }) }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { actions.vm.open(Screen.Workers) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(stringResource(R.string.workers), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.workers_count, state.directory.workers.size),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            items(state.registers, key = { it.id }) { register ->
                Card(Modifier.fillMaxWidth().clickable { actions.vm.open(Screen.RegisterList(register.id)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(register.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (register.rows.isEmpty()) {
                                    stringResource(R.string.register_empty_short)
                                } else {
                                    stringResource(R.string.register_counts, register.rows.size, register.addedCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RegisterScreen(register: Register, state: UiState, actions: AppActions) {
    var query by rememberSaveable(register.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var editColumns by remember { mutableStateOf(false) }
    val results = remember(register, query) { register.search(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(register.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_file)) },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                            onClick = { menu = false; actions.importInto(register.id) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_file)) },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            onClick = { menu = false; actions.exportTo(register) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_columns)) },
                            leadingIcon = { Icon(Icons.Outlined.Description, null) },
                            onClick = { menu = false; editColumns = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.vm.open(Screen.Form(register.id, null)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_row)) },
            )
        },
    ) { padding ->
        if (register.rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Outlined.FileUpload, null,
                    Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.register_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.register_empty_body),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { actions.importInto(register.id) }) { Text(stringResource(R.string.import_file)) }
                Text(
                    stringResource(R.string.columns_now, register.columns.joinToString(" · ")),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = { editColumns = true }) { Text(stringResource(R.string.edit_columns)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                SearchField(query, { query = it })
                Text(
                    stringResource(R.string.lines_shown, results.size, register.rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                RegisterTable(register, results) { index -> actions.vm.open(Screen.Form(register.id, index)) }
            }
        }
    }

    if (editColumns) {
        ColumnsDialog(register, onDismiss = { editColumns = false }) { columns, sources ->
            actions.vm.setColumns(register.id, columns, sources)
            editColumns = false
        }
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            }
        },
        placeholder = { Text(stringResource(R.string.search_hint)) },
    )
}

/** A line: matricule and name first, then every other filled column. */
@Composable
fun RowCard(register: Register, row: DataRow, title: String? = null, onClick: () -> Unit) {
    val mat = register.columnIndex(ColumnKind.MATRICULE)
    val name = register.columnIndex(ColumnKind.NAME)
    val function = register.columnIndex(ColumnKind.FUNCTION)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(register.cell(row, mat), register.cell(row, name)).filter { it.isNotBlank() }
                        .joinToString(" · ").ifEmpty { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (row.added) {
                    Text(
                        stringResource(R.string.new_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            register.cell(row, function).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            register.columns.forEachIndexed { i, header ->
                if (i == mat || i == name || i == function) return@forEachIndexed
                val value = row.cells.getOrElse(i) { "" }
                if (value.isBlank()) return@forEachIndexed
                Text(
                    "$header : $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Rename, add or remove columns. Cells stay with their column. */
@Composable
private fun ColumnsDialog(
    register: Register,
    onDismiss: () -> Unit,
    onSave: (columns: List<String>, sources: List<Int>) -> Unit,
) {
    val names = remember { mutableStateListOf(*register.columns.toTypedArray()) }
    val sources = remember { mutableStateListOf(*register.columns.indices.toList().toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_columns)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.indices.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = names[i],
                            onValueChange = { names[i] = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { names.removeAt(i); sources.removeAt(i) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                        }
                    }
                }
                SuggestionChip(
                    onClick = { names += ""; sources += -1 },
                    label = { Text(stringResource(R.string.add_column)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                )
                if (register.rows.isNotEmpty()) {
                    Text(
                        stringResource(R.string.columns_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keep = names.indices.filter { names[it].isNotBlank() }
                    onSave(keep.map { names[it].trim() }, keep.map { sources[it] })
                },
                enabled = names.any { it.isNotBlank() },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/** Spreadsheet-like view of a register: one column per field, click a line to edit it. */
@Composable
private fun RegisterTable(register: Register, results: List<IndexedValue<DataRow>>, onOpen: (Int) -> Unit) {
    val widths = remember(register) {
        register.columns.indices.map { c ->
            val longest = maxOf(
                register.columns[c].length,
                register.rows.take(300).maxOfOrNull { it.cells.getOrElse(c) { "" }.length } ?: 0,
            )
            (longest * 8 + 24).coerceIn(90, 320).dp
        }
    }
    val totalWidth = widths.fold(0.dp) { a, b -> a + b } + 70.dp
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    val outline = MaterialTheme.colorScheme.outlineVariant

    Box(Modifier.fillMaxSize().padding(bottom = 80.dp)) {
        Box(Modifier.fillMaxSize().padding(end = 12.dp, bottom = 12.dp).horizontalScroll(horizontal)) {
            LazyColumn(Modifier.width(totalWidth).fillMaxHeight(), state = vertical) {
                stickyHeader {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(1.dp, outline),
                    ) {
                        TableCell("#", 70.dp, bold = true)
                        register.columns.forEachIndexed { c, header -> TableCell(header, widths[c], bold = true) }
                    }
                }
                items(results, key = { it.index }) { (index, row) ->
                    Row(
                        Modifier
                            .clickable { onOpen(index) }
                            .background(
                                if (row.added) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surface,
                            )
                            .border(0.5.dp, outline),
                    ) {
                        TableCell((index + 1).toString(), 70.dp, muted = true)
                        register.columns.indices.forEach { c -> TableCell(row.cells.getOrElse(c) { "" }, widths[c]) }
                    }
                }
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(vertical),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
        HorizontalScrollbar(
            rememberScrollbarAdapter(horizontal),
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
        )
    }
}

@Composable
private fun TableCell(value: String, width: Dp, bold: Boolean = false, muted: Boolean = false) {
    Text(
        value,
        modifier = Modifier.width(width).padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
