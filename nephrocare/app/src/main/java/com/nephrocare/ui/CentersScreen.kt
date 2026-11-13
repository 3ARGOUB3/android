package com.nephrocare.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.OutlinedButton
import com.nephrocare.core.CenterRow
import com.nephrocare.core.CentersCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.TextNormalizer
import com.nephrocare.data.Center
import kotlinx.coroutines.launch

fun centerTypeLabel(type: String): Int = when (type) {
    "HOSPITAL" -> R.string.center_hospital
    "NEPHROLOGIST" -> R.string.center_nephrologist
    else -> R.string.center_dialysis
}

@Composable
fun CentersScreen(back: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val centers by app.db.centers().observeAll().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<Center?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val importedText = stringResource(R.string.centers_imported)
    val exportedText = stringResource(R.string.centers_exported)
    val failedText = stringResource(R.string.centers_csv_failed)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                message = try {
                    val added = withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                        val existing = app.db.centers().all().map { key(it.name, it.city) }.toSet()
                        val fresh = CentersCsv.parse(text)
                            .filter { key(it.name, it.city) !in existing }
                            .distinctBy { key(it.name, it.city) }
                            .map { Center(name = it.name, type = it.type, city = it.city, address = it.address, phone = it.phone, notes = it.notes) }
                        app.db.centers().insertAll(fresh)
                        fresh.size
                    }
                    String.format(importedText, added)
                } catch (e: Exception) {
                    failedText
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                message = try {
                    withContext(Dispatchers.IO) {
                        val rows = app.db.centers().all().map { CenterRow(it.name, it.type, it.city, it.address, it.phone, it.notes) }
                        // A BOM lets Excel open the Arabic text correctly.
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(("\uFEFF" + CentersCsv.format(rows)).toByteArray(Charsets.UTF_8)) }
                    }
                    exportedText
                } catch (e: Exception) {
                    failedText
                }
            }
        }
    }

    ScreenScaffold(
        stringResource(R.string.section_centers), back,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { editing = Center(name = "") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add)) },
            )
        },
    ) { padding ->
        val q = TextNormalizer.normalize(query.trim())
        val shown = centers
            .filter { type == "ALL" || it.type == type }
            .filter { q.isEmpty() || TextNormalizer.normalize("${it.name} ${it.city} ${it.address}").contains(q) }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Banner(stringResource(R.string.centers_intro)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Text(" " + stringResource(R.string.centers_import), maxLines = 1)
                    }
                    OutlinedButton(onClick = { exportLauncher.launch("NephroCare_centres.csv") }, enabled = centers.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Text(" " + stringResource(R.string.centers_export), maxLines = 1)
                    }
                }
            }
            item { Text(stringResource(R.string.centers_csv_hint), style = MaterialTheme.typography.bodySmall) }
            message?.let { m -> item { Banner(m) } }
            item {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.centers_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ChoiceChips(
                    listOf("ALL" to stringResource(R.string.food_all)) +
                        listOf("CENTER", "HOSPITAL", "NEPHROLOGIST").map { it to stringResource(centerTypeLabel(it)) },
                    type, { type = it },
                )
            }
            if (shown.isEmpty()) item { EmptyState(stringResource(R.string.centers_empty)) }
            items(shown, key = { it.id }) { c ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(stringResource(centerTypeLabel(c.type)), c.city).filter { it.isNotBlank() }.joinToString(" · "),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (c.address.isNotBlank()) Text(c.address, style = MaterialTheme.typography.bodySmall)
                            if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                            if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            if (c.phone.isNotBlank()) {
                                IconButton(onClick = { dial(context, c.phone) }) { Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.call)) }
                            }
                            IconButton(onClick = {
                                val query = Uri.encode(listOf(c.name, c.address, c.city).filter { it.isNotBlank() }.joinToString(", "))
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))) }
                            }) { Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.map)) }
                            IconButton(onClick = { editing = c }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit)) }
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(36.dp)) }
        }
    }

    editing?.let { current ->
        CenterDialog(
            current,
            onDismiss = { editing = null },
            onSave = { c ->
                scope.launch { if (c.id == 0L) app.db.centers().insert(c) else app.db.centers().update(c) }
                editing = null
            },
            onDelete = if (current.id != 0L) ({ scope.launch { app.db.centers().delete(current) }; editing = null }) else null,
        )
    }
}

@Composable
private fun CenterDialog(initial: Center, onDismiss: () -> Unit, onSave: (Center) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var city by remember { mutableStateOf(initial.city) }
    var address by remember { mutableStateOf(initial.address) }
    var phone by remember { mutableStateOf(initial.phone) }
    var notes by remember { mutableStateOf(initial.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.add else R.string.edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, stringResource(R.string.center_name))
                ChoiceChips(listOf("CENTER", "HOSPITAL", "NEPHROLOGIST").map { it to stringResource(centerTypeLabel(it)) }, type, { type = it })
                TextInput(city, { city = it }, stringResource(R.string.center_city))
                TextInput(address, { address = it }, stringResource(R.string.center_address))
                TextInput(phone, { phone = it }, stringResource(R.string.center_phone), keyboardType = KeyboardType.Phone)
                TextInput(notes, { notes = it }, stringResource(R.string.note_optional), singleLine = false)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), type = type, city = city.trim(), address = address.trim(), phone = phone.trim(), notes = notes.trim()))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun key(name: String, city: String) = TextNormalizer.normalize(name.trim() + "|" + city.trim())
