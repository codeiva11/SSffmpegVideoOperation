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
 * MVI port of the old FastAndSlowVideoMotionActivity.
 * Old values preserved exactly: switch ON (fast) -> setpts 0.5 / atempo 2.0,
 * switch OFF (slow) -> setpts 2.0 / atempo 0.5. The old switch defaulted to
 * OFF, so the default here is slow (isFast = false).
 */
data class FastSlowVideoState(
    val inputPath: String? = null,
    val isFast: Boolean = false,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface FastSlowVideoEvent {
    data class VideoPicked(val paths: List<String>) : FastSlowVideoEvent
    data class FastChanged(val isFast: Boolean) : FastSlowVideoEvent
    data class ApplyMotion(val outputPath: String) : FastSlowVideoEvent
    data object Cancel : FastSlowVideoEvent
    data class LogReceived(val text: String) : FastSlowVideoEvent
    data object Succeeded : FastSlowVideoEvent
    data object Failed : FastSlowVideoEvent
}

class FastSlowVideoViewModel :
    OperationViewModel<FastSlowVideoState, FastSlowVideoEvent>(FastSlowVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: FastSlowVideoEvent) {
        when (event) {
            is FastSlowVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is FastSlowVideoEvent.FastChanged -> setState { copy(isFast = event.isFast) }
            is FastSlowVideoEvent.ApplyMotion -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val setpts = if (state.value.isFast) 0.5 else 2.0
                val atempo = if (state.value.isFast) 2.0 else 0.5
                // videoMotion probes the input audio bitrate (FFprobeKit
                // blocks), so the query is built off the main thread —
                // same pattern as the H.265 screen.
                Thread {
                    val query = queries.videoMotion(input, event.outputPath, setpts, atempo)
                    executeQuery(
                        query,
                        onLog = { onEvent(FastSlowVideoEvent.LogReceived(it.text)) },
                        onSuccess = { onEvent(FastSlowVideoEvent.Succeeded) },
                        onCancel = { setState { copy(isRunning = false) } },
                        onFailed = { onEvent(FastSlowVideoEvent.Failed) }
                    )
                }.start()
            }
            FastSlowVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is FastSlowVideoEvent.LogReceived -> setState { copy(log = event.text) }
            FastSlowVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            FastSlowVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun FastSlowVideoScreen(
    onBack: () -> Unit,
    viewModel: FastSlowVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(FastSlowVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.fast_slow_motion_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        Text(stringResource(R.string.if_you_want_to_fast_motion_video_then_please_on_this_switch_otherwise_video_converted_in_to_slow_motion))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (state.isFast) stringResource(R.string.fast) else stringResource(R.string.slow))
            Switch(
                checked = state.isFast,
                onCheckedChange = { viewModel.onEvent(FastSlowVideoEvent.FastChanged(it)) }
            )
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            // Old layout hard-coded this button text; no string resource exists.
            runLabel = "Apply Motion",
            onRun = {
                viewModel.onEvent(
                    FastSlowVideoEvent.ApplyMotion(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(FastSlowVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
