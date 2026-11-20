package com.dlab.morsio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dlab.morsio.Groq
import com.dlab.morsio.MorsioWidget
import com.dlab.morsio.Reminders
import com.dlab.morsio.Slot
import com.dlab.morsio.store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(theme: String, onTheme: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = context.store
    val prefs = store.storage.prefs
    var goal by remember { mutableIntStateOf(store.storage.goal) }
    var denied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            MorsioWidget.refresh(context)
            store.sync()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                LargeTopAppBar(
                    title = { Text("Impostazioni", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") } },
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    SectionLabel("Obiettivo giornaliero")
                    MorsioCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TrackChanges, null, tint = Palette.accent, modifier = Modifier.padding(start = 6.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("$goal kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            fun set(v: Int) { goal = v.coerceIn(1000, 5000); prefs.edit().putInt("goal", goal).apply() }
                            FilledTonalIconButton({ set(goal - 50) }) { Icon(Icons.Rounded.Remove, "Meno 50") }
                            FilledTonalIconButton({ set(goal + 50) }) { Icon(Icons.Rounded.Add, "Più 50") }
                        }
                    }
                }
                item {
                    SectionLabel("Aspetto")
                    MorsioCard(padding = 12.dp) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("system" to "Sistema", "light" to "Chiaro", "dark" to "Scuro").forEachIndexed { i, (value, label) ->
                                SegmentedButton(theme == value, { prefs.edit().putString("theme", value).apply(); onTheme(value) },
                                    SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                            }
                        }
                    }
                }
                item {
                    SectionLabel("Promemoria")
                    MorsioCard(padding = 0.dp) {
                        Column {
                            Slot.all.forEachIndexed { i, slot ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ReminderRow(slot) { denied = it }
                            }
                        }
                    }
                    Footer(if (denied) "Le notifiche sono disattivate: abilitale dalle impostazioni di Android." else "Se il pasto è già nel diario, il promemoria di quel giorno non arriva.")
                }
                item {
                    SectionLabel("Gemini")
                    MorsioCard(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrefField("Chiave API", "geminiKey", "Incolla la chiave", secret = true)
                            PrefField("Modello", "geminiModel", "gemini-flash-latest")
                        }
                    }
                    Footer("Chiave gratuita da aistudio.google.com. Se il modello è sovraccarico si passa da soli a Flash-Lite.")
                }
                item {
                    SectionLabel("Groq")
                    MorsioCard(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrefField("Chiave API", "groqKey", "Incolla la chiave", secret = true)
                            PrefField("Modello", "groqModel", Groq.DEFAULT_MODEL)
                        }
                    }
                    Footer("Chiave gratuita da console.groq.com. Se c'è, si usa prima Groq e Gemini resta di riserva.")
                }
                item {
                    SectionLabel("Server foto")
                    MorsioCard(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrefField("Indirizzo", "serverURL", "https://cibo.esempio.it", keyboard = KeyboardType.Uri)
                            PrefField("Chiave upload", "uploadKey", "Da setup.sh", secret = true)
                        }
                    }
                    Footer("Lascia vuoto per non caricare le foto.")
                }
            }
        }
    }
}

@Composable
private fun Footer(text: String) = Text(text, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun PrefField(label: String, key: String, placeholder: String, secret: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    val prefs = LocalContext.current.store.storage.prefs
    var value by remember { mutableStateOf(prefs.getString(key, "").orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 6.dp).width(118.dp))
        TextField(value, { value = it.trim(); prefs.edit().putString(key, value).apply() }, Modifier.weight(1f),
            placeholder = { Text(placeholder, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
            shape = RoundedCornerShape(12.dp), colors = fieldColors(MaterialTheme.colorScheme.surfaceContainerLow))
    }
}

/** Interruttore e orario del promemoria di una fascia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderRow(slot: String, onDenied: (Boolean) -> Unit) {
    val context = LocalContext.current
    val storage = context.store.storage
    var on by remember { mutableStateOf(storage.prefs.getBoolean(Reminders.enabledKey(slot), false)) }
    var minutes by remember { mutableIntStateOf(Reminders.minutes(storage, slot)) }
    var picking by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onDenied(!granted) }

    fun enable(value: Boolean) {
        on = value
        storage.prefs.edit().putBoolean(Reminders.enabledKey(slot), value).apply()
        if (value && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.schedule(context)
    }

    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(slotIcon(slot), null, tint = Palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(slot, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (on) {
            FilledTonalButton({ picking = true }) { Text("%02d:%02d".format(minutes / 60, minutes % 60)) }
            Spacer(Modifier.width(10.dp))
        }
        Switch(on, ::enable)
    }

    if (picking) {
        val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
        AlertDialog({ picking = false }, confirmButton = {
            TextButton({
                minutes = state.hour * 60 + state.minute
                storage.prefs.edit().putInt(Reminders.timeKey(slot), minutes).apply()
                Reminders.schedule(context)
                picking = false
            }) { Text("OK") }
        }, dismissButton = { TextButton({ picking = false }) { Text("Annulla") } }, title = { Text("Promemoria $slot") }, text = { TimePicker(state) })
    }
}
