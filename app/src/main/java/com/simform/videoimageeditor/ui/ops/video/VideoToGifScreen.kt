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

/** MVI port of the old VideoToGifActivity: video pick -> convertVideoToGIF -> .gif out. */
data class VideoToGifState(
    val inputPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface VideoToGifEvent {
    data class VideoPicked(val paths: List<String>) : VideoToGifEvent
    data class Convert(val outputPath: String) : VideoToGifEvent
    data object Cancel : VideoToGifEvent
    data class LogReceived(val text: String) : VideoToGifEvent
    data object Succeeded : VideoToGifEvent
    data object Failed : VideoToGifEvent
}

class VideoToGifViewModel :
    OperationViewModel<VideoToGifState, VideoToGifEvent>(VideoToGifState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: VideoToGifEvent) {
        when (event) {
            is VideoToGifEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is VideoToGifEvent.Convert -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.convertVideoToGIF(input, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(VideoToGifEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(VideoToGifEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(VideoToGifEvent.Failed) }
                )
            }
            VideoToGifEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is VideoToGifEvent.LogReceived -> setState { copy(log = event.text) }
            VideoToGifEvent.Succeeded -> setState { copy(isRunning = false) }
            VideoToGifEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun VideoToGifScreen(
    onBack: () -> Unit,
    viewModel: VideoToGifViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(VideoToGifEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.video_to_gif), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.convert_into_gif),
            onRun = {
                viewModel.onEvent(
                    VideoToGifEvent.Convert(Common.getFilePath(context, Common.GIF))
                )
            },
            onCancel = { viewModel.onEvent(VideoToGifEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
