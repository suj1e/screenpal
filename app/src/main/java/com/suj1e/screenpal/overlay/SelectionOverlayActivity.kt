package com.suj1e.screenpal.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.suj1e.screenpal.ui.theme.ScreenPalTheme

class SelectionOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenPalTheme {
                // Selection screen placeholder
            }
        }
    }
}
