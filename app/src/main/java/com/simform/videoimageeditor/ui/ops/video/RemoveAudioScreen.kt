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

/** MVI port of the old RemoveAudioFromVideoActivity: video pick -> removeAudioFromVideo. */
data class RemoveAudioState(
    val inputPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface RemoveAudioEvent {
    data class VideoPicked(val paths: List<String>) : RemoveAudioEvent
    data class Remove(val outputPath: String) : RemoveAudioEvent
    data object Cancel : RemoveAudioEvent
    data class LogReceived(val text: String) : RemoveAudioEvent
    data object Succeeded : RemoveAudioEvent
    data object Failed : RemoveAudioEvent
}

class RemoveAudioViewModel :
    OperationViewModel<RemoveAudioState, RemoveAudioEvent>(RemoveAudioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: RemoveAudioEvent) {
        when (event) {
            is RemoveAudioEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is RemoveAudioEvent.Remove -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.removeAudioFromVideo(input, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(RemoveAudioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(RemoveAudioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(RemoveAudioEvent.Failed) }
                )
            }
            RemoveAudioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is RemoveAudioEvent.LogReceived -> setState { copy(log = event.text) }
            RemoveAudioEvent.Succeeded -> setState { copy(isRunning = false) }
            RemoveAudioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun RemoveAudioScreen(
    onBack: () -> Unit,
    viewModel: RemoveAudioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(RemoveAudioEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.audio_remove_from_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.remove_audio),
            onRun = {
                viewModel.onEvent(
                    RemoveAudioEvent.Remove(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(RemoveAudioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
