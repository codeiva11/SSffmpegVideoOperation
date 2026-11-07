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
import com.simform.videoimageeditor.ui.common.parseHhMmSsToSeconds
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/**
 * Remove-audio-range-from-video screen: one immutable state + one sealed
 * event (MVI).
 *
 * Picks a VIDEO file, takes a start/end time range (HH:MM:SS, same
 * validation as Crop Audio: start < end, end may equal the max time),
 * and writes a video in which the audio is muted ONLY in that range —
 * the video stream is copied untouched and the audio before/after the
 * range stays normal. The original video is untouched; the output is a
 * new file in the "Ffmpeg Output" folder.
 */
data class RemoveAudioRangeState(
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

sealed interface RemoveAudioRangeEvent {
    data class VideoPicked(val paths: List<String>) : RemoveAudioRangeEvent
    data class StartTimeChanged(val value: String) : RemoveAudioRangeEvent
    data class EndTimeChanged(val value: String) : RemoveAudioRangeEvent
    data class Remove(val outputPath: String) : RemoveAudioRangeEvent
    data object Cancel : RemoveAudioRangeEvent
    data class LogReceived(val text: String) : RemoveAudioRangeEvent
    data object Succeeded : RemoveAudioRangeEvent
    data object Failed : RemoveAudioRangeEvent
}

class RemoveAudioRangeViewModel :
    OperationViewModel<RemoveAudioRangeState, RemoveAudioRangeEvent>(RemoveAudioRangeState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: RemoveAudioRangeEvent) {
        when (event) {
            is RemoveAudioRangeEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
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
            is RemoveAudioRangeEvent.StartTimeChanged -> setState { copy(startTime = event.value) }
            is RemoveAudioRangeEvent.EndTimeChanged -> setState { copy(endTime = event.value) }
            is RemoveAudioRangeEvent.Remove -> {
                val current = state.value
                val input = current.inputPath
                if (input == null) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                val startSeconds = parseHhMmSsToSeconds(current.startTime)
                val endSeconds = parseHhMmSsToSeconds(current.endTime)
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
                // removeAudioRangeFromVideo probes the input audio
                // (FFprobeKit blocks), so the query is built off the main
                // thread — same pattern as the H.265 screen.
                Thread {
                    try {
                        val query = queries.removeAudioRangeFromVideo(
                            input, startSeconds, endSeconds, event.outputPath
                        )
                        executeQuery(
                            query,
                            onLog = { onEvent(RemoveAudioRangeEvent.LogReceived(it.text)) },
                            onSuccess = { onEvent(RemoveAudioRangeEvent.Succeeded) },
                            onCancel = { setState { copy(isRunning = false) } },
                            onFailed = { onEvent(RemoveAudioRangeEvent.Failed) },
                            label = "Removing audio range from video"
                        )
                    } catch (e: IllegalArgumentException) {
                        setState { copy(isRunning = false, error = e.message) }
                    }
                }.start()
            }
            RemoveAudioRangeEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is RemoveAudioRangeEvent.LogReceived -> setState { copy(log = event.text) }
            RemoveAudioRangeEvent.Succeeded -> setState { copy(isRunning = false) }
            RemoveAudioRangeEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun RemoveAudioRangeScreen(
    onBack: () -> Unit,
    viewModel: RemoveAudioRangeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(RemoveAudioRangeEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.remove_audio_range_from_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickAnyVideo() }
        state.maxTime?.let { Text("Selected video max time : $it") }
        TimeField(
            label = stringResource(R.string.select_starting_time),
            value = state.startTime,
            onValueChange = { viewModel.onEvent(RemoveAudioRangeEvent.StartTimeChanged(it)) }
        )
        TimeField(
            label = stringResource(R.string.select_end_time),
            value = state.endTime,
            onValueChange = { viewModel.onEvent(RemoveAudioRangeEvent.EndTimeChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.remove_audio),
            onRun = {
                viewModel.onEvent(
                    RemoveAudioRangeEvent.Remove(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(RemoveAudioRangeEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
