package com.dlab.morsio.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import com.dlab.morsio.Meal
import com.dlab.morsio.PlanMeal
import com.dlab.morsio.Slot
import com.dlab.morsio.kcal
import com.dlab.morsio.on
import com.dlab.morsio.store
import com.dlab.morsio.toMeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val italian: Locale = Locale.ITALIAN
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italian)).replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(padding: PaddingValues, day: LocalDate, onDay: (LocalDate) -> Unit, onEdit: (Meal) -> Unit, onSettings: () -> Unit) {
    val store = LocalContext.current.store
    val meals by store.meals.collectAsState()
    val plan by store.plan.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    val dayMeals = meals.on(day).sortedBy { it.time }
    val planDay = plan?.day(day)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayTitle(day), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                FilledTonalIconButton({ showCalendar = true }, colors = buttonColors) { Icon(Icons.Rounded.CalendarMonth, "Calendario") }
                FilledTonalIconButton(onSettings, colors = buttonColors) { Icon(Icons.Rounded.Settings, "Impostazioni") }
            }
        }
        item { DayStrip(day, meals, store.storage.goal) { onDay(it) } }
        item { SummaryCard(dayMeals, store.storage.goal, store.adherence(day)) }
        for (slot in Slot.all) {
            val slotMeals = dayMeals.filter { it.resolvedSlot == slot }
            val planned = planDay?.meal(slot)
            if (slotMeals.isEmpty() && planned == null) continue
            item(key = "h-$slot") {
                Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(slotIcon(slot), null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(slot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (slotMeals.any { it.fromPlan }) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Verified, "Piano seguito", tint = Palette.accent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (planned != null && planned.kcal > 0) {
                        Text("piano ${planned.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(slotMeals, key = { it.id }) { MealCard(it, onEdit) }
            if (slotMeals.isEmpty() && planned != null) {
                item(key = "p-$slot") { PlanSuggestionCard(planned) { onEdit(planned.toMeal(day)) } }
            }
        }
        if (dayMeals.isEmpty() && planDay == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nessun pasto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Fotografa quello che mangi: l'IA stima calorie e macro.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showCalendar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showCalendar = false
                }) { Text("Vai") }
            },
            dismissButton = { TextButton({ showCalendar = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }
}

/** Gli ultimi 14 giorni e i prossimi 7, con un mini anello ciascuno. */
@Composable
private fun DayStrip(selected: LocalDate, meals: List<Meal>, goal: Int, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = (-13L..7L).map { today.plusDays(it) }
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem(maxOf(0, days.indexOf(today) - 2)) }  // oggi in vista, con un paio di giorni prima
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { d ->
            val isSelected = d == selected
            Column(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) Palette.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSelect(d) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, italian).uppercase().take(3), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = if (d == today) Palette.accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    CalorieRing(meals.on(d).kcal.toFloat() / goal, Modifier.size(34.dp), lineWidth = 4.dp)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(meals: List<Meal>, goal: Int, adherence: Pair<Int, Int>?) {
    val eaten = meals.kcal
    val left = goal - eaten
    MorsioCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                CalorieRing(eaten.toFloat() / goal, Modifier.size(128.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${abs(left)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = if (left < 0) Palette.over else MaterialTheme.colorScheme.onSurface)
                    Text(if (left >= 0) "kcal rimaste" else "kcal in più", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$eaten / $goal kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                MacroSplitBar(meals.sumOf { it.protein }, meals.sumOf { it.carbs }, meals.sumOf { it.fat })
                MacroRow("Proteine", meals.sumOf { it.protein }, Palette.protein)
                MacroRow("Carboidrati", meals.sumOf { it.carbs }, Palette.carbs)
                MacroRow("Grassi", meals.sumOf { it.fat }, Palette.fat)
                if (adherence != null) {
                    Text("Piano ${adherence.first}/${adherence.second}", style = MaterialTheme.typography.labelLarge,
                        color = Palette.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealCard(meal: Meal, onEdit: (Meal) -> Unit) {
    val store = LocalContext.current.store
    var menu by remember { mutableStateOf(false) }
    val pending = meal.analysis != null && meal.analysis.error == null
    val failed = meal.analysis?.error
    Box {
        MorsioCard(Modifier.clip(RoundedCornerShape(26.dp)).combinedClickable(onClick = { onEdit(meal) }, onLongClick = { menu = true }), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateContentSize()) {
                MealThumb(meal, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        pending -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Palette.accent)
                            Spacer(Modifier.width(6.dp))
                            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                            Text("Analisi in corso…", style = MaterialTheme.typography.bodySmall, color = Palette.accent, modifier = Modifier.alpha(pulse))
                        }
                        failed != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = Palette.over, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(failed, style = MaterialTheme.typography.bodySmall, color = Palette.over, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        else -> {
                            Text("${meal.dateTime.format(timeFormat)} · P ${meal.protein.roundToInt()} · C ${meal.carbs.roundToInt()} · G ${meal.fat.roundToInt()}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MacroSplitBar(meal.protein, meal.carbs, meal.fat, height = 5.dp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (failed != null) {
                    FilledTonalIconButton({ store.retryAnalysis(meal) }) { Icon(Icons.Rounded.Refresh, "Riprova analisi") }
                } else if (!pending) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${meal.kcal}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Modifica") }, { menu = false; onEdit(meal) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
            if (meal.analysis != null) {
                DropdownMenuItem({ Text("Riprova analisi") }, { menu = false; store.retryAnalysis(meal) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) })
            }
            DropdownMenuItem({ Text("Elimina", color = Palette.over) }, { menu = false; store.delete(meal) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.over) })
        }
    }
}

/** Pasto previsto dal piano non ancora inserito: un tocco lo prepara già compilato. */
@Composable
private fun PlanSuggestionCard(planned: PlanMeal, onClick: () -> Unit) {
    val dash = Palette.accent.copy(alpha = 0.55f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(dash, cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Dal piano", style = MaterialTheme.typography.labelMedium, color = Palette.accent, fontWeight = FontWeight.Bold)
            Text(planned.items.joinToString(", ") { it.alternatives.firstOrNull() ?: it.name }, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        if (planned.kcal > 0) {
            Text("${planned.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Palette.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "Aggiungi", tint = Palette.accent)
        }
    }
}
