package com.simform.videoimageeditor.ui.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/** Public output folder at the root of phone storage: /storage/emulated/0/Ffmpeg Output/ */
const val OUTPUT_FOLDER_NAME = "Ffmpeg Output"

fun outputFolder(): File = File(Environment.getExternalStorageDirectory(), OUTPUT_FOLDER_NAME)

fun ensureOutputFolder(): File = outputFolder().apply { if (!exists()) mkdirs() }

/** True when the app may write the public output folder. */
fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
