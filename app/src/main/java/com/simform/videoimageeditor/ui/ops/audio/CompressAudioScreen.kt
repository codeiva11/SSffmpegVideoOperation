package com.simform.videoimageeditor.ui.ops.audio

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
import java.io.File

/** Reference audio screen: one immutable state + one sealed event (MVI). */
data class CompressAudioState(
    val inputPath: String? = null,
    val inputSize: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CompressAudioEvent {
    data class AudioPicked(val paths: List<String>) : CompressAudioEvent
    data class Compress(val outputPath: String) : CompressAudioEvent
    data object Cancel : CompressAudioEvent
    data class LogReceived(val text: String) : CompressAudioEvent
    data object Succeeded : CompressAudioEvent
    data object Failed : CompressAudioEvent
}

class CompressAudioViewModel :
    OperationViewModel<CompressAudioState, CompressAudioEvent>(CompressAudioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CompressAudioEvent) {
        when (event) {
            is CompressAudioEvent.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                setState {
                    copy(
                        inputPath = path,
                        inputSize = Common.getFileSize(File(path)),
                        error = null
                    )
                }
            }
            is CompressAudioEvent.Compress -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.compressAudio(input, Common.BITRATE_128, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(CompressAudioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CompressAudioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CompressAudioEvent.Failed) }
                )
            }
            CompressAudioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CompressAudioEvent.LogReceived -> setState { copy(log = event.text) }
            CompressAudioEvent.Succeeded -> setState { copy(isRunning = false) }
            CompressAudioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CompressAudioScreen(
    onBack: () -> Unit,
    viewModel: CompressAudioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CompressAudioEvent.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.compress_audio), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.select_audio_file)
        ) { picker.pickAudio() }
        state.inputSize?.let {
            androidx.compose.material3.Text("Input file Size : $it")
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.compress),
            onRun = {
                viewModel.onEvent(
                    CompressAudioEvent.Compress(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(CompressAudioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
