package com.dlab.morsio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlab.morsio.PlanMeal
import com.dlab.morsio.Slot
import com.dlab.morsio.store
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun PlanScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val store = context.store
    val plan by store.plan.collectAsState()
    val meals by store.meals.collectAsState()
    val today = Slot.weekdayCode(LocalDate.now())
    var weekday by rememberSaveable { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        error = runCatching {
            store.importPlan(context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        }.exceptionOrNull()?.let { "Import non riuscito: ${it.message}" }
    }
    val import = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(plan?.name ?: "Piano", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (plan != null) Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Opzioni") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Importa un altro piano") }, { menu = false; import() }, leadingIcon = { Icon(Icons.Rounded.Download, null) })
                        DropdownMenuItem({ Text("Rimuovi piano", color = Palette.over) }, { menu = false; store.removePlan() },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Palette.over) })
                    }
                }
            }
        }
        val p = plan
        if (p == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Assignment, null, tint = Palette.accent, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nessun piano", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Importa il JSON della tua dieta (lo stesso di Diet1): vedrai cosa prevede ogni pasto direttamente nel diario.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(import, colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White)) { Text("Importa piano") }
                }
            }
        } else {
            item { AdherenceCard(remember(meals, p) { store.weeklyAdherence() }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Slot.weekdays.forEach { d ->
                        val selected = d == weekday
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Palette.accent else MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { weekday = d }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = when { selected -> Color.White; d == today -> Palette.accent; else -> MaterialTheme.colorScheme.onSurface })
                        }
                    }
                }
            }
            items(p.day(weekday)?.meals.orEmpty(), key = { it.id }) { PlanMealCard(it) }
        }
        error?.let { item { Text(it, color = Palette.over, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun AdherenceCard(week: Pair<Int, Int>) {
    val progress = if (week.second > 0) week.first.toFloat() / week.second else 0f
    MorsioCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                CalorieRing(progress, Modifier.size(84.dp), lineWidth = 10.dp)
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Aderenza al piano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${week.first} pasti su ${week.second} negli ultimi 7 giorni", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanMealCard(meal: PlanMeal) {
    MorsioCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(slotIcon(meal.name), null, tint = Palette.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (meal.kcal > 0) Text("${meal.kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.accent)
            }
            if (meal.kcal > 0) MacroSplitBar(meal.protein, meal.carbs, meal.fat, height = 6.dp)
            meal.items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val alts = item.alternatives
                    Text(alts.firstOrNull() ?: item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    alts.drop(1).forEach {
                        Text("oppure $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
