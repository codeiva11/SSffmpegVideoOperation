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
import com.simform.videoimageeditor.ui.common.mediaDurationMs
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension

/**
 * MVI port of the old VideoFadeInFadeOutActivity. Duration is read at pick
 * time via mediaDurationMs (ms -> seconds, same as the old
 * TimeUnit.MILLISECONDS.toSeconds), and the old hardcodes are preserved:
 * fadeInEndSeconds = 3, fadeOutStartSeconds = 3.
 */
data class FadeInFadeOutState(
    val inputPath: String? = null,
    val durationSeconds: Long? = null,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface FadeInFadeOutEvent {
    data class VideoPicked(val paths: List<String>) : FadeInFadeOutEvent
    data class ApplyFade(val outputPath: String) : FadeInFadeOutEvent
    data object Cancel : FadeInFadeOutEvent
    data class LogReceived(val text: String) : FadeInFadeOutEvent
    data object Succeeded : FadeInFadeOutEvent
    data object Failed : FadeInFadeOutEvent
}

class FadeInFadeOutViewModel :
    OperationViewModel<FadeInFadeOutState, FadeInFadeOutEvent>(FadeInFadeOutState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: FadeInFadeOutEvent) {
        when (event) {
            is FadeInFadeOutEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState {
                    copy(
                        inputPath = path,
                        durationSeconds = mediaDurationMs(path)?.div(1000),
                        error = null
                    )
                }
            }
            is FadeInFadeOutEvent.ApplyFade -> {
                val input = state.value.inputPath ?: return
                val duration = state.value.durationSeconds ?: return
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.videoFadeInFadeOut(
                    input,
                    duration,
                    fadeInEndSeconds = 3,
                    fadeOutStartSeconds = 3,
                    output = event.outputPath
                )
                executeQuery(
                    query,
                    onLog = { onEvent(FadeInFadeOutEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(FadeInFadeOutEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(FadeInFadeOutEvent.Failed) }
                )
            }
            FadeInFadeOutEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is FadeInFadeOutEvent.LogReceived -> setState { copy(log = event.text) }
            FadeInFadeOutEvent.Succeeded -> setState { copy(isRunning = false) }
            FadeInFadeOutEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun FadeInFadeOutScreen(
    onBack: () -> Unit,
    viewModel: FadeInFadeOutViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(FadeInFadeOutEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.video_fade_in_and_fade_out), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickVideo() }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null && state.durationSeconds != null,
            runLabel = stringResource(R.string.apply_fade_in_and_fade_out),
            onRun = {
                viewModel.onEvent(
                    FadeInFadeOutEvent.ApplyFade(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(FadeInFadeOutEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
