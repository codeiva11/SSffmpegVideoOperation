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
import com.simform.videoimageeditor.ui.service.EncodeService
import com.simform.videoimageeditor.ui.storage.OUTPUT_FOLDER_NAME
import com.simform.videoimageeditor.ui.storage.ensureOutputFolder
import com.simform.videoimageeditor.ui.storage.hasStorageAccess
import com.simform.videoimageeditor.ui.theme.VideoOperationsTheme
import com.simform.videoimageeditor.ui.updater.UpdateInfo
import com.simform.videoimageeditor.ui.updater.verifiedCachedApk
import java.io.File
import com.simform.videoimageeditor.ui.updater.checkForUpdate
import com.simform.videoimageeditor.ui.updater.downloadAndVerify
import com.simform.videoimageeditor.ui.updater.installApk

/**
 * Single-activity host for the Compose UI. Every screen is a destination in
 * the navigation graph; each screen owns one immutable state + one sealed
 * event type (MVI, per the migration guide).
 *
 * Edited outputs are saved in the public "$OUTPUT_FOLDER_NAME" folder at the
 * root of phone storage, so first launch asks for all-files access
 * (Android 11+) or the legacy write permission (older Android).
 *
 * On launch the in-app updater checks the latest GitHub release and offers
 * "Install Update" when a newer build exists — and it re-checks every time
 * the app returns to the foreground, so a build published while the app
 * was already open is found too (the launch-only check never saw it).
 */
class MainActivity : ComponentActivity() {
    /**
     * Increments on every onResume (including the first one, right after
     * onCreate). UpdateDialog observes it and re-checks for updates when
     * the app returns to the foreground — event-driven, no polling timer.
     */
    private var resumeCount by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets the operation ViewModels start the background-encode
        // foreground service without holding an Activity reference.
        EncodeService.init(applicationContext)
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

                    UpdateDialog(resumeSignal = resumeCount)

                    val navController = rememberNavController()
                    AppNavHost(navController)
                }
            }
        }
    }
}

/** In-app updater: the new version downloads silently first, and the
 * popup appears only once the APK is downloaded and checksum-verified —
 * so tapping Install Update installs at once, with no waiting. A failed
 * download stays silent (no popup at all). A cached verified APK is
 * reused, so tapping Later does not re-download on the next launch.
 *
 * The check runs on launch AND every time the app returns to the
 * foreground (resumeSignal changes): the launch-only check never saw a
 * build published while the app was already open, so the popup never
 * came — the same flaw StreamCloud fixed by re-checking on resume.
 * Guards: a check already in flight is not started twice; a popup
 * already showing is not re-triggered; a release dismissed in this
 * session (Later / dismiss / Install) does not pop up again until a
 * NEWER release appears. Silent-download-first, SHA-256 fail-closed
 * verification, the "Install Update" dialog and cached-APK reuse are
 * all unchanged. */
@Composable
private fun UpdateDialog(resumeSignal: Int) {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var readyApk by remember { mutableStateOf<File?>(null) }
    // Tag dismissed in this session — a newer tag still shows.
    var dismissedTag by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    suspend fun runCheck() {
        if (checking) return
        // update is only set once the APK is downloaded + verified, so
        // non-null here means the popup is already showing.
        if (update != null) return
        checking = true
        try {
            val info = checkForUpdate() ?: return
            if (info.tag == dismissedTag) return
            val apk = verifiedCachedApk(context, info)
                ?: downloadAndVerify(context, info) {}
            if (apk != null) {
                update = info
                readyApk = apk
            }
            // apk == null (download/verify failed): stay silent; the
            // next resume (or relaunch) retries, as before.
        } finally {
            checking = false
        }
    }

    LaunchedEffect(Unit) { runCheck() }
    LaunchedEffect(resumeSignal) { runCheck() }

    val info = update ?: return
    val apk = readyApk ?: return
    AlertDialog(
        onDismissRequest = {
            dismissedTag = info.tag
            update = null
            readyApk = null
        },
        title = { Text("Install Update") },
        text = {
            Text(
                "A new version ${info.tag} is available. It has already " +
                    "been downloaded — tap Install Update to install it now."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                dismissedTag = info.tag
                update = null
                readyApk = null
                installApk(context, apk)
            }) { Text("Install Update") }
        },
        dismissButton = {
            TextButton(onClick = {
                dismissedTag = info.tag
                update = null
                readyApk = null
            }) { Text("Later") }
        }
    )
}
