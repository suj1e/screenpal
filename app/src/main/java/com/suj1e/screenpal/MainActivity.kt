package com.suj1e.screenpal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.suj1e.screenpal.ui.theme.ScreenPalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenPalTheme {
                // Main screen placeholder
            }
        }
    }
}
