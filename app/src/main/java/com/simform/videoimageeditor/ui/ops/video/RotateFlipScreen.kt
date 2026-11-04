package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
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
 * MVI port of the old VideoRotateFlipActivity. The old screen had 7 buttons,
 * each running immediately; here they are radio options + one Run button.
 * Values preserved exactly: rotate uses rotateVideo(degree 90/180/270),
 * flip uses flipVideo(degree 0..3) with the old labels.
 */
enum class RotateFlipOption(val labelRes: Int, val degree: Int, val isRotate: Boolean) {
    ROTATE_90(R.string.rotate_90_degree, 90, true),
    ROTATE_180(R.string.rotate_180_degree, 180, true),
    ROTATE_270(R.string.rotate_270_degree, 270, true),
    FLIP_COUNTER_CLOCKWISE_VERTICAL(R.string._90_counter_clockwise_and_vertical_flip, 0, false),
    FLIP_CLOCKWISE(R.string._90_clockwise, 1, false),
    FLIP_COUNTER_CLOCKWISE(R.string._90_counter_clockwise, 2, false),
    FLIP_CLOCKWISE_VERTICAL(R.string._90_clockwise_and_vertical_flip, 3, false)
}

data class RotateFlipState(
    val inputPath: String? = null,
    val option: RotateFlipOption = RotateFlipOption.ROTATE_90,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface RotateFlipEvent {
    data class VideoPicked(val paths: List<String>) : RotateFlipEvent
    data class OptionSelected(val option: RotateFlipOption) : RotateFlipEvent
    data class Apply(val outputPath: String) : RotateFlipEvent
    data object Cancel : RotateFlipEvent
    data class LogReceived(val text: String) : RotateFlipEvent
    data object Succeeded : RotateFlipEvent
    data object Failed : RotateFlipEvent
}

class RotateFlipViewModel :
    OperationViewModel<RotateFlipState, RotateFlipEvent>(RotateFlipState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: RotateFlipEvent) {
        when (event) {
            is RotateFlipEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is RotateFlipEvent.OptionSelected -> setState { copy(option = event.option) }
            is RotateFlipEvent.Apply -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val option = state.value.option
                val query = if (option.isRotate) {
                    queries.rotateVideo(input, option.degree, event.outputPath)
                } else {
                    queries.flipVideo(input, option.degree, event.outputPath)
                }
                executeQuery(
                    query,
                    onLog = { onEvent(RotateFlipEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(RotateFlipEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(RotateFlipEvent.Failed) }
                )
            }
            RotateFlipEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is RotateFlipEvent.LogReceived -> setState { copy(log = event.text) }
            RotateFlipEvent.Succeeded -> setState { copy(isRunning = false) }
            RotateFlipEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun RotateFlipScreen(
    onBack: () -> Unit,
    viewModel: RotateFlipViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(RotateFlipEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.video_rotate), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RotateFlipOption.values().forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onEvent(RotateFlipEvent.OptionSelected(option)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.option == option,
                    onClick = { viewModel.onEvent(RotateFlipEvent.OptionSelected(option)) }
                )
                Text(stringResource(option.labelRes))
            }
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            // Old screen had no single run button (each option ran immediately);
            // no string resource exists for this label.
            runLabel = "Apply",
            onRun = {
                viewModel.onEvent(
                    RotateFlipEvent.Apply(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(RotateFlipEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
