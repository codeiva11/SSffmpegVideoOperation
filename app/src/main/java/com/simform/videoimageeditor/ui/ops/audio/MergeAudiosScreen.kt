package com.simform.videoimageeditor.ui.ops.audio

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
import com.simform.videooperations.Paths

/** Merge-audios screen: one immutable state + one sealed event (MVI). */
data class MergeAudiosState(
    val audioPaths: List<String> = emptyList(),
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface MergeAudiosEvent {
    data class AudiosPicked(val paths: List<String>) : MergeAudiosEvent
    data class Merge(val outputPath: String) : MergeAudiosEvent
    data object Cancel : MergeAudiosEvent
    data class LogReceived(val text: String) : MergeAudiosEvent
    data object Succeeded : MergeAudiosEvent
    data object Failed : MergeAudiosEvent
}

class MergeAudiosViewModel :
    OperationViewModel<MergeAudiosState, MergeAudiosEvent>(MergeAudiosState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: MergeAudiosEvent) {
        when (event) {
            is MergeAudiosEvent.AudiosPicked -> {
                if (event.paths.size < 2) {
                    setState { copy(error = "Please select minimum 2 audio files") }
                    return
                }
                setState { copy(audioPaths = event.paths, error = null) }
            }
            is MergeAudiosEvent.Merge -> {
                val current = state.value
                if (current.audioPaths.size < 2) {
                    setState { copy(error = "Please select minimum 2 audio files") }
                    return
                }
                val pathsList = ArrayList<Paths>().apply {
                    current.audioPaths.forEach { path ->
                        add(Paths().apply { filePath = path; isImageFile = true })
                    }
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                // Duration is hardcoded to "first" in the old screen.
                val query = queries.mergeAudios(pathsList, Common.DURATION_FIRST, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(MergeAudiosEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(MergeAudiosEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(MergeAudiosEvent.Failed) }
                )
            }
            MergeAudiosEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is MergeAudiosEvent.LogReceived -> setState { copy(log = event.text) }
            MergeAudiosEvent.Succeeded -> setState { copy(isRunning = false) }
            MergeAudiosEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun MergeAudiosScreen(
    onBack: () -> Unit,
    viewModel: MergeAudiosViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeAudiosEvent.AudiosPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.merge_audios), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = if (state.audioPaths.isNotEmpty()) "${state.audioPaths.size} Audio selected" else null,
            buttonText = stringResource(R.string.select_audio_file)
        ) { picker.pickAudios() }
        RunControls(
            running = state.isRunning,
            canRun = state.audioPaths.size >= 2,
            runLabel = stringResource(R.string.merge_audios),
            onRun = {
                viewModel.onEvent(
                    MergeAudiosEvent.Merge(Common.getFilePath(context, Common.MP3))
                )
            },
            onCancel = { viewModel.onEvent(MergeAudiosEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
