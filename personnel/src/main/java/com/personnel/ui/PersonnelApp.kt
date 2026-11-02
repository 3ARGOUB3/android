package com.personnel.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personnel.PersonnelViewModel
import com.personnel.R
import com.personnel.Screen
import com.personnel.core.Register

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Actions every screen can trigger. */
class AppActions(
    val vm: PersonnelViewModel,
    val importInto: (registerId: String?) -> Unit,
    val exportTo: (register: Register?) -> Unit,
)

@Composable
fun PersonnelApp(vm: PersonnelViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Which register the next picked file is for (empty = the whole workbook).
    var importTarget by rememberSaveable { mutableStateOf("") }
    var exportTarget by rememberSaveable { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readFile(uri, displayName(context, uri), importTarget.ifEmpty { null })
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) vm.export(uri, exportTarget.ifEmpty { null })
    }
    val allFileName = stringResource(R.string.export_all_file)
    val actions = remember(vm) {
        AppActions(
            vm = vm,
            importInto = { id ->
                importTarget = id.orEmpty()
                // Some file managers do not tag Excel files with their MIME type, so allow any file.
                importPicker.launch(arrayOf("*/*"))
            },
            exportTo = { register ->
                exportTarget = register?.id.orEmpty()
                exportPicker.launch("${register?.title ?: allFileName}.xlsx")
            },
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    BackHandler(enabled = stack.size > 1) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (val screen = stack.last()) {
            Screen.Home -> HomeScreen(state, actions)
            is Screen.RegisterList -> state.register(screen.registerId)?.let { RegisterScreen(it, state, actions) }
            is Screen.Form -> state.register(screen.registerId)?.let { RowFormScreen(it, screen, state, actions) }
            Screen.Workers -> WorkersScreen(state, actions)
            is Screen.WorkerDetail -> WorkerDetailScreen(screen.workerKey, state, actions)
        }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }

    state.pendingImport?.let { ImportDialog(it, state.registers, vm) }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
