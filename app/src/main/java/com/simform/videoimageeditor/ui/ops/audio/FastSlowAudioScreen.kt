package com.simform.videoimageeditor.ui.ops.audio

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Fast/slow-audio screen: one immutable state + one sealed event (MVI).
 *
 * Preserved from the old screen: Switch ON = fast (atempo 2.0), OFF = slow
 * (atempo 0.5). The old layout set no default checked state, so the switch
 * starts OFF (slow).
 */
data class FastSlowAudioState(
    val inputPath: String? = null,
    val isFast: Boolean = false,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface FastSlowAudioEvent {
    data class AudioPicked(val paths: List<String>) : FastSlowAudioEvent
    data class FastChanged(val isFast: Boolean) : FastSlowAudioEvent
    data class Convert(val outputPath: String) : FastSlowAudioEvent
    data object Cancel : FastSlowAudioEvent
    data class LogReceived(val text: String) : FastSlowAudioEvent
    data object Succeeded : FastSlowAudioEvent
    data object Failed : FastSlowAudioEvent
}

class FastSlowAudioViewModel :
    OperationViewModel<FastSlowAudioState, FastSlowAudioEvent>(FastSlowAudioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: FastSlowAudioEvent) {
        when (event) {
            is FastSlowAudioEvent.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is FastSlowAudioEvent.FastChanged -> setState { copy(isFast = event.isFast) }
            is FastSlowAudioEvent.Convert -> {
                val input = state.value.inputPath
                if (input == null) {
                    setState { copy(error = "Please select Input audio") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val atempo = if (state.value.isFast) 2.0 else 0.5
                val query = queries.audioMotion(input, event.outputPath, atempo)
                executeQuery(
                    query,
                    onLog = { onEvent(FastSlowAudioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(FastSlowAudioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(FastSlowAudioEvent.Failed) }
                )
            }
            FastSlowAudioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is FastSlowAudioEvent.LogReceived -> setState { copy(log = event.text) }
            FastSlowAudioEvent.Succeeded -> setState { copy(isRunning = false) }
            FastSlowAudioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun FastSlowAudioScreen(
    onBack: () -> Unit,
    viewModel: FastSlowAudioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(FastSlowAudioEvent.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.fast_slow_audio_convert), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.select_audio_file)
        ) { picker.pickAudio() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.if_you_want_to_fast_audio_then_please_on_this_switch_otherwise_audio_converted_in_to_slow_audio),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.isFast,
                onCheckedChange = { viewModel.onEvent(FastSlowAudioEvent.FastChanged(it)) }
            )
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.fast_slow_audio_convert),
            onRun = {
                viewModel.onEvent(
                    FastSlowAudioEvent.Convert(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(FastSlowAudioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
