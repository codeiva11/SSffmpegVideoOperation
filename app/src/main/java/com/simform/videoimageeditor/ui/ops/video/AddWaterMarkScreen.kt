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
import com.simform.videoimageeditor.ui.common.TextField
import com.simform.videoimageeditor.ui.common.videoDimensions
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/** MVI port of AddWaterMarkOnVideoActivity: overlay an image on a video. */
data class AddWaterMarkState(
    val videoPath: String? = null,
    val imagePath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val xPos: String = "",
    val yPos: String = "",
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface AddWaterMarkEvent {
    data class VideoPicked(val paths: List<String>) : AddWaterMarkEvent
    data class ImagePicked(val paths: List<String>) : AddWaterMarkEvent
    data class XChanged(val value: String) : AddWaterMarkEvent
    data class YChanged(val value: String) : AddWaterMarkEvent
    data class Add(val outputPath: String) : AddWaterMarkEvent
    data object Cancel : AddWaterMarkEvent
    data class LogReceived(val text: String) : AddWaterMarkEvent
    data object Succeeded : AddWaterMarkEvent
    data object Failed : AddWaterMarkEvent
}

class AddWaterMarkViewModel :
    OperationViewModel<AddWaterMarkState, AddWaterMarkEvent>(AddWaterMarkState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: AddWaterMarkEvent) {
        when (event) {
            is AddWaterMarkEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                val (w, h) = videoDimensions(path) ?: (null to null)
                setState { copy(videoPath = path, width = w, height = h, error = null) }
            }
            is AddWaterMarkEvent.ImagePicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Image not selected") }
                    return
                }
                setState { copy(imagePath = path, error = null) }
            }
            is AddWaterMarkEvent.XChanged -> setState { copy(xPos = event.value) }
            is AddWaterMarkEvent.YChanged -> setState { copy(yPos = event.value) }
            is AddWaterMarkEvent.Add -> {
                val video = state.value.videoPath
                if (video == null) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                val image = state.value.imagePath
                if (image == null) {
                    setState { copy(error = "Please select Input image") }
                    return
                }
                // Old validation: X/Y are percentages in (0, 100].
                val xPct = state.value.xPos.toFloatOrNull()
                if (state.value.xPos.isBlank()) {
                    setState { copy(error = "Please add X position") }
                    return
                }
                if (xPct == null || xPct > 100 || xPct <= 0) {
                    setState { copy(error = "Please add X in percentage(%)") }
                    return
                }
                val yPct = state.value.yPos.toFloatOrNull()
                if (state.value.yPos.isBlank()) {
                    setState { copy(error = "Please add Y position") }
                    return
                }
                if (yPct == null || yPct > 100 || yPct <= 0) {
                    setState { copy(error = "Please add Y in percentage(%)") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val xPos = state.value.width?.let { (xPct * it) / 100 }
                val yPos = state.value.height?.let { (yPct * it) / 100 }
                val query = queries.addVideoWaterMark(video, image, xPos, yPos, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(AddWaterMarkEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(AddWaterMarkEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(AddWaterMarkEvent.Failed) }
                )
            }
            AddWaterMarkEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is AddWaterMarkEvent.LogReceived -> setState { copy(log = event.text) }
            AddWaterMarkEvent.Succeeded -> setState { copy(isRunning = false) }
            AddWaterMarkEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun AddWaterMarkScreen(
    onBack: () -> Unit,
    viewModel: AddWaterMarkViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val videoPicker = rememberFilePicker { paths ->
        viewModel.onEvent(AddWaterMarkEvent.VideoPicked(paths))
    }
    val imagePicker = rememberFilePicker { paths ->
        viewModel.onEvent(AddWaterMarkEvent.ImagePicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.add_water_mark_on_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.videoPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { videoPicker.pickVideo() }
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.imagePath,
            buttonText = stringResource(R.string.uploadImage)
        ) { imagePicker.pickImage() }
        TextField(
            label = stringResource(R.string.x),
            value = state.xPos,
            onValueChange = { viewModel.onEvent(AddWaterMarkEvent.XChanged(it)) }
        )
        TextField(
            label = stringResource(R.string.y),
            value = state.yPos,
            onValueChange = { viewModel.onEvent(AddWaterMarkEvent.YChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.videoPath != null && state.imagePath != null,
            runLabel = stringResource(R.string.add_water_mark),
            onRun = {
                viewModel.onEvent(
                    AddWaterMarkEvent.Add(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(AddWaterMarkEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
