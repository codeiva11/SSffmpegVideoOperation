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

/** Merge-image-and-MP3 screen: one immutable state + one sealed event (MVI). */
data class MergeImageAndMp3State(
    val imagePath: String? = null,
    val audioPath: String? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface MergeImageAndMp3Event {
    data class ImagePicked(val paths: List<String>) : MergeImageAndMp3Event
    data class AudioPicked(val paths: List<String>) : MergeImageAndMp3Event
    data class Merge(val outputPath: String) : MergeImageAndMp3Event
    data object Cancel : MergeImageAndMp3Event
    data class LogReceived(val text: String) : MergeImageAndMp3Event
    data object Succeeded : MergeImageAndMp3Event
    data object Failed : MergeImageAndMp3Event
}

class MergeImageAndMp3ViewModel :
    OperationViewModel<MergeImageAndMp3State, MergeImageAndMp3Event>(MergeImageAndMp3State()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: MergeImageAndMp3Event) {
        when (event) {
            is MergeImageAndMp3Event.ImagePicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Image not selected") }
                    return
                }
                setState { copy(imagePath = path, error = null) }
            }
            is MergeImageAndMp3Event.AudioPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Audio not selected") }
                    return
                }
                setState { copy(audioPath = path, error = null) }
            }
            is MergeImageAndMp3Event.Merge -> {
                val image = state.value.imagePath
                if (image == null) {
                    setState { copy(error = "Please select input image") }
                    return
                }
                val audio = state.value.audioPath
                if (audio == null) {
                    setState { copy(error = "Please select Input Audio") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.mergeImageAndAudio(image, audio, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(MergeImageAndMp3Event.LogReceived(it.text)) },
                    onSuccess = { onEvent(MergeImageAndMp3Event.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(MergeImageAndMp3Event.Failed) }
                )
            }
            MergeImageAndMp3Event.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is MergeImageAndMp3Event.LogReceived -> setState { copy(log = event.text) }
            MergeImageAndMp3Event.Succeeded -> setState { copy(isRunning = false) }
            MergeImageAndMp3Event.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun MergeImageAndMp3Screen(
    onBack: () -> Unit,
    viewModel: MergeImageAndMp3ViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // One picker per input slot (see MergeAudioVideoScreen).
    val imagePicker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeImageAndMp3Event.ImagePicked(paths))
    }
    val audioPicker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeImageAndMp3Event.AudioPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.merge_image_and_audio), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.imagePath,
            buttonText = stringResource(R.string.uploadImage)
        ) { imagePicker.pickImage() }
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.audioPath,
            buttonText = stringResource(R.string.select_mp3_file)
        ) { audioPicker.pickAudio() }
        RunControls(
            running = state.isRunning,
            canRun = state.imagePath != null && state.audioPath != null,
            runLabel = stringResource(R.string.merge_image_and_audio),
            onRun = {
                viewModel.onEvent(
                    MergeImageAndMp3Event.Merge(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(MergeImageAndMp3Event.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
