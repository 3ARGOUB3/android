package com.personnel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personnel.R
import com.personnel.Screen
import com.personnel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkersScreen(state: UiState, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(state.directory, query) { state.directory.search(query) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workers)) },
                navigationIcon = {
                    IconButton(onClick = actions.vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
        ) {
            item {
                SearchField(query, { query = it })
                Text(
                    stringResource(R.string.workers_count, results.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(results.take(300), key = { it.key }) { worker ->
                ListItem(
                    headlineContent = { Text(worker.name.ifBlank { "—" }, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(listOf(worker.matricule, worker.function).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    trailingContent = { Text(worker.records.size.toString()) },
                    modifier = Modifier.clickable { actions.vm.open(Screen.WorkerDetail(worker.key)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailScreen(workerKey: String, state: UiState, actions: AppActions) {
    val worker = state.directory.withKey(workerKey)
    var chooseRegister by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(worker?.let { w -> w.name.ifBlank { w.matricule } }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = actions.vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (worker != null) {
                ExtendedFloatingActionButton(
                    onClick = { chooseRegister = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.add_for_worker)) },
                )
            }
        },
    ) { padding ->
        if (worker == null) {
            // The last line about this worker was deleted or changed.
            Text(stringResource(R.string.worker_gone), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(worker.name, style = MaterialTheme.typography.titleLarge)
                        if (worker.matricule.isNotBlank()) {
                            Text(stringResource(R.string.matricule_value, worker.matricule))
                        }
                        if (worker.function.isNotBlank()) {
                            Text(stringResource(R.string.function_value, worker.function))
                        }
                        Text(
                            stringResource(R.string.records_count, worker.records.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(worker.records.asReversed()) { record ->
                RowCard(record.register, record.row, title = record.register.title) {
                    actions.vm.open(Screen.Form(record.register.id, record.rowIndex))
                }
            }
        }
    }

    if (chooseRegister && worker != null) {
        AlertDialog(
            onDismissRequest = { chooseRegister = false },
            title = { Text(stringResource(R.string.choose_register)) },
            text = {
                LazyColumn {
                    items(state.registers, key = { it.id }) { register ->
                        ListItem(
                            headlineContent = { Text(register.title) },
                            modifier = Modifier.clickable {
                                chooseRegister = false
                                actions.vm.open(Screen.Form(register.id, null, worker.key))
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseRegister = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
