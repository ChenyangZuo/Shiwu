package com.zzzcy.shiwu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zzzcy.shiwu.ui.ShiwuApp
import com.zzzcy.shiwu.ui.theme.ShiwuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiwuTheme {
                ShiwuApp()
            }
        }
    }
}
