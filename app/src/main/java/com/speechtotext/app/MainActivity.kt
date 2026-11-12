package com.speechtotext.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.speechtotext.app.navigation.AppNavigation
import com.speechtotext.app.ui.theme.SpeechToTextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeechToTextTheme {
                AppNavigation()
            }
        }
    }
}
