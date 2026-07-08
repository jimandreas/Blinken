package com.jimandreas.blinken

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.jimandreas.blinken.settings.AllowlistRepository
import com.jimandreas.blinken.settings.ui.SettingsScreen
import com.jimandreas.blinken.ui.theme.BlinkenTheme

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
