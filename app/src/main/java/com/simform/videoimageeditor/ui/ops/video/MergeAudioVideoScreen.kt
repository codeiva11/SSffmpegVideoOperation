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

/** Merge-video-and-audio screen: one immutable state + one sealed event (MVI). */
data class MergeAudioVideoState(
    val videoPath: String? = null,
    val audioPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface MergeAudioVideoEvent {
    data class VideoPicked(val paths: List<String>) : MergeAudioVideoEvent
    data class AudioPicked(val paths: List<String>) : MergeAudioVideoEvent
    data class Merge(val outputPath: String) : MergeAudioVideoEvent
    data object Cancel : MergeAudioVideoEvent
    data class LogReceived(val text: String) : MergeAudioVideoEvent
    data object Succeeded : MergeAudioVideoEvent
    data object Failed : MergeAudioVideoEvent
}

class MergeAudioVideoViewModel :
    OperationViewModel<MergeAudioVideoState, MergeAudioVideoEvent>(MergeAudioVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: MergeAudioVideoEvent) {
        when (event) {
            is MergeAudioVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(videoPath = path, error = null) }
            }
            is MergeAudioVideoEvent.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                setState { copy(audioPath = path, error = null) }
            }
            is MergeAudioVideoEvent.Merge -> {
                val video = state.value.videoPath
                if (video == null) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                val audio = state.value.audioPath
                if (audio == null) {
                    setState { copy(error = "Please select Input Audio") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.mergeAudioVideo(video, audio, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(MergeAudioVideoEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(MergeAudioVideoEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(MergeAudioVideoEvent.Failed) }
                )
            }
            MergeAudioVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is MergeAudioVideoEvent.LogReceived -> setState { copy(log = event.text) }
            MergeAudioVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            MergeAudioVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun MergeAudioVideoScreen(
    onBack: () -> Unit,
    viewModel: MergeAudioVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // One picker per input slot: pickVideo and pickAudio report to the same
    // callback inside one FilePicker, so each slot gets its own instance.
    val videoPicker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeAudioVideoEvent.VideoPicked(paths))
    }
    val audioPicker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeAudioVideoEvent.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.merge_video_and_audio), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.videoPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { videoPicker.pickVideo() }
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.audioPath,
            buttonText = stringResource(R.string.select_audio_file)
        ) { audioPicker.pickAudio() }
        RunControls(
            running = state.isRunning,
            canRun = state.videoPath != null && state.audioPath != null,
            runLabel = stringResource(R.string.merge_video_and_audio),
            onRun = {
                viewModel.onEvent(
                    MergeAudioVideoEvent.Merge(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(MergeAudioVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
