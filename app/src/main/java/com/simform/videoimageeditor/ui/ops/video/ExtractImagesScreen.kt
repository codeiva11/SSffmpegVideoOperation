package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.PickFileRow
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/**
 * MVI port of ExtractImagesActivity: extract frames from a video.
 * Old literal preserved: 4 frames per second.
 */
data class ExtractImagesState(
    val inputPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface ExtractImagesEvent {
    data class VideoPicked(val paths: List<String>) : ExtractImagesEvent
    data class Extract(val outputPath: String) : ExtractImagesEvent
    data object Cancel : ExtractImagesEvent
    data class LogReceived(val text: String) : ExtractImagesEvent
    data object Succeeded : ExtractImagesEvent
    data object Failed : ExtractImagesEvent
}

class ExtractImagesViewModel :
    OperationViewModel<ExtractImagesState, ExtractImagesEvent>(ExtractImagesState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: ExtractImagesEvent) {
        when (event) {
            is ExtractImagesEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is ExtractImagesEvent.Extract -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.extractImages(input, event.outputPath, 4f)
                executeQuery(
                    query,
                    onLog = { onEvent(ExtractImagesEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(ExtractImagesEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(ExtractImagesEvent.Failed) }
                )
            }
            ExtractImagesEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is ExtractImagesEvent.LogReceived -> setState { copy(log = event.text) }
            ExtractImagesEvent.Succeeded -> setState { copy(isRunning = false) }
            ExtractImagesEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun ExtractImagesScreen(
    onBack: () -> Unit,
    viewModel: ExtractImagesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(ExtractImagesEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.extract_frame_from_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.extract_frames),
            onRun = {
                viewModel.onEvent(
                    ExtractImagesEvent.Extract(Common.getFilePath(context, Common.IMAGE))
                )
            },
            onCancel = { viewModel.onEvent(ExtractImagesEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
