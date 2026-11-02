package com.personnel.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.personnel.core.Register
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = text(R.string.app_name),
        icon = painterResource("icon.png"),
        state = rememberWindowState(size = DpSize(1150.dp, 780.dp)),
    ) {
        val scope = rememberCoroutineScope()
        val app = remember { AppState(scope) }
        PersonnelTheme {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (Lang.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                PersonnelApp(app, window)
            }
        }
    }
}

/** Actions every screen can trigger. */
class AppActions(
    val vm: AppState,
    val importInto: (registerId: String?) -> Unit,
    val exportTo: (register: Register?) -> Unit,
)

@Composable
fun PersonnelApp(vm: AppState, frame: Frame) {
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }

    val actions = remember(vm) {
        AppActions(
            vm = vm,
            importInto = { id -> pickExcelFile(frame)?.let { vm.readFile(it, id) } },
            exportTo = { register ->
                val name = "${register?.title ?: text(R.string.export_all_file)}.xlsx"
                pickSaveFile(frame, name)?.let { vm.export(it, register?.id) }
            },
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Box(
        Modifier.fillMaxSize().onEscape { if (vm.stack.last() !is Screen.Form) vm.back() },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Wide windows: keep forms and lists readable; register tables use the full width.
        val wide = vm.stack.last() is Screen.RegisterList
        Box(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxSize().widthIn(max = 900.dp)) {
            when (val screen = vm.stack.last()) {
                Screen.Home -> HomeScreen(state, actions)
                is Screen.RegisterList -> state.register(screen.registerId)?.let { RegisterScreen(it, state, actions) }
                is Screen.Form -> state.register(screen.registerId)?.let { RowFormScreen(it, screen, state, actions) }
                Screen.Workers -> WorkersScreen(state, actions)
                is Screen.WorkerDetail -> WorkerDetailScreen(screen.workerKey, state, actions)
            }
        }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.pendingImport?.let { ImportDialog(it, state.registers, vm) }
}

/** Escape goes back (except in the form, where it could lose typed values). */
private fun Modifier.onEscape(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
        action()
        true
    } else {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportDialog(pending: PendingImport, registers: List<Register>, vm: AppState) {
    val replaced = pending.targets.filterNotNull().mapNotNull { id -> registers.firstOrNull { it.id == id } }
        .filter { it.rows.isNotEmpty() }
    AlertDialog(
        onDismissRequest = vm::cancelImport,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pending.fileName, style = MaterialTheme.typography.bodySmall)
                pending.tables.forEachIndexed { i, table ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.sheet_summary, table.sheetName, table.rows.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            table.columns.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                        var expanded by remember { mutableStateOf(false) }
                        val target = registers.firstOrNull { it.id == pending.targets[i] }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = target?.title ?: stringResource(R.string.skip_sheet),
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text(stringResource(R.string.import_into)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.skip_sheet)) },
                                    onClick = { vm.setImportTarget(i, null); expanded = false },
                                )
                                registers.forEach { register ->
                                    DropdownMenuItem(
                                        text = { Text(register.title) },
                                        onClick = { vm.setImportTarget(i, register.id); expanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (replaced.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.import_replaces,
                            replaced.joinToString("\n") { "• ${it.title} (${it.rows.size})" },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::confirmImport, enabled = pending.targets.any { it != null }) {
                Text(stringResource(R.string.import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelImport) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---------- files ----------

private fun pickExcelFile(frame: Frame): File? {
    val dialog = FileDialog(frame, text(R.string.import_file), FileDialog.LOAD).apply {
        // Windows uses the wildcard in the file name, other systems the filter.
        file = "*.xlsx;*.xls"
        setFilenameFilter { _, name -> name.endsWith(".xlsx", true) || name.endsWith(".xls", true) }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

private fun pickSaveFile(frame: Frame, suggested: String): File? {
    val dialog = FileDialog(frame, text(R.string.export_file), FileDialog.SAVE).apply {
        file = suggested.replace(Regex("[\\\\/:*?\"<>|]"), " ")
        isVisible = true
    }
    val name = dialog.file ?: return null
    val withExtension = if (name.endsWith(".xlsx", true)) name else "$name.xlsx"
    return File(dialog.directory, withExtension)
}

// ---------- theme ----------

private val LightColors = lightColorScheme(primary = Color(0xFF0D47A1), secondary = Color(0xFF4A5C79))
private val DarkColors = darkColorScheme(primary = Color(0xFFA9C7FF), secondary = Color(0xFFBBC7DB))

@Composable
fun PersonnelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
