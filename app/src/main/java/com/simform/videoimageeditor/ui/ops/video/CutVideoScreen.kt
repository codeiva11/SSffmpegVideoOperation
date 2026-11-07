package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.material3.Text
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
import com.simform.videoimageeditor.ui.common.TimeField
import com.simform.videoimageeditor.ui.common.mediaDurationMs
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/** MVI port of CutVideoUsingTimeActivity: cut a video between two HH:MM:SS times. */
data class CutVideoState(
    val inputPath: String? = null,
    val maxTimeMs: Long? = null,
    val startTime: String = "00:00:00",
    val endTime: String = "00:00:00",
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CutVideoEvent {
    data class VideoPicked(val paths: List<String>) : CutVideoEvent
    data class StartTimeChanged(val value: String) : CutVideoEvent
    data class EndTimeChanged(val value: String) : CutVideoEvent
    data class Cut(val outputPath: String) : CutVideoEvent
    data object Cancel : CutVideoEvent
    data class LogReceived(val text: String) : CutVideoEvent
    data object Succeeded : CutVideoEvent
    data object Failed : CutVideoEvent
}

class CutVideoViewModel :
    OperationViewModel<CutVideoState, CutVideoEvent>(CutVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CutVideoEvent) {
        when (event) {
            is CutVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                queries.FRAME_RATE = Common.getFrameRate(path) ?: 25
                setState {
                    copy(
                        inputPath = path,
                        maxTimeMs = mediaDurationMs(path),
                        error = null
                    )
                }
            }
            is CutVideoEvent.StartTimeChanged -> setState { copy(startTime = event.value) }
            is CutVideoEvent.EndTimeChanged -> setState { copy(endTime = event.value) }
            is CutVideoEvent.Cut -> {
                val input = state.value.inputPath ?: return
                val start = state.value.startTime
                val end = state.value.endTime
                // Same validations as the old activity: both times required,
                // start must be before end, and both must lie inside the video.
                // (The old time picker also rejected exactly 00:00:00; the new
                // default start IS 00:00:00, so only start < end is enforced.)
                if (start.isBlank()) {
                    setState { copy(error = "Please select start time") }
                    return
                }
                if (end.isBlank()) {
                    setState { copy(error = "Please select end time") }
                    return
                }
                val startSec = parseHms(start)
                val endSec = parseHms(end)
                if (startSec == null || endSec == null) {
                    setState { copy(error = "Please select time in Range") }
                    return
                }
                if (startSec >= endSec) {
                    setState { copy(error = "Start Time < End Time") }
                    return
                }
                state.value.maxTimeMs?.let { maxMs ->
                    if (endSec * 1000L > maxMs || startSec * 1000L >= maxMs) {
                        setState { copy(error = "Please select time in Range") }
                        return
                    }
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.cutVideo(input, start, end, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(CutVideoEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CutVideoEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CutVideoEvent.Failed) }
                )
            }
            CutVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CutVideoEvent.LogReceived -> setState { copy(log = event.text) }
            CutVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            CutVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }

    private fun parseHms(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toIntOrNull() ?: return null
        if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null
        return hours * 3600 + minutes * 60 + seconds
    }
}

@Composable
fun CutVideoScreen(
    onBack: () -> Unit,
    viewModel: CutVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CutVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.cut_video_using_time), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        state.maxTimeMs?.let {
            Text("Selected video max time : ${Common.stringForTime(it)}")
        }
        TimeField(
            label = stringResource(R.string.select_starting_time),
            value = state.startTime,
            onValueChange = { viewModel.onEvent(CutVideoEvent.StartTimeChanged(it)) }
        )
        TimeField(
            label = stringResource(R.string.select_end_time),
            value = state.endTime,
            onValueChange = { viewModel.onEvent(CutVideoEvent.EndTimeChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.cut_video),
            onRun = {
                viewModel.onEvent(
                    CutVideoEvent.Cut(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(CutVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
