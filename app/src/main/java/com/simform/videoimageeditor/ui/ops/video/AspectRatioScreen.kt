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
 * MVI port of the old AspectRatioActivity. The old screen hard-coded
 * RATIO_1 ("16:9"); here all seven ratios are offered as radio options
 * with RATIO_1 as the default, so the old behaviour is preserved.
 */
data class AspectRatioState(
    val inputPath: String? = null,
    val ratio: String = Common.RATIO_1,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface AspectRatioEvent {
    data class VideoPicked(val paths: List<String>) : AspectRatioEvent
    data class RatioSelected(val ratio: String) : AspectRatioEvent
    data class ApplyRatio(val outputPath: String) : AspectRatioEvent
    data object Cancel : AspectRatioEvent
    data class LogReceived(val text: String) : AspectRatioEvent
    data object Succeeded : AspectRatioEvent
    data object Failed : AspectRatioEvent
}

class AspectRatioViewModel :
    OperationViewModel<AspectRatioState, AspectRatioEvent>(AspectRatioState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: AspectRatioEvent) {
        when (event) {
            is AspectRatioEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState { copy(inputPath = path, error = null) }
            }
            is AspectRatioEvent.RatioSelected -> setState { copy(ratio = event.ratio) }
            is AspectRatioEvent.ApplyRatio -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.applyRatio(input, state.value.ratio, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(AspectRatioEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(AspectRatioEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(AspectRatioEvent.Failed) }
                )
            }
            AspectRatioEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is AspectRatioEvent.LogReceived -> setState { copy(log = event.text) }
            AspectRatioEvent.Succeeded -> setState { copy(isRunning = false) }
            AspectRatioEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

private val ASPECT_RATIOS = listOf(
    Common.RATIO_1,
    Common.RATIO_2,
    Common.RATIO_3,
    Common.RATIO_4,
    Common.RATIO_5,
    Common.RATIO_6,
    Common.RATIO_7
)

@Composable
fun AspectRatioScreen(
    onBack: () -> Unit,
    viewModel: AspectRatioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(AspectRatioEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.apply_aspect_ratio), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        ASPECT_RATIOS.forEach { ratio ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onEvent(AspectRatioEvent.RatioSelected(ratio)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.ratio == ratio,
                    onClick = { viewModel.onEvent(AspectRatioEvent.RatioSelected(ratio)) }
                )
                Text(ratio)
            }
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.apply_aspect_ratio),
            onRun = {
                viewModel.onEvent(
                    AspectRatioEvent.ApplyRatio(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(AspectRatioEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
