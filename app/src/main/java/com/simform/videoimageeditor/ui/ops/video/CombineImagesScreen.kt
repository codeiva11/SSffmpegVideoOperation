package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.NumberField
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.PickFileRow
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import com.simform.videooperations.Paths

/** MVI port of CombineImagesActivity: join several images into one video. */
data class CombineImagesState(
    val imagePaths: List<String> = emptyList(),
    val seconds: String = "",
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CombineImagesEvent {
    data class ImagesPicked(val paths: List<String>) : CombineImagesEvent
    data class SecondsChanged(val value: String) : CombineImagesEvent
    data class Combine(val outputPath: String) : CombineImagesEvent
    data object Cancel : CombineImagesEvent
    data class LogReceived(val text: String) : CombineImagesEvent
    data object Succeeded : CombineImagesEvent
    data object Failed : CombineImagesEvent
}

class CombineImagesViewModel :
    OperationViewModel<CombineImagesState, CombineImagesEvent>(CombineImagesState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CombineImagesEvent) {
        when (event) {
            is CombineImagesEvent.ImagesPicked -> {
                if (event.paths.isEmpty()) {
                    setState { copy(error = "Image not selected") }
                    return
                }
                setState { copy(imagePaths = event.paths, error = null) }
            }
            is CombineImagesEvent.SecondsChanged -> setState { copy(seconds = event.value) }
            is CombineImagesEvent.Combine -> {
                if (state.value.imagePaths.isEmpty()) {
                    setState { copy(error = "Please select Input image") }
                    return
                }
                val seconds = state.value.seconds.toIntOrNull()
                if (seconds == null || seconds == 0) {
                    setState { copy(error = "Please enter second") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val pathsList = ArrayList<Paths>().apply {
                    state.value.imagePaths.forEach { path ->
                        add(Paths().apply {
                            filePath = path
                            isImageFile = true
                        })
                    }
                }
                // Old literal preserved: 640x480.
                val query = queries.combineImagesAndVideos(
                    pathsList,
                    640,
                    480,
                    state.value.seconds,
                    event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(CombineImagesEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CombineImagesEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CombineImagesEvent.Failed) }
                )
            }
            CombineImagesEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CombineImagesEvent.LogReceived -> setState { copy(log = event.text) }
            CombineImagesEvent.Succeeded -> setState { copy(isRunning = false) }
            CombineImagesEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CombineImagesScreen(
    onBack: () -> Unit,
    viewModel: CombineImagesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CombineImagesEvent.ImagesPicked(paths))
    }
    val count = state.imagePaths.size
    OperationScaffold(title = stringResource(R.string.merge_images), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = if (count == 0) null
                else "$count" + (if (count == 1) " Image " else " Images ") + "selected",
            buttonText = stringResource(R.string.select_images)
        ) { picker.pickImages() }
        NumberField(
            label = stringResource(R.string.enter_image_display_time_in_second),
            value = state.seconds,
            onValueChange = { viewModel.onEvent(CombineImagesEvent.SecondsChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.imagePaths.isNotEmpty(),
            runLabel = stringResource(R.string.combine),
            onRun = {
                viewModel.onEvent(
                    CombineImagesEvent.Combine(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(CombineImagesEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
