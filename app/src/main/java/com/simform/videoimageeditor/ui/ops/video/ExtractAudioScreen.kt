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

/** MVI port of the old ExtractAudioActivity: video pick -> extractAudio -> .mp3 out. */
data class ExtractAudioState(
    val inputPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface ExtractAudioEvent {
    data class VideoPicked(val paths: List<String>) : ExtractAudioEvent
    data class Extract(val outputPath: String) : ExtractAudioEvent
    data object Cancel : ExtractAudioEvent
    data class LogReceived(val text: String) : ExtractAudioEvent
    data object Succeeded : ExtractAudioEvent
    data object Failed : ExtractAudioEvent
}

class ExtractAudioViewModel :
    OperationViewModel<ExtractAudioState, ExtractAudioEvent>(ExtractAudioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: ExtractAudioEvent) {
        when (event) {
            is ExtractAudioEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is ExtractAudioEvent.Extract -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.extractAudio(input, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(ExtractAudioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(ExtractAudioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(ExtractAudioEvent.Failed) }
                )
            }
            ExtractAudioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is ExtractAudioEvent.LogReceived -> setState { copy(log = event.text) }
            ExtractAudioEvent.Succeeded -> setState { copy(isRunning = false) }
            ExtractAudioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun ExtractAudioScreen(
    onBack: () -> Unit,
    viewModel: ExtractAudioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(ExtractAudioEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.extract_audio_from_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.extract_audio),
            onRun = {
                viewModel.onEvent(
                    ExtractAudioEvent.Extract(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(ExtractAudioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
