package com.simform.videoimageeditor.ui.ops.audio

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

/**
 * Crop-audio screen: one immutable state + one sealed event (MVI).
 *
 * Preserved from the old screen: start/end are HH:MM:SS; the run requires
 * start < end ("Start Time < End Time"); the max time of the picked audio
 * is shown. The old screen picked times with a dialog that only offered
 * values strictly between 00:00:00 and the max time; here the times are
 * typed, so the same bounds are enforced as validation instead (end may
 * equal the max time — a crop ending exactly at the file end is valid).
 */
data class CropAudioState(
    val inputPath: String? = null,
    val maxTime: String? = null,
    val maxTimeSeconds: Int? = null,
    val startTime: String = "00:00:00",
    val endTime: String = "00:00:00",
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CropAudioEvent {
    data class AudioPicked(val paths: List<String>) : CropAudioEvent
    data class StartTimeChanged(val value: String) : CropAudioEvent
    data class EndTimeChanged(val value: String) : CropAudioEvent
    data class Crop(val outputPath: String) : CropAudioEvent
    data object Cancel : CropAudioEvent
    data class LogReceived(val text: String) : CropAudioEvent
    data object Succeeded : CropAudioEvent
    data object Failed : CropAudioEvent
}

/** Strict HH:MM:SS to seconds, or null when the text is not a valid time. */
private fun parseTimeToSeconds(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 3) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    val seconds = parts[2].toIntOrNull() ?: return null
    if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null
    return hours * 3600 + minutes * 60 + seconds
}

class CropAudioViewModel :
    OperationViewModel<CropAudioState, CropAudioEvent>(CropAudioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CropAudioEvent) {
        when (event) {
            is CropAudioEvent.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                val durationMs = mediaDurationMs(path)
                setState {
                    copy(
                        inputPath = path,
                        maxTime = durationMs?.let { Common.stringForTime(it) },
                        maxTimeSeconds = durationMs?.let { (it / 1000).toInt() },
                        error = null
                    )
                }
            }
            is CropAudioEvent.StartTimeChanged -> setState { copy(startTime = event.value) }
            is CropAudioEvent.EndTimeChanged -> setState { copy(endTime = event.value) }
            is CropAudioEvent.Crop -> {
                val current = state.value
                val input = current.inputPath
                if (input == null) {
                    setState { copy(error = "Please select Input audio") }
                    return
                }
                val startSeconds = parseTimeToSeconds(current.startTime)
                val endSeconds = parseTimeToSeconds(current.endTime)
                if (startSeconds == null || endSeconds == null) {
                    setState { copy(error = "Please select time in Range") }
                    return
                }
                if (startSeconds >= endSeconds) {
                    setState { copy(error = "Start Time < End Time") }
                    return
                }
                val maxSeconds = current.maxTimeSeconds
                if (maxSeconds != null && endSeconds > maxSeconds) {
                    setState { copy(error = "Please select time in Range") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.cutAudio(input, current.startTime, current.endTime, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(CropAudioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CropAudioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CropAudioEvent.Failed) }
                )
            }
            CropAudioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CropAudioEvent.LogReceived -> setState { copy(log = event.text) }
            CropAudioEvent.Succeeded -> setState { copy(isRunning = false) }
            CropAudioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CropAudioScreen(
    onBack: () -> Unit,
    viewModel: CropAudioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CropAudioEvent.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.crop_audio_using_time), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.select_audio_file)
        ) { picker.pickAudio() }
        state.maxTime?.let { Text("Selected audio max time : $it") }
        TimeField(
            label = stringResource(R.string.select_starting_time),
            value = state.startTime,
            onValueChange = { viewModel.onEvent(CropAudioEvent.StartTimeChanged(it)) }
        )
        TimeField(
            label = stringResource(R.string.select_end_time),
            value = state.endTime,
            onValueChange = { viewModel.onEvent(CropAudioEvent.EndTimeChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.cut_video),
            onRun = {
                viewModel.onEvent(
                    CropAudioEvent.Crop(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(CropAudioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
