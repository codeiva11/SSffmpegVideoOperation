package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.NumberField
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.PickFileRow
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import com.simform.videooperations.SizeOfImage

/** MVI port of ImageToVideoConvertActivity: build a video from one image. */
data class ImageToVideoState(
    val inputPath: String? = null,
    val seconds: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface ImageToVideoEvent {
    data class ImagePicked(val paths: List<String>) : ImageToVideoEvent
    data class SecondsChanged(val value: String) : ImageToVideoEvent
    data class Create(val outputPath: String) : ImageToVideoEvent
    data object Cancel : ImageToVideoEvent
    data class LogReceived(val text: String) : ImageToVideoEvent
    data object Succeeded : ImageToVideoEvent
    data object Failed : ImageToVideoEvent
}

class ImageToVideoViewModel :
    OperationViewModel<ImageToVideoState, ImageToVideoEvent>(ImageToVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: ImageToVideoEvent) {
        when (event) {
            is ImageToVideoEvent.ImagePicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Image not selected") }
                    return
                }
                val size = SizeOfImage(path)
                // imageToVideo() itself reads the frame rate; set it on this
                // instance so the (old) dead-code path actually takes effect.
                queries.FRAME_RATE = Common.getFrameRate(path) ?: 25
                setState {
                    copy(
                        inputPath = path,
                        width = size.width(),
                        height = size.height(),
                        error = null
                    )
                }
            }
            is ImageToVideoEvent.SecondsChanged -> setState { copy(seconds = event.value) }
            is ImageToVideoEvent.Create -> {
                val input = state.value.inputPath ?: return
                val seconds = state.value.seconds.toIntOrNull()
                if (seconds == null || seconds == 0) {
                    setState { copy(error = "Please enter second") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.imageToVideo(
                    input,
                    event.outputPath,
                    seconds,
                    state.value.width,
                    state.value.height
                )
                executeQuery(
                    query,
                    onLog = { onEvent(ImageToVideoEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(ImageToVideoEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(ImageToVideoEvent.Failed) }
                )
            }
            ImageToVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is ImageToVideoEvent.LogReceived -> setState { copy(log = event.text) }
            ImageToVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            ImageToVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun ImageToVideoScreen(
    onBack: () -> Unit,
    viewModel: ImageToVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(ImageToVideoEvent.ImagePicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.image_to_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadImage)
        ) { picker.pickImage() }
        NumberField(
            label = stringResource(R.string.enter_image_display_time_in_second),
            value = state.seconds,
            onValueChange = { viewModel.onEvent(ImageToVideoEvent.SecondsChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.create),
            onRun = {
                viewModel.onEvent(
                    ImageToVideoEvent.Create(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(ImageToVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
