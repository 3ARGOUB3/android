package com.masroufi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.masroufi.ui.MasroufiApp
import com.masroufi.ui.MasroufiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MasroufiTheme {
                MasroufiApp()
            }
        }
    }
}
