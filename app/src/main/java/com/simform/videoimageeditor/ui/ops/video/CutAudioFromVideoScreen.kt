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
 * Cut-audio-from-video screen: one immutable state + one sealed event (MVI).
 *
 * Picks a VIDEO file, takes a start/end time range (HH:MM:SS, same
 * validation as Crop Audio: start < end, end may equal the max time),
 * and writes only that range of the video's audio to a separate MP3
 * file — the video stream is never in the output. The video itself is
 * untouched; the output is a new file in the "Ffmpeg Output" folder.
 */
data class CutAudioFromVideoState(
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

sealed interface CutAudioFromVideoEvent {
    data class VideoPicked(val paths: List<String>) : CutAudioFromVideoEvent
    data class StartTimeChanged(val value: String) : CutAudioFromVideoEvent
    data class EndTimeChanged(val value: String) : CutAudioFromVideoEvent
    data class Cut(val outputPath: String) : CutAudioFromVideoEvent
    data object Cancel : CutAudioFromVideoEvent
    data class LogReceived(val text: String) : CutAudioFromVideoEvent
    data object Succeeded : CutAudioFromVideoEvent
    data object Failed : CutAudioFromVideoEvent
}

class CutAudioFromVideoViewModel :
    OperationViewModel<CutAudioFromVideoState, CutAudioFromVideoEvent>(CutAudioFromVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CutAudioFromVideoEvent) {
        when (event) {
            is CutAudioFromVideoEvent.VideoPicked -> {
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
            is CutAudioFromVideoEvent.StartTimeChanged -> setState { copy(startTime = event.value) }
            is CutAudioFromVideoEvent.EndTimeChanged -> setState { copy(endTime = event.value) }
            is CutAudioFromVideoEvent.Cut -> {
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
                // cutAudioFromVideo probes the input audio (FFprobeKit
                // blocks), so the query is built off the main thread —
                // same pattern as the H.265 screen.
                Thread {
                    try {
                        val query = queries.cutAudioFromVideo(
                            input, current.startTime, current.endTime, event.outputPath
                        )
                        executeQuery(
                            query,
                            onLog = { onEvent(CutAudioFromVideoEvent.LogReceived(it.text)) },
                            onSuccess = { onEvent(CutAudioFromVideoEvent.Succeeded) },
                            onCancel = { setState { copy(isRunning = false) } },
                            onFailed = { onEvent(CutAudioFromVideoEvent.Failed) },
                            label = "Cutting audio from video"
                        )
                    } catch (e: IllegalArgumentException) {
                        setState { copy(isRunning = false, error = e.message) }
                    }
                }.start()
            }
            CutAudioFromVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CutAudioFromVideoEvent.LogReceived -> setState { copy(log = event.text) }
            CutAudioFromVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            CutAudioFromVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CutAudioFromVideoScreen(
    onBack: () -> Unit,
    viewModel: CutAudioFromVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CutAudioFromVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.cut_audio_from_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickAnyVideo() }
        state.maxTime?.let { Text("Selected video max time : $it") }
        TimeField(
            label = stringResource(R.string.select_starting_time),
            value = state.startTime,
            onValueChange = { viewModel.onEvent(CutAudioFromVideoEvent.StartTimeChanged(it)) }
        )
        TimeField(
            label = stringResource(R.string.select_end_time),
            value = state.endTime,
            onValueChange = { viewModel.onEvent(CutAudioFromVideoEvent.EndTimeChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.cut_video),
            onRun = {
                viewModel.onEvent(
                    CutAudioFromVideoEvent.Cut(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(CutAudioFromVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
