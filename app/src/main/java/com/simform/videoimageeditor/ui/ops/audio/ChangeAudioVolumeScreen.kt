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

/**
 * Change-audio-volume screen: one immutable state + one sealed event (MVI).
 *
 * The old screen had NO volume input: the volume was hardcoded to 0.1f.
 * That exact behaviour is preserved — there is no volume field here either.
 */
data class ChangeAudioVolumeState(
    val inputPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface ChangeAudioVolumeEvent {
    data class AudioPicked(val paths: List<String>) : ChangeAudioVolumeEvent
    data class Change(val outputPath: String) : ChangeAudioVolumeEvent
    data object Cancel : ChangeAudioVolumeEvent
    data class LogReceived(val text: String) : ChangeAudioVolumeEvent
    data object Succeeded : ChangeAudioVolumeEvent
    data object Failed : ChangeAudioVolumeEvent
}

class ChangeAudioVolumeViewModel :
    OperationViewModel<ChangeAudioVolumeState, ChangeAudioVolumeEvent>(ChangeAudioVolumeState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: ChangeAudioVolumeEvent) {
        when (event) {
            is ChangeAudioVolumeEvent.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is ChangeAudioVolumeEvent.Change -> {
                val input = state.value.inputPath
                if (input == null) {
                    setState { copy(error = "Please select audio") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.audioVolumeUpdate(input, 0.1f, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(ChangeAudioVolumeEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(ChangeAudioVolumeEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(ChangeAudioVolumeEvent.Failed) }
                )
            }
            ChangeAudioVolumeEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is ChangeAudioVolumeEvent.LogReceived -> setState { copy(log = event.text) }
            ChangeAudioVolumeEvent.Succeeded -> setState { copy(isRunning = false) }
            ChangeAudioVolumeEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun ChangeAudioVolumeScreen(
    onBack: () -> Unit,
    viewModel: ChangeAudioVolumeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(ChangeAudioVolumeEvent.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.change_audio_volume), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.select_audio_file)
        ) { picker.pickAudio() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.change_audio_volume),
            onRun = {
                viewModel.onEvent(
                    ChangeAudioVolumeEvent.Change(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(ChangeAudioVolumeEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
