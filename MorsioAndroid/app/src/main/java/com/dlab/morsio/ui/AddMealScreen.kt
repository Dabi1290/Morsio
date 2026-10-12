package com.dlab.morsio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dlab.morsio.FoodItem
import com.dlab.morsio.Meal
import com.dlab.morsio.OpenFoodFacts
import com.dlab.morsio.Slot
import com.dlab.morsio.jpeg
import com.dlab.morsio.newId
import com.dlab.morsio.pastItems
import com.dlab.morsio.pastMeals
import com.dlab.morsio.store
import com.dlab.morsio.toBitmap
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Crea un pasto nuovo, oppure modifica [editing] (se non è ancora salvato è un pasto nuovo precompilato, es. dal piano). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(editing: Meal?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = context.store
    val scope = rememberCoroutineScope()
    val meals by store.meals.collectAsState()
    val plan by store.plan.collectAsState()
    val base = remember { editing ?: Meal(name = "") }
    val isNew = remember { store.meals.value.none { it.id == base.id } }

    var preview by remember {
        mutableStateOf(if (base.hasPhoto) store.storage.photoFile(base.id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } else null)
    }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var note by remember { mutableStateOf(if (base.fromPlan && base.kcal == 0) base.items.orEmpty().joinToString(", ") { it.name } else "") }
    var name by remember { mutableStateOf(base.name) }
    var kcal by remember { mutableDoubleStateOf(base.kcal.toDouble()) }
    var protein by remember { mutableDoubleStateOf(base.protein) }
    var carbs by remember { mutableDoubleStateOf(base.carbs) }
    var fat by remember { mutableDoubleStateOf(base.fat) }
    val items = remember { mutableStateListOf<FoodItem>().apply { addAll(base.items.orEmpty()) } }
    var dateTime by remember { mutableStateOf(base.dateTime) }
    var slot by remember { mutableStateOf(base.resolvedSlot) }
    var fromPlan by remember { mutableStateOf(base.fromPlan) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun pick(uri: Uri) {
        pickedUri = uri
        scope.launch { preview = withContext(Dispatchers.IO) { runCatching { context.jpeg(uri, 1024, 80).toBitmap() }.getOrNull() } }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(::pick) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::pick) }

    fun addItem(item: FoodItem) {
        // valori inseriti a mano prima del primo alimento: diventano un alimento, così non si perdono
        if (items.isEmpty() && kcal > 0) items.add(FoodItem.fromTotals(name.ifEmpty { "Altro" }, 100.0, kcal, protein, carbs, fat))
        items.add(item)
    }

    fun lookup(code: String) {
        busy = true
        scope.launch {
            try {
                val product = store.storage.products[code]?.copy(id = newId()) ?: OpenFoodFacts.product(code)
                if (product != null) {
                    addItem(product)
                    if (name.isEmpty()) name = product.name
                    notice = null
                } else {
                    // non è nel database: lo compila l'utente e al salvataggio resta in memoria per questo codice
                    addItem(FoodItem.empty(barcode = code))
                    notice = "Prodotto $code non trovato: scrivi nome e valori dell'etichetta per 100 g. Salvando il pasto, la prossima scansione lo riconosce."
                }
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { it.rawValue?.let(::lookup) }
            .addOnFailureListener { error = "Scanner non disponibile: ${it.message}" }
    }

    fun current(): Meal = base.copy(
        name = name, kcal = kcal.roundToInt(), protein = protein, carbs = carbs, fat = fat,
        time = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        slot = slot, fromPlan = fromPlan, hasPhoto = preview != null,
    ).withItems(items.toList())

    fun save() {
        val uri = pickedUri
        scope.launch {
            // in modifica la foto salvata è già compressa: la riscrivo solo se è cambiata
            val photo = uri?.let { withContext(Dispatchers.IO) { context.jpeg(it, 512, 40) } }
            store.save(current().copy(analysis = null), photo)  // salvataggio manuale: i valori dell'utente vincono sull'IA
            onClose()
        }
    }

    fun analyze() {
        val uri = pickedUri
        val existing = if (uri == null && preview != null) store.storage.photoFile(base.id) else null
        scope.launch {
            val (photo, ai) = withContext(Dispatchers.IO) {
                (uri?.let { context.jpeg(it, 512, 40) }) to (uri?.let { context.jpeg(it, 1024, 70) } ?: existing?.readBytes())
            }
            store.analyzeInBackground(current().copy(name = name.ifEmpty { "Nuovo pasto" }), photo, ai, note)
            onClose()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (isNew) "Nuovo pasto" else "Modifica pasto", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClose) { Icon(Icons.Rounded.Close, "Annulla") } },
                    actions = {
                        TextButton(::save, enabled = name.isNotBlank() && !busy) { Text("Salva", fontWeight = FontWeight.Bold) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // foto e sorgenti
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateContentSize()) {
                        preview?.let { bmp -> PhotoPreview(bmp) { preview = null; pickedUri = null } }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Tile("Foto", Icons.Rounded.CameraAlt, Modifier.weight(1f)) {
                                val file = File(context.cacheDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                                cameraUri = FileProvider.getUriForFile(context, "com.dlab.morsio.files", file)
                                camera.launch(cameraUri!!)
                            }
                            Tile("Galleria", Icons.Rounded.Image, Modifier.weight(1f)) {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            Tile("Barcode", Icons.Rounded.QrCodeScanner, Modifier.weight(1f)) { scan() }
                            Tile("Storico", Icons.Rounded.History, Modifier.weight(1f), enabled = meals.isNotEmpty()) { showHistory = true }
                        }
                    }
                }

                item {
                    SectionLabel("Descrizione (opzionale)")
                    TextField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = { Text("Dettagli, es. porzione da 150 g") },
                        shape = RoundedCornerShape(20.dp), colors = fieldColors(), maxLines = 4)
                }

                item {
                    Button(::analyze, Modifier.fillMaxWidth().height(54.dp), enabled = !busy && (preview != null || note.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White)) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analizza con IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Il pasto viene salvato subito e l'analisi continua in background: potrai controllare e modificare i valori dopo.",
                        Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (items.isNotEmpty()) {
                    item {
                        SectionLabel("Alimenti")
                        MorsioCard(padding = 0.dp) {
                            Column {
                                items.forEachIndexed { i, item ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    key(item.id) {
                                        ItemRow(item, onChange = { items[i] = it }, onDelete = { items.removeAt(i) })
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                AddItemButton { addItem(FoodItem.empty()) }
                            }
                        }
                        Text(notice ?: "Correggi i grammi e calorie e macro si ricalcolano. Tocca le kcal per correggere i valori.",
                            Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    SectionLabel(if (items.isEmpty()) "Valori" else "Totale")
                    MorsioCard(padding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = { Text("Nome del pasto") },
                                singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(MaterialTheme.colorScheme.surfaceContainerLow))
                            if (items.isEmpty()) {
                                ValueRow("Kcal", Palette.accent) { NumberField(kcal, { kcal = it }) }
                                ValueRow("Proteine (g)", Palette.protein) { NumberField(protein, { protein = it }) }
                                ValueRow("Carboidrati (g)", Palette.carbs) { NumberField(carbs, { carbs = it }) }
                                ValueRow("Grassi (g)", Palette.fat) { NumberField(fat, { fat = it }) }
                                AddItemButton { addItem(FoodItem.empty()) }
                            } else {
                                ValueRow("Kcal", Palette.accent) { Total(items.sumOf { it.kcal }, "") }
                                ValueRow("Proteine", Palette.protein) { Total(items.sumOf { it.protein }, " g") }
                                ValueRow("Carboidrati", Palette.carbs) { Total(items.sumOf { it.carbs }, " g") }
                                ValueRow("Grassi", Palette.fat) { Total(items.sumOf { it.fat }, " g") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            WhenRow(dateTime) { dateTime = it }
                            Text("Pasto", style = MaterialTheme.typography.bodyLarge)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                Slot.all.forEachIndexed { i, s ->
                                    SegmentedButton(slot == s, { slot = s }, SegmentedButtonDefaults.itemShape(i, Slot.all.size),
                                        icon = {}, label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) })
                                }
                            }
                            if (plan != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Segue il piano", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(fromPlan, { fromPlan = it })
                                }
                            }
                        }
                    }
                }

                error?.let { item { Text(it, color = Palette.over, modifier = Modifier.padding(horizontal = 6.dp)) } }
            }
        }
    }

    if (showHistory) {
        HistorySheet(meals, onDismiss = { showHistory = false },
            pickMeal = { past ->
                // stessi alimenti e valori, senza IA (foto e orario restano quelli attuali)
                name = past.name
                kcal = past.kcal.toDouble(); protein = past.protein; carbs = past.carbs; fat = past.fat
                items.clear(); items.addAll(past.items.orEmpty().map { it.copy(id = newId()) })
                showHistory = false
            },
            pickItem = { addItem(it.copy(id = newId())); showHistory = false })
    }
}

@Composable
fun fieldColors(container: Color = MaterialTheme.colorScheme.surfaceContainer) = TextFieldDefaults.colors(
    focusedContainerColor = container, unfocusedContainerColor = container, disabledContainerColor = container,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun PhotoPreview(bitmap: Bitmap, onRemove: () -> Unit) {
    Box {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
        FilledTonalIconButton(onRemove, Modifier.align(Alignment.TopEnd).padding(10.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)) {
            Icon(Icons.Rounded.Close, "Rimuovi foto")
        }
    }
}

@Composable
private fun Tile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Palette.accent.copy(alpha = if (enabled) 0.15f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = Palette.accent.copy(alpha = if (enabled) 1f else 0.4f)
        Icon(icon, null, tint = tint)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun AddItemButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AddCircle, null, tint = Palette.accent)
        Spacer(Modifier.width(12.dp))
        Text("Aggiungi alimento", color = Palette.accent, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ValueRow(label: String, color: Color, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun Total(value: Double, unit: String) =
    Text("${value.roundToInt()}$unit", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

private fun format(v: Double) = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(java.util.Locale.ITALIAN, v)

/** Campo numerico che accetta virgola o punto; si riallinea se il valore cambia da fuori (es. con i pulsanti). */
@Composable
fun NumberField(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier.width(96.dp), color: Color = Color.Unspecified) {
    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        val typed = text.replace(',', '.').toDoubleOrNull() ?: if (text.isEmpty()) 0.0 else null
        if (typed == null || kotlin.math.abs(typed - value) > 0.01) text = format(value)
    }
    TextField(
        text, { t ->
            text = t
            t.replace(',', '.').toDoubleOrNull()?.let(onChange) ?: if (t.isEmpty()) onChange(0.0) else Unit
        },
        modifier, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp), colors = fieldColors(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

/** Riga di un alimento: nome, grammi modificabili e, aprendo i valori, kcal e macro correggibili. */
@Composable
private fun ItemRow(item: FoodItem, onChange: (FoodItem) -> Unit, onDelete: () -> Unit) {
    // un alimento aggiunto a mano (ancora senza nome) si apre già sui valori
    var expanded by remember { mutableStateOf(item.name.isEmpty()) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(item.name, { onChange(item.copy(name = it)) }, Modifier.weight(1f), placeholder = { Text("Alimento") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = fieldColors(Color.Transparent))
            IconButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, "Elimina alimento", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${item.kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.accent)
                    Text("P ${item.protein.roundToInt()} · C ${item.carbs.roundToInt()} · G ${item.fat.roundToInt()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ExpandMore, if (expanded) "Chiudi valori" else "Modifica valori", Modifier.size(18.dp).rotate(arrow),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton({ onChange(item.copy(grams = (item.grams - 10).coerceAtLeast(0.0))) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Remove, "Meno 10 g") }
            NumberField(item.grams, { onChange(item.copy(grams = it)) }, Modifier.width(76.dp).padding(horizontal = 4.dp))
            Text("g", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton({ onChange(item.copy(grams = item.grams + 10)) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Add, "Più 10 g") }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // si mostra il valore della porzione ma si salva per 100 g, così i grammi continuano a riscalare
                    fun portion(get: (FoodItem) -> Double, set: (FoodItem, Double) -> FoodItem): Pair<Double, (Double) -> Unit> =
                        get(item) * item.grams / 100 to { v -> onChange(set(item, if (item.grams > 0) v * 100 / item.grams else v)) }
                    MacroField("Kcal", Palette.accent, portion({ it.kcal100 }, { i, v -> i.copy(kcal100 = v) }), Modifier.weight(1f))
                    MacroField("Proteine", Palette.protein, portion({ it.protein100 }, { i, v -> i.copy(protein100 = v) }), Modifier.weight(1f))
                    MacroField("Carbo", Palette.carbs, portion({ it.carbs100 }, { i, v -> i.copy(carbs100 = v) }), Modifier.weight(1f))
                    MacroField("Grassi", Palette.fat, portion({ it.fat100 }, { i, v -> i.copy(fat100 = v) }), Modifier.weight(1f))
                }
                Text("Valori per ${item.grams.roundToInt()} g. Cambiando i grammi si riscalano.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MacroField(label: String, color: Color, binding: Pair<Double, (Double) -> Unit>, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(4.dp))
        NumberField(binding.first, binding.second, Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenRow(value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quando", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton({ pickDate = true }) { Text(value.format(DateTimeFormatter.ofPattern("d MMM yyyy", italian))) }
        FilledTonalButton({ pickTime = true }) { Text(value.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog({ pickDate = false }, confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(value.toLocalTime())) }
                pickDate = false
            }) { Text("OK") }
        }, dismissButton = { TextButton({ pickDate = false }) { Text("Annulla") } }) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
        AlertDialog({ pickTime = false }, confirmButton = {
            TextButton({ onChange(value.withHour(state.hour).withMinute(state.minute)); pickTime = false }) { Text("OK") }
        }, dismissButton = { TextButton({ pickTime = false }) { Text("Annulla") } }, text = { TimePicker(state) })
    }
}

/** Pasti e alimenti già inseriti, da riusare senza chiamare l'IA. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(meals: List<Meal>, onDismiss: () -> Unit, pickMeal: (Meal) -> Unit, pickItem: (FoodItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = context.store.storage.prefs
    var showsItems by remember { mutableStateOf(prefs.getBoolean("historyShowsItems", false)) }
    val pastM = remember(meals, query) { pastMeals(meals, query) }
    val pastI = remember(meals, query) { pastItems(meals, query) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Storico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cerca pasto o alimento") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Pasti", true to "Alimenti").forEachIndexed { i, (value, label) ->
                    SegmentedButton(showsItems == value, { showsItems = value; prefs.edit().putBoolean("historyShowsItems", value).apply() },
                        SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(label) }
                }
            }
            val empty = if (showsItems) pastI.isEmpty() else pastM.isEmpty()
            if (empty) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (query.isNotEmpty()) "Nessun risultato per \"$query\"" else if (showsItems) "Nessun alimento" else "Nessun pasto",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (query.isEmpty()) Text("Quello che salvi compare qui, pronto da riusare.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.height(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (showsItems) items(pastI, key = { it.id }) { item ->
                    HistoryRow(item.name, "${item.grams.roundToInt()} g · P ${item.protein.roundToInt()} · C ${item.carbs.roundToInt()} · G ${item.fat.roundToInt()}",
                        item.kcal.roundToInt(), null) { pickItem(item) }
                } else items(pastM, key = { it.id }) { meal ->
                    HistoryRow(meal.name, meal.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italian)).replaceFirstChar { it.uppercase() },
                        meal.kcal, meal) { pickMeal(meal) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(title: String, subtitle: String, kcal: Int, meal: Meal?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meal != null) {
            MealThumb(meal, 44.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$kcal kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.accent)
    }
}
