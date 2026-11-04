package com.simform.videoimageeditor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.simform.videoimageeditor.ui.navigation.AppNavHost
import com.simform.videoimageeditor.ui.storage.OUTPUT_FOLDER_NAME
import com.simform.videoimageeditor.ui.storage.ensureOutputFolder
import com.simform.videoimageeditor.ui.storage.hasStorageAccess
import com.simform.videoimageeditor.ui.theme.VideoOperationsTheme

/**
 * Single-activity host for the Compose UI. Every screen is a destination in
 * the navigation graph; each screen owns one immutable state + one sealed
 * event type (MVI, per the migration guide).
 *
 * Edited outputs are saved in the public "$OUTPUT_FOLDER_NAME" folder at the
 * root of phone storage, so first launch asks for all-files access
 * (Android 11+) or the legacy write permission (older Android).
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
                    val context = LocalContext.current
                    var showStorageDialog by remember {
                        mutableStateOf(!hasStorageAccess(context))
                    }
                    val settingsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { showStorageDialog = !hasStorageAccess(context) }
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { showStorageDialog = !hasStorageAccess(context) }

                    LaunchedEffect(showStorageDialog) {
                        if (!showStorageDialog) {
                            ensureOutputFolder()
                        }
                    }

                    if (showStorageDialog) {
                        AlertDialog(
                            onDismissRequest = { showStorageDialog = false },
                            title = { Text("Save outputs to '$OUTPUT_FOLDER_NAME'") },
                            text = {
                                Text(
                                    "Edited videos and audio are saved in the " +
                                        "'$OUTPUT_FOLDER_NAME' folder on your phone storage, " +
                                        "so you can find them in Files. Please allow " +
                                        "all-files access."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                        ).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        settingsLauncher.launch(intent)
                                    } else {
                                        permissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                }) { Text("Allow") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showStorageDialog = false }) {
                                    Text("Later")
                                }
                            }
                        )
                    }

                    val navController = rememberNavController()
                    AppNavHost(navController)
                }
            }
        }
    }
}
