package com.nephrocare.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nephrocare.BuildConfig
import com.nephrocare.LocaleHelper
import com.nephrocare.R
import com.nephrocare.core.BackupCrypto
import com.nephrocare.core.WrongPasswordException
import com.nephrocare.data.BackupManager
import com.nephrocare.reminders.Notifications
import com.nephrocare.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

private val LANGUAGES = listOf("ar" to "العربية", "fr" to "Français", "en" to "English", "es" to "Español")

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

fun changeLanguage(app: com.nephrocare.AppContainer, language: String) {
    app.settings.updateSettings { it.copy(language = language) }
    Notifications.createChannels(app.context)
    LocaleHelper.apply(language)
}

@Composable
fun SettingsScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var pinDialog by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; backupMode = "export" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; backupMode = "import" }
    }

    ScreenScaffold(stringResource(R.string.nav_settings), onBack = null) { padding ->
        ScrollColumn(padding) {
            SettingRow(stringResource(R.string.profile_title), stringResource(R.string.profile_subtitle)) { go(Routes.PROFILE) }

            SectionHeader(stringResource(R.string.settings_language))
            ChoiceChips(LANGUAGES, app.language(), { changeLanguage(app, it) })

            SectionHeader(stringResource(R.string.settings_display))
            ChoiceChips(
                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                settings.theme, { t -> app.settings.updateSettings { it.copy(theme = t) } },
            )
            Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(
                listOf("1.0" to stringResource(R.string.text_normal), "1.15" to stringResource(R.string.text_large), "1.3" to stringResource(R.string.text_xlarge)),
                settings.textScale.toString(), { v -> app.settings.updateSettings { it.copy(textScale = v.toFloat()) } },
            )

            SectionHeader(stringResource(R.string.settings_notifications))
            val canPost = remember(tick) { Notifications.canPost(context) }
            if (!canPost) {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_allow_notifications)) }
            }
            val exact = remember(tick) { ReminderScheduler.canUseExactAlarms(context) }
            if (!exact && Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                    }
                    tick++
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_exact_alarms)) }
            }
            val batteryFree = remember(tick) {
                Build.VERSION.SDK_INT < 23 ||
                    (context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
            }
            if (!batteryFree) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    tick++
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_battery)) }
                Text(stringResource(R.string.settings_battery_hint), style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(stringResource(R.string.settings_notif_details), settings.notificationDetails, stringResource(R.string.settings_notif_details_hint)) { v ->
                app.settings.updateSettings { it.copy(notificationDetails = v) }
            }

            SectionHeader(stringResource(R.string.settings_privacy))
            SwitchRow(stringResource(R.string.settings_pin), settings.pinHash != null, stringResource(R.string.settings_pin_hint)) { on ->
                if (on) pinDialog = true
                else {
                    app.settings.updateSettings { it.copy(pinHash = null, pinSalt = null) }
                    (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            SettingRow(stringResource(R.string.settings_backup_export), stringResource(R.string.settings_backup_export_hint)) {
                exportLauncher.launch("NephroCare_backup_${LocalDate.now()}.ncb")
            }
            SettingRow(stringResource(R.string.settings_backup_import), stringResource(R.string.settings_backup_import_hint)) {
                importLauncher.launch(arrayOf("*/*"))
            }
            SettingRow(stringResource(R.string.privacy_title), null) { go(Routes.PRIVACY) }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error)
            }

            SectionHeader(stringResource(R.string.settings_about))
            SettingRow(stringResource(R.string.about_title), "NephroCare ${BuildConfig.VERSION_NAME}") { go(Routes.ABOUT) }
            message?.let { Banner(it) }
        }
    }

    if (pinDialog) {
        PinSetupDialog(onDismiss = { pinDialog = false }) { pin ->
            val salt = BackupCrypto.newSalt()
            val saltHex = salt.joinToString("") { "%02x".format(it) }
            app.settings.updateSettings { it.copy(pinHash = BackupCrypto.hashPin(pin, salt), pinSalt = saltHex) }
            (context as? Activity)?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            pinDialog = false
        }
    }
    val mode = backupMode
    val uri = pendingUri
    if (mode != null && uri != null) {
        val okExport = stringResource(R.string.backup_done)
        val okImport = stringResource(R.string.restore_done)
        val wrong = stringResource(R.string.backup_wrong_password)
        val failed = stringResource(R.string.backup_failed)
        PasswordDialog(
            title = stringResource(if (mode == "export") R.string.settings_backup_export else R.string.settings_backup_import),
            confirmTwice = mode == "export",
            onDismiss = { backupMode = null; pendingUri = null },
        ) { password ->
            backupMode = null; pendingUri = null
            scope.launch {
                message = try {
                    if (mode == "export") { BackupManager.export(app, uri, password); okExport }
                    else { BackupManager.import(app, uri, password); okImport }
                } catch (e: WrongPasswordException) {
                    wrong
                } catch (e: Exception) {
                    failed
                }
            }
        }
    }
    if (confirmDelete) {
        val done = stringResource(R.string.delete_all_done)
        ConfirmDialog(
            stringResource(R.string.settings_delete_all), stringResource(R.string.delete_all_text), stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    BackupManager.deleteEverything(app)
                    (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    message = done
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it }, stringResource(R.string.pin_new))
                PinField(again, { again = it }, stringResource(R.string.pin_repeat))
                Text(stringResource(R.string.pin_forget_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = pin.length >= 4 && pin == again, onClick = { onSet(pin) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() }.take(8)) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordDialog(title: String, confirmTwice: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (confirmTwice) R.string.backup_password_hint else R.string.restore_password_hint))
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.backup_password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (confirmTwice) {
                    OutlinedTextField(again, { again = it }, label = { Text(stringResource(R.string.pin_repeat)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 6 && (!confirmTwice || password == again),
                onClick = { onOk(password) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ProfileScreen(back: () -> Unit) {
    val app = LocalApp.current
    val p by app.settings.profile.collectAsState()
    var name by rememberSaveable { mutableStateOf(p.name) }
    var birthYear by rememberSaveable { mutableStateOf(p.birthYear?.toString() ?: "") }
    var treatment by rememberSaveable { mutableStateOf(p.treatment) }
    var since by rememberSaveable { mutableStateOf(p.dialysisSince) }
    var center by rememberSaveable { mutableStateOf(p.center) }
    var nephrologist by rememberSaveable { mutableStateOf(p.nephrologist) }
    var nephroPhone by rememberSaveable { mutableStateOf(p.nephrologistPhone) }
    var bloodType by rememberSaveable { mutableStateOf(p.bloodType) }
    var allergies by rememberSaveable { mutableStateOf(p.allergies) }
    var emergencyName by rememberSaveable { mutableStateOf(p.emergencyName) }
    var emergencyPhone by rememberSaveable { mutableStateOf(p.emergencyPhone) }

    ScreenScaffold(stringResource(R.string.profile_title), back) { padding ->
        ScrollColumn(padding) {
            Banner(stringResource(R.string.profile_privacy_note))
            TextInput(name, { name = it }, stringResource(R.string.profile_name))
            NumberField(birthYear, { birthYear = it }, stringResource(R.string.profile_birth_year), decimal = false)
            Text(stringResource(R.string.profile_treatment), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(
                listOf(
                    "HD" to stringResource(R.string.treatment_hd),
                    "PD" to stringResource(R.string.treatment_pd),
                    "CKD" to stringResource(R.string.treatment_ckd),
                    "TX" to stringResource(R.string.treatment_tx),
                ),
                treatment, { treatment = it },
            )
            TextInput(since, { since = it }, stringResource(R.string.profile_since))
            TextInput(center, { center = it }, stringResource(R.string.profile_center))
            TextInput(nephrologist, { nephrologist = it }, stringResource(R.string.profile_nephrologist))
            TextInput(nephroPhone, { nephroPhone = it }, stringResource(R.string.profile_nephrologist_phone), keyboardType = KeyboardType.Phone)
            TextInput(bloodType, { bloodType = it }, stringResource(R.string.profile_blood_type))
            TextInput(allergies, { allergies = it }, stringResource(R.string.profile_allergies), singleLine = false)
            TextInput(emergencyName, { emergencyName = it }, stringResource(R.string.profile_emergency_name))
            TextInput(emergencyPhone, { emergencyPhone = it }, stringResource(R.string.profile_emergency_phone), keyboardType = KeyboardType.Phone)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    app.settings.updateProfile {
                        it.copy(
                            name = name.trim(), birthYear = parseInt(birthYear)?.takeIf { y -> y in 1900..2100 }, treatment = treatment,
                            dialysisSince = since.trim(), center = center.trim(), nephrologist = nephrologist.trim(),
                            nephrologistPhone = nephroPhone.trim(), bloodType = bloodType.trim(), allergies = allergies.trim(),
                            emergencyName = emergencyName.trim(), emergencyPhone = emergencyPhone.trim(),
                        )
                    }
                    back()
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    ScreenScaffold(stringResource(R.string.about_title), back) { padding ->
        ScrollColumn(padding) {
            Text("NephroCare ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge)
            SectionHeader(stringResource(R.string.about_safety_title))
            Text(stringResource(R.string.disclaimer_full), style = MaterialTheme.typography.bodyLarge)
            SectionHeader(stringResource(R.string.about_review_title))
            Text(stringResource(R.string.about_review_text), style = MaterialTheme.typography.bodyLarge)
            SectionHeader(stringResource(R.string.about_sources_title))
            Text(stringResource(R.string.about_sources_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrivacyScreen(back: () -> Unit) {
    ScreenScaffold(stringResource(R.string.privacy_title), back) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.settings.collectAsState()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            PinField(pin, { pin = it; wrong = false }, stringResource(R.string.lock_enter_pin))
            if (wrong) Text(stringResource(R.string.lock_wrong), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val salt = settings.pinSalt?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
                    val hash = settings.pinHash
                    if (salt == null || hash == null || BackupCrypto.constantTimeEquals(BackupCrypto.hashPin(pin, salt), hash)) onUnlocked()
                    else { wrong = true; pin = "" }
                },
            ) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}

@Composable
fun OnboardingScreen() {
    val app = LocalApp.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.settings.updateSettings { it.copy(onboardingDone = true) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium)
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_choose_language), style = MaterialTheme.typography.titleMedium)
                    LANGUAGES.forEach { (code, label) ->
                        val selected = app.language() == code
                        Button(
                            onClick = { changeLanguage(app, code) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selected) androidx.compose.material3.ButtonDefaults.buttonColors()
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
                    }
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
                }
                1 -> {
                    Text(stringResource(R.string.about_safety_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.disclaimer_full), style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(accepted, { accepted = it })
                        Text(stringResource(R.string.onboarding_accept))
                    }
                    Button(enabled = accepted, onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
                }
                else -> {
                    Text(stringResource(R.string.onboarding_name), style = MaterialTheme.typography.titleMedium)
                    TextInput(name, { name = it }, stringResource(R.string.profile_name))
                    Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            app.settings.updateProfile { it.copy(name = name.trim()) }
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else app.settings.updateSettings { it.copy(onboardingDone = true) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_start)) }
                }
            }
            Text(stringResource(R.string.disclaimer_short), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
