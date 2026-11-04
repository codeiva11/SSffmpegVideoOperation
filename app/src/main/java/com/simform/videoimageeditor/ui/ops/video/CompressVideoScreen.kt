package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.simform.videoimageeditor.ui.common.videoDimensions
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import java.io.File

/** Reference video screen: one immutable state + one sealed event (MVI). */
data class CompressVideoState(
    val inputPath: String? = null,
    val inputSize: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CompressVideoEvent {
    data class VideoPicked(val paths: List<String>) : CompressVideoEvent
    data class Compress(val outputPath: String) : CompressVideoEvent
    data object Cancel : CompressVideoEvent
    data class LogReceived(val text: String) : CompressVideoEvent
    data object Succeeded : CompressVideoEvent
    data object Failed : CompressVideoEvent
}

class CompressVideoViewModel :
    OperationViewModel<CompressVideoState, CompressVideoEvent>(CompressVideoState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CompressVideoEvent) {
        when (event) {
            is CompressVideoEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                val (w, h) = videoDimensions(path) ?: (null to null)
                // Frame rate is read here and set on THIS instance (the old code
                // called Common.getFrameRate, which wrote to a throwaway instance).
                queries.FRAME_RATE = Common.getFrameRate(path) ?: 25
                setState {
                    copy(
                        inputPath = path,
                        inputSize = Common.getFileSize(File(path)),
                        width = w,
                        height = h,
                        error = null
                    )
                }
            }
            is CompressVideoEvent.Compress -> {
                val input = state.value.inputPath ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.compressor(
                    input,
                    state.value.width,
                    state.value.height,
                    event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(CompressVideoEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CompressVideoEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CompressVideoEvent.Failed) }
                )
            }
            CompressVideoEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CompressVideoEvent.LogReceived -> setState { copy(log = event.text) }
            CompressVideoEvent.Succeeded -> setState { copy(isRunning = false) }
            CompressVideoEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CompressVideoScreen(
    onBack: () -> Unit,
    viewModel: CompressVideoViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CompressVideoEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.compress_a_video), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        state.inputSize?.let { Text("Input file Size : $it") }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.compress),
            onRun = {
                viewModel.onEvent(
                    CompressVideoEvent.Compress(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(CompressVideoEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
