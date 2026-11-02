package com.personnel.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personnel.core.Register
import com.personnel.core.RegisterStore
import com.personnel.core.Row
import com.personnel.core.SheetMatcher
import com.personnel.core.SpreadsheetReader
import com.personnel.core.Table
import com.personnel.core.WorkerDirectory
import com.personnel.core.XlsxWriter
import com.personnel.core.withColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

/** Where the registers are saved: %APPDATA%\GestionPersonnel on Windows, ~/.gestion-personnel elsewhere. */
val dataDir: File = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
    ?.let { File(it, "GestionPersonnel") }
    ?: File(System.getProperty("user.home"), ".gestion-personnel")

/** Desktop counterpart of the Android PersonnelViewModel, with the same actions. */
class AppState(private val scope: CoroutineScope) {

    private val store = RegisterStore(File(dataDir, "registres"))
    private val langFile = File(dataDir, "langue.txt")

    var state by mutableStateOf(UiState())
        private set

    val stack = mutableStateListOf<Screen>(Screen.Home)

    init {
        runCatching { langFile.readText().trim() }.getOrNull()?.let { Lang.arabic = it == "ar" }
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.loadAll() }
            setRegisters(saved)
            state = state.copy(loading = false)
        }
    }

    fun toggleLanguage() {
        Lang.arabic = !Lang.arabic
        runCatching {
            dataDir.mkdirs()
            langFile.writeText(if (Lang.arabic) "ar" else "fr")
        }
    }

    // ---------- navigation ----------

    fun open(screen: Screen) {
        stack += screen
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

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
        val current = state.register(registerId) ?: return
        val updated = transform(current)
        setRegisters(state.registers.map { if (it.id == registerId) updated else it })
        scope.launch(Dispatchers.IO) { store.save(updated) }
    }

    private fun setRegisters(registers: List<Register>) {
        state = state.copy(registers = registers, directory = WorkerDirectory(registers))
    }

    // ---------- import ----------

    /**
     * Reads a workbook. Each sheet is offered for the register its name suggests; when
     * [forRegisterId] is given, the best sheet goes to that register instead.
     */
    fun readFile(file: File, forRegisterId: String?) {
        state = state.copy(loading = true)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SpreadsheetReader.read(file.readBytes()).mapNotNull(SpreadsheetReader::toTable) }
            }
            result.fold(
                onSuccess = { tables ->
                    if (tables.isEmpty()) {
                        fail(text(R.string.no_data))
                        return@fold
                    }
                    val registers = state.registers
                    val targets: List<String?> = if (forRegisterId != null) {
                        val best = tables.indexOfFirst { SheetMatcher.bestRegister(it.sheetName, registers)?.id == forRegisterId }
                            .takeIf { it >= 0 } ?: tables.indexOfFirst { it.rows.isNotEmpty() }.coerceAtLeast(0)
                        tables.indices.map { if (it == best) forRegisterId else null }
                    } else {
                        val used = HashSet<String>()
                        tables.map { table ->
                            SheetMatcher.bestRegister(table.sheetName, registers)?.id?.takeIf { used.add(it) }
                        }
                    }
                    state = state.copy(loading = false, pendingImport = PendingImport(file.name, tables, targets))
                },
                onFailure = { error -> fail(text(R.string.import_failed, error.message ?: error.javaClass.simpleName)) },
            )
        }
    }

    fun setImportTarget(tableIndex: Int, registerId: String?) {
        val pending = state.pendingImport ?: return
        // A register takes one sheet: choosing it here clears it elsewhere.
        val targets = pending.targets.mapIndexed { i, t ->
            when {
                i == tableIndex -> registerId
                registerId != null && t == registerId -> null
                else -> t
            }
        }
        state = state.copy(pendingImport = pending.copy(targets = targets))
    }

    fun cancelImport() {
        state = state.copy(pendingImport = null)
    }

    /** Replaces the chosen registers with the sheets' columns and lines. */
    fun confirmImport() {
        val pending = state.pendingImport ?: return
        val byId = pending.tables.indices
            .mapNotNull { i -> pending.targets[i]?.let { it to pending.tables[i] } }
            .toMap()
        val updated = state.registers.map { register ->
            val table = byId[register.id] ?: return@map register
            register.copy(columns = table.columns, rows = table.rows.map { Row(it) })
        }
        setRegisters(updated)
        state = state.copy(
            pendingImport = null,
            message = text(R.string.imported, byId.size, byId.values.sumOf { it.rows.size }),
        )
        scope.launch(Dispatchers.IO) { updated.filter { it.id in byId }.forEach(store::save) }
    }

    // ---------- export ----------

    fun export(file: File, registerId: String?) {
        val registers = state.registers.filter { registerId == null || it.id == registerId }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { file.outputStream().use { XlsxWriter.write(registers, it) } }
            }
            val message = result.fold(
                onSuccess = { text(R.string.exported) + " : " + file.name },
                onFailure = { text(R.string.export_failed, it.message ?: it.javaClass.simpleName) },
            )
            state = state.copy(message = message)
        }
    }

    private fun fail(message: String) {
        state = state.copy(loading = false, message = message)
    }

    fun messageShown() {
        state = state.copy(message = null)
    }
}
