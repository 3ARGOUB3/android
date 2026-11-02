package com.personnel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personnel.core.ImportException
import com.personnel.core.Register
import com.personnel.core.RegisterStore
import com.personnel.core.Row
import com.personnel.core.SheetMatcher
import com.personnel.core.SpreadsheetReader
import com.personnel.core.Table
import com.personnel.core.WorkerDirectory
import com.personnel.core.XlsxWriter
import com.personnel.core.withColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Screens, kept as a back stack in the view model so they survive rotation. */
sealed interface Screen {
    data object Home : Screen
    data class RegisterList(val registerId: String) : Screen
    /** [rowIndex] null = new line; [workerKey] pre-fills matricule, name and function. */
    data class Form(val registerId: String, val rowIndex: Int?, val workerKey: String? = null) : Screen
    data object Workers : Screen
    data class WorkerDetail(val workerKey: String) : Screen
}

/** Sheets read from a picked file, waiting for the user to say which register each one fills. */
data class PendingImport(
    val fileName: String,
    val tables: List<Table>,
    /** Register id chosen for each table, or null to skip it. */
    val targets: List<String?>,
)

data class UiState(
    val registers: List<Register> = emptyList(),
    val directory: WorkerDirectory = WorkerDirectory(emptyList()),
    val loading: Boolean = true,
    val message: String? = null,
    val pendingImport: PendingImport? = null,
) {
    fun register(id: String): Register? = registers.firstOrNull { it.id == id }
}

class PersonnelViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RegisterStore(File(application.filesDir, "registres"))
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.loadAll() }
            setRegisters(saved)
            _state.update { it.copy(loading = false) }
        }
    }

    // ---------- navigation ----------

    fun open(screen: Screen) = _stack.update { it + screen }

    fun back() = _stack.update { if (it.size > 1) it.dropLast(1) else it }

    // ---------- editing ----------

    fun saveRow(registerId: String, rowIndex: Int?, cells: List<String>) = change(registerId) { register ->
        val rows = register.rows.toMutableList()
        if (rowIndex == null) {
            rows += Row(cells, added = true)
        } else {
            rows[rowIndex] = rows[rowIndex].copy(cells = cells)
        }
        register.copy(rows = rows)
    }

    fun deleteRow(registerId: String, rowIndex: Int) = change(registerId) { register ->
        register.copy(rows = register.rows.filterIndexed { i, _ -> i != rowIndex })
    }

    fun setColumns(registerId: String, columns: List<String>, sources: List<Int>) = change(registerId) {
        it.withColumns(columns, sources)
    }

    private fun change(registerId: String, transform: (Register) -> Register) {
        val current = _state.value.register(registerId) ?: return
        val updated = transform(current)
        setRegisters(_state.value.registers.map { if (it.id == registerId) updated else it })
        viewModelScope.launch(Dispatchers.IO) { store.save(updated) }
    }

    private fun setRegisters(registers: List<Register>) =
        _state.update { it.copy(registers = registers, directory = WorkerDirectory(registers)) }

    // ---------- import ----------

    /**
     * Reads a workbook. Each sheet is offered for the register its name suggests; when
     * [forRegisterId] is given, the best sheet goes to that register instead.
     */
    fun readFile(uri: Uri, fileName: String, forRegisterId: String?) {
        val app = getApplication<Application>()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw ImportException(app.getString(R.string.cannot_open))
                    SpreadsheetReader.read(bytes).mapNotNull(SpreadsheetReader::toTable)
                }
            }
            result.fold(
                onSuccess = { tables ->
                    if (tables.isEmpty()) {
                        fail(app.getString(R.string.no_data))
                        return@fold
                    }
                    val registers = _state.value.registers
                    val targets: List<String?> = if (forRegisterId != null) {
                        val target = registers.first { it.id == forRegisterId }
                        val best = tables.indexOfFirst { SheetMatcher.bestRegister(it.sheetName, registers)?.id == forRegisterId }
                            .takeIf { it >= 0 } ?: tables.indexOfFirst { it.rows.isNotEmpty() }.coerceAtLeast(0)
                        tables.indices.map { if (it == best) target.id else null }
                    } else {
                        val used = HashSet<String>()
                        tables.map { table ->
                            SheetMatcher.bestRegister(table.sheetName, registers)?.id?.takeIf { used.add(it) }
                        }
                    }
                    _state.update {
                        it.copy(loading = false, pendingImport = PendingImport(fileName, tables, targets))
                    }
                },
                onFailure = { error -> fail(app.getString(R.string.import_failed, error.message ?: error.javaClass.simpleName)) },
            )
        }
    }

    fun setImportTarget(tableIndex: Int, registerId: String?) = _state.update { s ->
        val pending = s.pendingImport ?: return@update s
        // A register takes one sheet: choosing it here clears it elsewhere.
        val targets = pending.targets.mapIndexed { i, t ->
            when {
                i == tableIndex -> registerId
                registerId != null && t == registerId -> null
                else -> t
            }
        }
        s.copy(pendingImport = pending.copy(targets = targets))
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    /** Replaces the chosen registers with the sheets' columns and lines. */
    fun confirmImport() {
        val pending = _state.value.pendingImport ?: return
        val app = getApplication<Application>()
        val byId = pending.tables.indices
            .mapNotNull { i -> pending.targets[i]?.let { it to pending.tables[i] } }
            .toMap()
        val updated = _state.value.registers.map { register ->
            val table = byId[register.id] ?: return@map register
            register.copy(columns = table.columns, rows = table.rows.map { Row(it) })
        }
        setRegisters(updated)
        _state.update { s ->
            s.copy(
                pendingImport = null,
                message = app.getString(R.string.imported, byId.size, byId.values.sumOf { it.rows.size }),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            updated.filter { it.id in byId }.forEach(store::save)
        }
    }

    // ---------- export ----------

    fun export(uri: Uri, registerId: String?) {
        val app = getApplication<Application>()
        val registers = _state.value.registers.filter { registerId == null || it.id == registerId }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { XlsxWriter.write(registers, it) }
                        ?: throw ImportException(app.getString(R.string.cannot_open))
                }
            }
            val message = result.fold(
                onSuccess = { app.getString(R.string.exported) },
                onFailure = { app.getString(R.string.export_failed, it.message ?: it.javaClass.simpleName) },
            )
            _state.update { it.copy(message = message) }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, message = message) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
