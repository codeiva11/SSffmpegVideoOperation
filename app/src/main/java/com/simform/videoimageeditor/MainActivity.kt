package com.simform.videoimageeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.simform.videoimageeditor.ui.navigation.AppNavHost
import com.simform.videoimageeditor.ui.theme.VideoOperationsTheme

/**
 * Single-activity host for the Compose UI. Every screen is a destination in
 * the navigation graph; each screen owns one immutable state + one sealed
 * event type (MVI, per the migration guide).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoOperationsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController)
                }
            }
        }
    }
}
