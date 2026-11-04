package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.NumberField
import com.simform.videoimageeditor.ui.common.videoDimensions
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import com.simform.videooperations.Paths

/**
 * MVI port of CombineImageAndVideoActivity: join images and videos into one
 * video. The old screen took exactly one image + one video and scaled to the
 * video's own dimensions; this version builds a list (image and video entries
 * in pick order) and scales to the first video's dimensions (640x480 fallback
 * when the list has no video yet).
 */
data class CombineImageAndVideoState(
    val items: List<Paths> = emptyList(),
    val seconds: String = "",
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CombineImageAndVideoEvent {
    data class ImagePicked(val paths: List<String>) : CombineImageAndVideoEvent
    data class VideoPicked(val paths: List<String>) : CombineImageAndVideoEvent
    data class SecondsChanged(val value: String) : CombineImageAndVideoEvent
    data class Combine(val outputPath: String) : CombineImageAndVideoEvent
    data object Cancel : CombineImageAndVideoEvent
    data class LogReceived(val text: String) : CombineImageAndVideoEvent
    data object Succeeded : CombineImageAndVideoEvent
    data object Failed : CombineImageAndVideoEvent
}

class CombineImageAndVideoViewModel :
    OperationViewModel<CombineImageAndVideoState, CombineImageAndVideoEvent>(
        CombineImageAndVideoState()
    ) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CombineImageAndVideoEvent) {
        when (event) {
            is CombineImageAndVideoEvent.ImagePicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Image not selected") }
                    return
                }
                val item = Paths().apply {
                    filePath = path
                    isImageFile = true
                }
                setState { copy(items = items + item, error = null) }
            }
            is CombineImageAndVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                val item = Paths().apply {
                    filePath = path
                    isImageFile = false
                }
                val (w, h) = videoDimensions(path) ?: (null to null)
                setState {
                    copy(
                        items = items + item,
                        width = w ?: width,
                        height = h ?: height,
                        error = null
                    )
                }
            }
            is CombineImageAndVideoEvent.SecondsChanged -> setState { copy(seconds = event.value) }
            is CombineImageAndVideoEvent.Combine -> {
                if (state.value.items.none { it.isImageFile }) {
                    setState { copy(error = "Please select Input image") }
                    return
                }
                if (state.value.items.none { !it.isImageFile }) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                val seconds = state.value.seconds.toIntOrNull()
                if (seconds == null || seconds == 0) {
                    setState { copy(error = "Please enter second") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.combineImagesAndVideos(
                    ArrayList(state.value.items),
                    state.value.width ?: 640,
                    state.value.height ?: 480,
                    state.value.seconds,
                    event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(CombineImageAndVideoEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CombineImageAndVideoEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CombineImageAndVideoEvent.Failed) }
                )
            }
            CombineImageAndVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CombineImageAndVideoEvent.LogReceived -> setState { copy(log = event.text) }
            CombineImageAndVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            CombineImageAndVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CombineImageAndVideoScreen(
    onBack: () -> Unit,
    viewModel: CombineImageAndVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberFilePicker { paths ->
        viewModel.onEvent(CombineImageAndVideoEvent.ImagePicked(paths))
    }
    val videoPicker = rememberFilePicker { paths ->
        viewModel.onEvent(CombineImageAndVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.merge_image_and_video), onBack = onBack) {
        OutlinedButton(
            onClick = { imagePicker.pickImage() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add image") }
        OutlinedButton(
            onClick = { videoPicker.pickVideo() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add video") }
        if (state.items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.items.forEach { item ->
                    Text(
                        (if (item.isImageFile) "Image: " else "Video: ") +
                            item.filePath.substringAfterLast("/")
                    )
                }
            }
        }
        NumberField(
            label = stringResource(R.string.enter_image_display_time_in_second),
            value = state.seconds,
            onValueChange = { viewModel.onEvent(CombineImageAndVideoEvent.SecondsChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.items.isNotEmpty(),
            runLabel = stringResource(R.string.combine),
            onRun = {
                viewModel.onEvent(
                    CombineImageAndVideoEvent.Combine(
                        Common.getFilePath(context, Common.VIDEO)
                    )
                )
            },
            onCancel = { viewModel.onEvent(CombineImageAndVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
