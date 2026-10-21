package com.scrollmate.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmate.app.ScrollMateApplication
import com.scrollmate.app.ui.theme.ScrollMateTheme
import com.scrollmate.app.ui.theme.isAppInDarkTheme
import com.scrollmate.core.model.ThemeMode

class MainActivity : ComponentActivity() {

    private val container get() = (application as ScrollMateApplication).container
    private val mainViewModel: MainViewModel by viewModels { MainViewModel.factory(container) }
    private val focusViewModel: FocusViewModel by viewModels { FocusViewModel.factory(container) }

    /** Tab requested by a notification tap; consumed once by the UI. */
    private val requestedTab = mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = tabFrom(intent)
        setContent {
            val state by mainViewModel.state.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(state.prefs?.themeMode ?: ThemeMode.SYSTEM)
            // Status/navigation bar icons follow the in-app theme, not only the system setting.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ScrollMateTheme(darkTheme = dark) {
                ScrollMateRoot(
                    mainViewModel = mainViewModel,
                    focusViewModel = focusViewModel,
                    requestedTab = requestedTab.value,
                    onRequestedTabConsumed = { requestedTab.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab.value = tabFrom(intent)
    }

    private fun tabFrom(intent: Intent?): Tab? =
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }

    companion object {
        const val EXTRA_TAB = "open_tab"
    }
}
