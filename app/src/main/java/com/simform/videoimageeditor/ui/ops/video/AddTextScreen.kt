package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.layout.Arrangement
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
import com.simform.videoimageeditor.ui.common.TextField
import com.simform.videoimageeditor.ui.common.videoDimensions
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/** MVI port of AddTextOnVideoActivity: draw text on a video. */
data class AddTextState(
    val videoPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val text: String = "",
    val xPos: String = "",
    val yPos: String = "",
    val showBackground: Boolean = true,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface AddTextEvent {
    data class VideoPicked(val paths: List<String>) : AddTextEvent
    data class TextChanged(val value: String) : AddTextEvent
    data class XChanged(val value: String) : AddTextEvent
    data class YChanged(val value: String) : AddTextEvent
    data class BackgroundChanged(val value: Boolean) : AddTextEvent
    data class Add(val outputPath: String, val fontPath: String) : AddTextEvent
    data object Cancel : AddTextEvent
    data class LogReceived(val text: String) : AddTextEvent
    data object Succeeded : AddTextEvent
    data object Failed : AddTextEvent
}

class AddTextViewModel :
    OperationViewModel<AddTextState, AddTextEvent>(AddTextState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: AddTextEvent) {
        when (event) {
            is AddTextEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                val (w, h) = videoDimensions(path) ?: (null to null)
                setState { copy(videoPath = path, width = w, height = h, error = null) }
            }
            is AddTextEvent.TextChanged -> setState { copy(text = event.value) }
            is AddTextEvent.XChanged -> setState { copy(xPos = event.value) }
            is AddTextEvent.YChanged -> setState { copy(yPos = event.value) }
            is AddTextEvent.BackgroundChanged -> setState { copy(showBackground = event.value) }
            is AddTextEvent.Add -> {
                val video = state.value.videoPath
                if (video == null) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                if (state.value.text.isBlank()) {
                    setState { copy(error = "Please enter text for this process") }
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
                // Old hardcodes preserved: fontSize 28, fontColor "red".
                val query = queries.addTextOnVideo(
                    video,
                    state.value.text,
                    xPos,
                    yPos,
                    fontPath = event.fontPath,
                    isTextBackgroundDisplay = state.value.showBackground,
                    fontSize = 28,
                    fontcolor = "red",
                    output = event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(AddTextEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(AddTextEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(AddTextEvent.Failed) }
                )
            }
            AddTextEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is AddTextEvent.LogReceived -> setState { copy(log = event.text) }
            AddTextEvent.Succeeded -> setState { copy(isRunning = false) }
            AddTextEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun AddTextScreen(
    onBack: () -> Unit,
    viewModel: AddTextViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(AddTextEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.add_text_on_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.videoPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        TextField(
            label = stringResource(R.string.enter_text),
            value = state.text,
            onValueChange = { viewModel.onEvent(AddTextEvent.TextChanged(it)) }
        )
        TextField(
            label = stringResource(R.string.x),
            value = state.xPos,
            onValueChange = { viewModel.onEvent(AddTextEvent.XChanged(it)) }
        )
        TextField(
            label = stringResource(R.string.y),
            value = state.yPos,
            onValueChange = { viewModel.onEvent(AddTextEvent.YChanged(it)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show text background")
            Switch(
                checked = state.showBackground,
                onCheckedChange = { viewModel.onEvent(AddTextEvent.BackgroundChanged(it)) }
            )
        }
        RunControls(
            running = state.isRunning,
            canRun = state.videoPath != null,
            runLabel = stringResource(R.string.add_text_on_video),
            onRun = {
                viewModel.onEvent(
                    AddTextEvent.Add(
                        outputPath = Common.getFilePath(context, Common.VIDEO),
                        fontPath = Common.getFileFromAssets(context, "little_lord.ttf").absolutePath
                    )
                )
            },
            onCancel = { viewModel.onEvent(AddTextEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
