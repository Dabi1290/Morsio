package com.dlab.morsio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dlab.morsio.ui.AddMealScreen
import com.dlab.morsio.ui.DiaryScreen
import com.dlab.morsio.ui.MorsioTheme
import com.dlab.morsio.ui.Palette
import com.dlab.morsio.ui.PlanScreen
import com.dlab.morsio.ui.SettingsScreen

/** Schermata aperta sopra le schede: inserimento/modifica di un pasto o impostazioni. */
sealed interface Overlay {
    data class Edit(val meal: Meal?) : Overlay
    data object Settings : Overlay
}

class MainActivity : ComponentActivity() {
    private val theme = mutableStateOf("system")
    private var overlay by mutableStateOf<Overlay?>(null)
    private var tab by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        theme.value = store.storage.prefs.getString("theme", "system")!!
        Reminders.schedule(this)
        handle(intent)
        setContent {
            val dark = when (theme.value) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            MorsioTheme(dark) { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        store.resumeAnalyses()
        store.sync()
    }

    /** Dal widget o da un promemoria: torna al diario e apre l'inserimento. */
    private fun handle(intent: Intent?) {
        if (intent?.action == Reminders.ACTION_ADD) {
            tab = 0
            overlay = Overlay.Edit(null)
        }
    }

    @Composable
    private fun App() {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        val colors = NavigationBarItemDefaults.colors(indicatorColor = Palette.accent.copy(alpha = 0.18f))
                        NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Rounded.Restaurant, null) }, label = { Text("Diario") }, colors = colors)
                        NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Rounded.Assignment, null) }, label = { Text("Piano") }, colors = colors)
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { overlay = Overlay.Edit(null) },
                        icon = { Icon(Icons.Rounded.CameraAlt, null) },
                        text = { Text("Aggiungi pasto", fontWeight = FontWeight.SemiBold) },
                        containerColor = Palette.accent,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    )
                },
            ) { padding ->
                AnimatedContent(tab, label = "tab") { t ->
                    if (t == 0) DiaryScreen(padding, onEdit = { overlay = Overlay.Edit(it) }, onSettings = { overlay = Overlay.Settings })
                    else PlanScreen(padding)
                }
            }
            // fascia sotto la barra di stato: il contenuto che scorre non finisce sotto ora e icone
            Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
            when (val o = overlay) {
                is Overlay.Edit -> {
                    BackHandler { overlay = null }
                    AddMealScreen(o.meal, onClose = { overlay = null })
                }
                Overlay.Settings -> {
                    BackHandler { overlay = null }
                    SettingsScreen(theme.value, onTheme = { theme.value = it }, onClose = { overlay = null })
                }
                null -> {}
            }
        }
    }
}
