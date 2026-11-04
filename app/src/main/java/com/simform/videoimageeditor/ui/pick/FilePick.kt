package com.simform.videoimageeditor.ui.pick

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File picking through the system picker (Activity Result API, scoped-storage
 * correct). The old FilePicker returned raw file paths; the system picker
 * returns content:// URIs, so each picked item is copied into the app cache
 * and the FFmpeg command receives that real file path.
 */
class FilePicker internal constructor(
    private val pickSingleVisual: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    private val pickMultipleVisual: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    private val pickSingleAudio: ManagedActivityResultLauncher<String, Uri?>,
    private val pickMultipleAudio: ManagedActivityResultLauncher<String, List<Uri>>,
    private val pickSingleVideoFile: ManagedActivityResultLauncher<String, Uri?>
) {
    fun pickVideo() = pickSingleVisual.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    )

    fun pickVideos() = pickMultipleVisual.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    )

    fun pickImage() = pickSingleVisual.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    fun pickImages() = pickMultipleVisual.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    /**
     * Any video file, any format (mp4/mkv/avi/mov/webm/...): the system file
     * picker with video/*, not the photo picker which only lists
     * media-store videos.
     */
    fun pickAnyVideo() = pickSingleVideoFile.launch("video/*")

    fun pickAudio() = pickSingleAudio.launch("audio/*")

    fun pickAudios() = pickMultipleAudio.launch("audio/*")
}

@Composable
fun rememberFilePicker(
    scope: CoroutineScope = rememberCoroutineScope(),
    onPicked: (List<String>) -> Unit
): FilePicker {
    val context = LocalContext.current
    val deliver: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            val paths = withContext(Dispatchers.IO) {
                uris.mapNotNull { context.copyUriToFile(it) }
            }
            onPicked(paths)
        }
    }
    val pickSingleVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { deliver(listOf(it)) } }
    val pickMultipleVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> deliver(uris) }
    val pickSingleAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { deliver(listOf(it)) } }
    val pickMultipleAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> deliver(uris) }
    val pickSingleVideoFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { deliver(listOf(it)) } }
    return remember(
        pickSingleVisual, pickMultipleVisual, pickSingleAudio, pickMultipleAudio,
        pickSingleVideoFile
    ) {
        FilePicker(
            pickSingleVisual, pickMultipleVisual, pickSingleAudio, pickMultipleAudio,
            pickSingleVideoFile
        )
    }
}

private fun Context.copyUriToFile(uri: Uri): String? {
    return try {
        val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "picked_${System.currentTimeMillis()}"
        val dir = File(cacheDir, "picked").apply { if (!exists()) mkdirs() }
        val out = File(dir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
