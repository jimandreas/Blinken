package com.bammellab.blinken

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.bammellab.blinken.settings.AllowlistRepository
import com.bammellab.blinken.settings.ui.SettingsScreen
import com.bammellab.blinken.ui.theme.BlinkenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AllowlistRepository(applicationContext)
        setContent {
            BlinkenTheme {
                SettingsScreen(
                    repository = repository,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
