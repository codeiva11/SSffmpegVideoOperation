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
import com.simform.videoimageeditor.ui.common.videoDimensions
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import com.simform.videooperations.Paths

/** MVI port of CombineVideosActivity: join several videos into one video. */
data class CombineVideosState(
    val videoPaths: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface CombineVideosEvent {
    data class VideosPicked(val paths: List<String>) : CombineVideosEvent
    data class Combine(val outputPath: String) : CombineVideosEvent
    data object Cancel : CombineVideosEvent
    data class LogReceived(val text: String) : CombineVideosEvent
    data object Succeeded : CombineVideosEvent
    data object Failed : CombineVideosEvent
}

class CombineVideosViewModel :
    OperationViewModel<CombineVideosState, CombineVideosEvent>(CombineVideosState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: CombineVideosEvent) {
        when (event) {
            is CombineVideosEvent.VideosPicked -> {
                if (event.paths.isEmpty()) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                // The old activity tried to read the dimensions through a
                // cross-layout synthetic (tvInputPathVideo from another
                // layout) that never resolved, so width/height stayed 0 in
                // practice. The evident intent was the first video's size,
                // which is what this port uses.
                val (w, h) = event.paths.firstOrNull()
                    ?.let { videoDimensions(it) } ?: (null to null)
                setState { copy(videoPaths = event.paths, width = w, height = h, error = null) }
            }
            is CombineVideosEvent.Combine -> {
                if (state.value.videoPaths.isEmpty()) {
                    setState { copy(error = "Please select Input video") }
                    return
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val pathsList = ArrayList<Paths>().apply {
                    state.value.videoPaths.forEach { path ->
                        add(Paths().apply {
                            filePath = path
                            isImageFile = false
                        })
                    }
                }
                val query = queries.combineVideos(
                    pathsList,
                    state.value.width,
                    state.value.height,
                    event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(CombineVideosEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(CombineVideosEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(CombineVideosEvent.Failed) }
                )
            }
            CombineVideosEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is CombineVideosEvent.LogReceived -> setState { copy(log = event.text) }
            CombineVideosEvent.Succeeded -> setState { copy(isRunning = false) }
            CombineVideosEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun CombineVideosScreen(
    onBack: () -> Unit,
    viewModel: CombineVideosViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(CombineVideosEvent.VideosPicked(paths))
    }
    val count = state.videoPaths.size
    OperationScaffold(title = stringResource(R.string.merge_videos), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = if (count == 0) null
                else "$count" + (if (count == 1) " Video " else " Videos ") + "selected",
            buttonText = stringResource(R.string.select_videos)
        ) { picker.pickVideos() }
        RunControls(
            running = state.isRunning,
            canRun = state.videoPaths.isNotEmpty(),
            runLabel = stringResource(R.string.combine),
            onRun = {
                viewModel.onEvent(
                    CombineVideosEvent.Combine(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(CombineVideosEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
