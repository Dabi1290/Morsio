package com.dlab.morsio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dlab.morsio.ui.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class MorsioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storage = Storage(context)
        val today = storage.loadMeals().on(LocalDate.now())
        val goal = storage.goal
        provideContent { Content(today, goal) }
    }

    @Composable
    private fun Content(meals: List<Meal>, goal: Int) {
        val eaten = meals.kcal
        val left = goal - eaten
        val progress = if (goal > 0) eaten.toFloat() / goal else 0f
        val primary = ColorProvider(day = Color(0xFF14201B), night = Color(0xFFF1F5F3))
        val secondary = ColorProvider(day = Color(0xFF4A5A53), night = Color(0xFFA9B8B1))
        val wide = LocalSize.current.width >= 250.dp
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(ColorProvider(day = Color(0xFFF3F8F5), night = Color(0xFF111A16)))
                .padding(14.dp)
                .clickable(actionStartActivity(Intent(Reminders.ACTION_ADD).setPackage("com.dlab.morsio"))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Box(GlanceModifier.size(if (wide) 96.dp else 92.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring(progress)), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${kotlin.math.abs(left)}", style = TextStyle(color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Text(if (left >= 0) "rimaste" else "in più", style = TextStyle(color = secondary, fontSize = 11.sp))
                }
            }
            if (wide) {
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.fillMaxWidth()) {
                    Text("Oggi · $eaten / $goal kcal", style = TextStyle(color = primary, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(6.dp))
                    macro("Proteine", meals.sumOf { it.protein }, Palette.protein, primary)
                    macro("Carboidrati", meals.sumOf { it.carbs }, Palette.carbs, primary)
                    macro("Grassi", meals.sumOf { it.fat }, Palette.fat, primary)
                }
            }
        }
    }

    @Composable
    private fun macro(label: String, grams: Double, color: Color, text: GlanceColor) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(vertical = 1.dp)) {
            Text("●", style = TextStyle(color = GlanceColor(color), fontSize = 11.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text("$label ${grams.roundToInt()} g", style = TextStyle(color = text, fontSize = 13.sp))
        }
    }

    /** L'anello delle calorie come immagine (Glance non disegna archi): verde, arancio sopra il 100%, rosso sopra il 115%. */
    private fun ring(progress: Float): Bitmap {
        val size = 192
        val stroke = 18f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        paint.color = Palette.accent.copy(alpha = 0.18f).toArgb()
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = (if (progress > 1.15f) Palette.over else if (progress > 1f) Palette.carbs else Palette.accent).toArgb()
        if (progress > 0) canvas.drawArc(rect, -90f, 360f * progress.coerceAtMost(1f), false, paint)
        return bmp
    }

    companion object {
        fun refresh(context: Context) {
            CoroutineScope(Dispatchers.Default).launch { runCatching { MorsioWidget().updateAll(context) } }
        }
    }
}

class MorsioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MorsioWidget()
}
