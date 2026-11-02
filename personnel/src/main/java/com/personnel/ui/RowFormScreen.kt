package com.personnel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personnel.R
import com.personnel.Screen
import com.personnel.UiState
import com.personnel.core.ColumnKind
import com.personnel.core.Dates
import com.personnel.core.Register
import com.personnel.core.Suggestions
import com.personnel.core.Worker
import com.personnel.core.WorkerDirectory
import com.personnel.core.normalize
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowFormScreen(register: Register, screen: Screen.Form, state: UiState, actions: AppActions) {
    val columns = register.columns
    val kinds = remember(columns) { columns.map(ColumnKind::of) }
    val directory = state.directory
    val editing = screen.rowIndex?.let { register.rows.getOrNull(it) }

    val initial = remember(screen) {
        columns.indices.map { i ->
            when {
                editing != null -> editing.cells.getOrElse(i) { "" }
                kinds[i] == ColumnKind.NUMBER -> Suggestions.nextNumber(register, i)
                else -> ""
            }
        }.let { cells ->
            val worker = screen.workerKey?.let(directory::withKey)
            if (worker != null) fillWorker(cells.toMutableList(), kinds, worker) else cells
        }
    }
    val values: SnapshotStateList<String> = rememberSaveable(
        screen,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { initial.toMutableStateList() }
    var focused by remember { mutableStateOf(-1) }
    var datePickerFor by remember { mutableStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val changed = values.toList() != initial
    BackHandler { if (changed) confirmDiscard = true else actions.vm.back() }

    fun save() {
        val cells = values.map { it.trim() }
        if (cells.all { it.isEmpty() }) {
            error = true
            return
        }
        actions.vm.saveRow(register.id, screen.rowIndex, cells)
        actions.vm.back()
    }

    fun selectWorker(worker: Worker) {
        val filled = fillWorker(values.toMutableList(), kinds, worker, overwrite = true)
        filled.forEachIndexed { i, v -> values[i] = v }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (editing == null) R.string.new_row else R.string.edit_row))
                        Text(register.title, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (changed) confirmDiscard = true else actions.vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (editing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = ::save) { Icon(Icons.Filled.Check, stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            columns.forEachIndexed { i, header ->
                val kind = kinds[i]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { text ->
                            values[i] = text
                            error = false
                            if (kind == ColumnKind.MATRICULE) {
                                // Known matricule: fill the name and function left empty.
                                directory.find(text)?.let { w ->
                                    fillWorker(values.toMutableList(), kinds, w).forEachIndexed { j, v -> values[j] = v }
                                }
                            }
                        },
                        label = { Text(header) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) focused = i },
                        singleLine = kind != ColumnKind.OTHER,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (kind) {
                                ColumnKind.NUMBER -> KeyboardType.Number
                                else -> KeyboardType.Text
                            },
                            imeAction = ImeAction.Next,
                        ),
                        trailingIcon = trailingIconFor(kind) { datePickerFor = i },
                        supportingText = if (kind == ColumnKind.MATRICULE) knownWorkerText(directory.find(values[i])) else null,
                    )
                    if (focused == i) {
                        SuggestionRow(register, i, kind, values[i], directory, ::selectWorker) { values[i] = it }
                    }
                }
            }
            if (error) {
                Text(stringResource(R.string.row_empty), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }

    if (datePickerFor >= 0) {
        val current = Dates.parse(values[datePickerFor]) ?: LocalDate.now()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = current.toEpochDay() * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { datePickerFor = -1 },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        values[datePickerFor] = Dates.format(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MILLIS)))
                    }
                    datePickerFor = -1
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { datePickerFor = -1 }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }

    val deleteIndex = screen.rowIndex
    if (confirmDelete && deleteIndex != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_row_title)) },
            text = { Text(stringResource(R.string.delete_row_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.vm.back()
                    actions.vm.deleteRow(register.id, deleteIndex)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; actions.vm.back() }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

private fun trailingIconFor(kind: ColumnKind, onPickDate: () -> Unit): (@Composable () -> Unit)? = when (kind) {
    ColumnKind.DATE -> {
        @Composable {
            IconButton(onClick = onPickDate) {
                Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date))
            }
        }
    }
    ColumnKind.MATRICULE, ColumnKind.NAME -> {
        @Composable { Icon(Icons.Outlined.PersonSearch, null) }
    }
    else -> null
}

private fun knownWorkerText(worker: Worker?): (@Composable () -> Unit)? =
    if (worker == null) null else {
        @Composable { Text(stringResource(R.string.known_worker, worker.name, worker.records.size)) }
    }

/** Chips under the field being typed in: matching workers, earlier values or today's date. */
@Composable
private fun SuggestionRow(
    register: Register,
    column: Int,
    kind: ColumnKind,
    typed: String,
    directory: WorkerDirectory,
    onWorker: (Worker) -> Unit,
    onValue: (String) -> Unit,
) {
    val workers = remember(typed, kind, directory) {
        val q = normalize(typed)
        when {
            q.isEmpty() -> emptyList()
            kind == ColumnKind.MATRICULE -> directory.workers.filter {
                WorkerDirectory.matriculeKey(it.matricule).startsWith(WorkerDirectory.matriculeKey(typed)) ||
                    normalize(it.matricule).startsWith(q)
            }
            kind == ColumnKind.NAME && q.length >= 2 -> directory.search(typed)
            else -> emptyList()
        }.filter { normalize(it.matricule) != q && normalize(it.name) != q }.take(6)
    }
    val values = remember(typed, column, register) {
        when (kind) {
            ColumnKind.MATRICULE, ColumnKind.NAME, ColumnKind.NUMBER -> emptyList()
            ColumnKind.DATE -> listOf(Dates.format(LocalDate.now())).filter { it != typed }
            else -> Suggestions.forColumn(register, column, typed)
        }
    }
    if (workers.isEmpty() && values.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        workers.forEach { w ->
            SuggestionChip(
                onClick = { onWorker(w) },
                label = { Text(listOf(w.matricule, w.name).filter { it.isNotBlank() }.joinToString(" · ")) },
            )
        }
        values.forEach { v ->
            SuggestionChip(onClick = { onValue(v) }, label = { Text(v, maxLines = 1) })
        }
    }
}

/** Puts the worker's matricule, name and function into their columns (only empty ones unless [overwrite]). */
private fun fillWorker(
    cells: MutableList<String>,
    kinds: List<ColumnKind>,
    worker: Worker,
    overwrite: Boolean = false,
): List<String> {
    kinds.forEachIndexed { i, kind ->
        val value = when (kind) {
            ColumnKind.MATRICULE -> worker.matricule
            ColumnKind.NAME -> worker.name
            ColumnKind.FUNCTION -> worker.function
            else -> return@forEachIndexed
        }
        if (value.isNotBlank() && (overwrite || cells[i].isBlank())) cells[i] = value
    }
    return cells
}
