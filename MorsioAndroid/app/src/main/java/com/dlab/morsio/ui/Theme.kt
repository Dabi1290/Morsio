package com.dlab.morsio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dlab.morsio.Meal
import com.dlab.morsio.store
import kotlin.math.roundToInt

object Palette {
    val accent = Color(0xFF21BD8F)
    val lime = Color(0xFF8CD94D)
    val protein = Color(0xFFFA6180)
    val carbs = Color(0xFFFFAD33)
    val fat = Color(0xFF738CFF)
    val over = Color(0xFFFF544D)
}

@Composable
fun MorsioTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Palette.accent, onPrimary = Color(0xFF00261B), secondary = Palette.lime,
        secondaryContainer = Color(0xFF1E3A30), onSecondaryContainer = Color(0xFFBFEBD9),
        background = Color(0xFF0B110E), surface = Color(0xFF0B110E),
        surfaceContainer = Color(0xFF16201C), surfaceContainerHigh = Color(0xFF1C2823), surfaceContainerLow = Color(0xFF121A17),
        onSurfaceVariant = Color(0xFFA9B8B1), surfaceVariant = Color(0xFF26332D), surfaceContainerHighest = Color(0xFF26332D),
        outline = Color(0xFF7D8C85), outlineVariant = Color(0xFF34423C),
    ) else lightColorScheme(
        primary = Color(0xFF14A47A), onPrimary = Color.White, secondary = Color(0xFF5E9E2A),
        secondaryContainer = Color(0xFFDDF3EA), onSecondaryContainer = Color(0xFF0E5E46),
        background = Color(0xFFF3F6F4), surface = Color(0xFFF3F6F4),
        surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerLow = Color(0xFFEAF0ED),
        onSurfaceVariant = Color(0xFF4A5A53), surfaceVariant = Color(0xFFE3EAE6), surfaceContainerHighest = Color(0xFFE3EAE6),
        outline = Color(0xFF7A8A83), outlineVariant = Color(0xFFD3DDD8),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

fun slotIcon(slot: String): ImageVector = when (slot) {
    "Colazione" -> Icons.Rounded.WbTwilight
    "Spuntini" -> Icons.Rounded.Eco
    "Pranzo" -> Icons.Rounded.Restaurant
    else -> Icons.Rounded.DarkMode
}

/** Card arrotondata dello stile dell'app. */
@Composable
fun MorsioCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Box(Modifier.padding(padding)) { content() } }
}

/** Anello di avanzamento: verde, arancio sopra il 100%, rosso sopra il 115%. */
@Composable
fun CalorieRing(progress: Float, modifier: Modifier = Modifier, lineWidth: Dp = 14.dp) {
    Canvas(modifier) {
        val w = lineWidth.toPx()
        val arcSize = Size(size.width - w, size.height - w)
        val topLeft = Offset(w / 2, w / 2)
        drawArc(Palette.accent.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
        val color = if (progress > 1.15f) Palette.over else if (progress > 1f) Palette.carbs else Palette.accent
        val brush = if (progress > 1f) Brush.linearGradient(listOf(color, color)) else Brush.sweepGradient(listOf(Palette.lime, Palette.accent, Palette.lime))
        if (progress > 0) drawArc(brush, -90f, 360f * progress.coerceAtMost(1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Barra divisa per proteine, carboidrati e grassi, in proporzione alle kcal. */
@Composable
fun MacroSplitBar(protein: Double, carbs: Double, fat: Double, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val parts = listOf(protein * 4 to Palette.protein, carbs * 4 to Palette.carbs, fat * 9 to Palette.fat).filter { it.first > 0 }
    Row(modifier.fillMaxWidth().height(height).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (parts.isEmpty()) Box(Modifier.weight(1f).height(height).background(Palette.accent.copy(alpha = 0.16f)))
        parts.forEach { (kcal, color) -> Box(Modifier.weight(kcal.toFloat()).height(height).background(color)) }
    }
}

@Composable
fun MacroRow(label: String, grams: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Foto del pasto, o l'icona della fascia se non c'è. */
@Composable
fun MealThumb(meal: Meal, size: Dp = 56.dp) {
    val context = LocalContext.current
    val bitmap = remember(meal.id, meal.hasPhoto) {
        if (meal.hasPhoto) context.store.storage.photoFile(meal.id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } else null
    }
    val shape = RoundedCornerShape(size * 0.3f)
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(shape).background(Palette.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (meal.analysis != null) Icons.Rounded.AutoAwesome else slotIcon(meal.resolvedSlot), null, tint = Palette.accent)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}
