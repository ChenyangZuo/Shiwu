package com.zzzcy.shiwu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.ShiwuApp
import com.zzzcy.shiwu.ui.theme.ShiwuTheme
import com.zzzcy.shiwu.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by ShiwuRepository.themeMode.collectAsState()
            ShiwuTheme(mode = themeMode.toThemeMode()) {
                ShiwuApp()
            }
        }
    }
}

private fun String.toThemeMode(): ThemeMode = when (this) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}
