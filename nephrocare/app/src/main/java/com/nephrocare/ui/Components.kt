package com.nephrocare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.Article
import com.nephrocare.core.Level
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding -> content(padding) }
}

/** A scrolling column with the standard screen padding. */
@Composable
fun ScrollColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) = Banner(
    text, modifier, Icons.Filled.WarningAmber,
    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, onClick,
)

@Composable
fun DisclaimerBanner(modifier: Modifier = Modifier) =
    Banner(stringResource(R.string.disclaimer_short), modifier)

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NavTile(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key == selected, onClick = { onSelected(key) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChoiceChips(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key in selected, onClick = { onToggle(key) }, label = { Text(label) })
        }
    }
}

/** Date and time buttons opening the Material pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, showTime: Boolean = true) {
    val language = currentLanguage()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.date(value.toMillis(), language), maxLines = 1)
        }
        if (showTime) {
            OutlinedButton(onClick = { pickTime = true }) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(Fmt.time(value.toLocalTime()))
            }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(d.atTime(value.toLocalTime()))
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        TimePickerDialog(value.toLocalTime(), onDismiss = { pickTime = false }) {
            onChange(value.toLocalDate().atTime(it))
            pickTime = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun TimeButton(time: LocalTime, modifier: Modifier = Modifier, onPicked: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Filled.Schedule, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(Fmt.time(time))
    }
    if (open) TimePickerDialog(time, onDismiss = { open = false }) { onPicked(it); open = false }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A history row: main text, secondary text and a delete button with confirmation. */
@Composable
fun HistoryRow(title: String, subtitle: String, modifier: Modifier = Modifier, accent: Color? = null, onDelete: (() -> Unit)?) {
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirm && onDelete != null) {
        ConfirmDialog(
            stringResource(R.string.delete_title), stringResource(R.string.delete_text), stringResource(R.string.delete),
            onConfirm = onDelete, onDismiss = { confirm = false },
        )
    }
}

fun levelColor(level: Level): Color = when (level) {
    Level.LOW -> Ok
    Level.MEDIUM -> Warning
    Level.HIGH -> Danger
}

@Composable
fun levelLabel(level: Level): String = stringResource(
    when (level) {
        Level.LOW -> R.string.level_low
        Level.MEDIUM -> R.string.level_medium
        Level.HIGH -> R.string.level_high
    },
)

@Composable
fun LevelBadge(name: String, level: Level) {
    val color = levelColor(level)
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(8.dp)) {
        Text(
            "$name: ${levelLabel(level)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Renders an article's sections. */
@Composable
fun ArticleBody(article: Article) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (article.summary.isNotBlank()) {
            Text(article.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
        article.s.forEach { section ->
            section.h?.let { SectionHeader(it) }
            section.p.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            section.b.forEach { bullet ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(bullet, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Default for "now" in forms, rounded to the minute. */
fun nowMinute(): LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)

fun LocalDate.isToday() = this == LocalDate.now()

/** Tabs that scroll when there are more than three (labels are long in some languages). */
@Composable
fun AppTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { i, title ->
            Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(title, maxLines = 1) })
        }
    }
    if (titles.size <= 3) {
        TabRow(selectedTabIndex = selected, tabs = tabs)
    } else {
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp, tabs = tabs)
    }
}
