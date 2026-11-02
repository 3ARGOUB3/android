package com.personnel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personnel.PendingImport
import com.personnel.PersonnelViewModel
import com.personnel.R
import com.personnel.core.Register
import com.personnel.core.SheetChoice

/**
 * Lets the user check each sheet before importing: the register it goes to, the row holding
 * the column names, the first line of data and the columns to keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(pending: PendingImport, registers: List<Register>, vm: PersonnelViewModel) {
    val active = pending.choices.filter { it.target != null }
    val replaced = active.mapNotNull { c -> registers.firstOrNull { it.id == c.target } }.filter { it.rows.isNotEmpty() }
    val ready = active.isNotEmpty() && active.all { it.table.columns.isNotEmpty() }
    AlertDialog(
        onDismissRequest = vm::cancelImport,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pending.fileName, style = MaterialTheme.typography.bodySmall)
                pending.choices.forEachIndexed { i, choice ->
                    if (i > 0) HorizontalDivider()
                    SheetSection(i, choice, registers, vm)
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
            TextButton(onClick = vm::confirmImport, enabled = ready) {
                Text(stringResource(R.string.import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelImport) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SheetSection(index: Int, choice: SheetChoice, registers: List<Register>, vm: PersonnelViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.sheet_summary, choice.sheet.name, choice.rowCount),
            style = MaterialTheme.typography.titleSmall,
        )

        var expanded by remember { mutableStateOf(false) }
        val target = registers.firstOrNull { it.id == choice.target }
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
                    onClick = { vm.setImportTarget(index, null); expanded = false },
                )
                registers.forEach { register ->
                    DropdownMenuItem(
                        text = { Text(register.title) },
                        onClick = { vm.setImportTarget(index, register.id); expanded = false },
                    )
                }
            }
        }
        if (target == null) return@Column

        // Rows are shown as Excel row numbers (1-based).
        RowPicker(
            label = stringResource(R.string.header_row),
            value = choice.headerRow + 1,
            min = 1,
            max = choice.rowCount,
            preview = choice.preview(choice.headerRow),
        ) { vm.setHeaderRow(index, it - 1) }
        RowPicker(
            label = stringResource(R.string.first_data_row),
            value = choice.firstDataRow + 1,
            min = choice.headerRow + 2,
            max = maxOf(choice.rowCount, choice.headerRow + 2),
            preview = choice.preview(choice.firstDataRow),
        ) { vm.setFirstDataRow(index, it - 1) }

        Text(stringResource(R.string.columns_to_import), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choice.allColumns.forEachIndexed { c, name ->
                val kept = c !in choice.excluded
                FilterChip(
                    selected = kept,
                    onClick = { vm.toggleColumn(index, c) },
                    label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = if (kept) {
                        { Icon(Icons.Filled.Check, null) }
                    } else {
                        null
                    },
                )
            }
        }
        val table = choice.table
        Text(
            if (table.columns.isEmpty()) {
                stringResource(R.string.no_columns)
            } else {
                stringResource(R.string.rows_to_import, table.rows.size, table.columns.size)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (table.columns.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/** A row number with -/+ buttons and a field to type it, plus what that row contains. */
@Composable
private fun RowPicker(label: String, value: Int, min: Int, max: Int, preview: String, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
                Icon(Icons.Filled.Remove, null)
            }
            var typed by remember(value) { mutableStateOf(value.toString()) }
            OutlinedTextField(
                value = typed,
                onValueChange = { text ->
                    typed = text.filter(Char::isDigit).take(6)
                    typed.toIntOrNull()?.takeIf { it in min..max }?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(76.dp),
            )
            IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
                Icon(Icons.Filled.Add, null)
            }
        }
        Text(
            preview.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
