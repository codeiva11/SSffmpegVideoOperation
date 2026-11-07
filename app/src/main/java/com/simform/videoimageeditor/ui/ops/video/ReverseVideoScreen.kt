package com.simform.videoimageeditor.ui.ops.video

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
 * MVI port of the old ReverseVideoActivity. The switch passes through to
 * videoReverse(input, isWithAudioReverse, output) exactly as before
 * (old default: switch OFF -> video only).
 */
data class ReverseVideoState(
    val inputPath: String? = null,
    val withAudio: Boolean = false,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface ReverseVideoEvent {
    data class VideoPicked(val paths: List<String>) : ReverseVideoEvent
    data class WithAudioChanged(val withAudio: Boolean) : ReverseVideoEvent
    data class Reverse(val outputPath: String) : ReverseVideoEvent
    data object Cancel : ReverseVideoEvent
    data class LogReceived(val text: String) : ReverseVideoEvent
    data object Succeeded : ReverseVideoEvent
    data object Failed : ReverseVideoEvent
}

class ReverseVideoViewModel :
    OperationViewModel<ReverseVideoState, ReverseVideoEvent>(ReverseVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: ReverseVideoEvent) {
        when (event) {
            is ReverseVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is ReverseVideoEvent.WithAudioChanged -> setState { copy(withAudio = event.withAudio) }
            is ReverseVideoEvent.Reverse -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val withAudio = state.value.withAudio
                // videoReverse probes the input audio bitrate when audio
                // is reversed (FFprobeKit blocks), so the query is built
                // off the main thread — same pattern as the H.265 screen.
                Thread {
                    val query = queries.videoReverse(input, withAudio, event.outputPath)
                    executeQuery(
                        query,
                        onLog = { onEvent(ReverseVideoEvent.LogReceived(it.text)) },
                        onSuccess = { onEvent(ReverseVideoEvent.Succeeded) },
                        onCancel = { setState { copy(isRunning = false) } },
                        onFailed = { onEvent(ReverseVideoEvent.Failed) }
                    )
                }.start()
            }
            ReverseVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is ReverseVideoEvent.LogReceived -> setState { copy(log = event.text) }
            ReverseVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            ReverseVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun ReverseVideoScreen(
    onBack: () -> Unit,
    viewModel: ReverseVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(ReverseVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.reverse_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        Text(stringResource(R.string.if_you_want_to_reverse_video_with_audio_then_please_on_this_switch))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.withAudio,
                onCheckedChange = { viewModel.onEvent(ReverseVideoEvent.WithAudioChanged(it)) }
            )
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.reverse_video),
            onRun = {
                viewModel.onEvent(
                    ReverseVideoEvent.Reverse(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(ReverseVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
