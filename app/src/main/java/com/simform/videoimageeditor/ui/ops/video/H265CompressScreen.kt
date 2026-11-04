package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import java.io.File

/**
 * H.265 (HEVC) compression: the smallest size at the best quality.
 * libx265 at CRF 28 holds roughly H.264 CRF 23 quality at about half
 * the file size. Preset is Auto for all qualities: x265 has no preset
 * named "auto", its default is medium, so no -preset flag is passed.
 * Default is Balanced (CRF 28, Auto).
 */
enum class H265Quality(val crf: Int, val preset: String, val label: String) {
    BEST(24, "auto", "Best quality (CRF 24, AAC 192k)"),
    BALANCED(28, "auto", "Balanced — small size, best quality (CRF 28, AAC 192k)"),
    SMALLEST(32, "auto", "Smallest size (CRF 32, AAC 192k)")
}

data class H265CompressState(
    val inputPath: String? = null,
    val inputSize: String? = null,
    val quality: H265Quality = H265Quality.BALANCED,
    val outputPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface H265CompressEvent {
    data class VideoPicked(val paths: List<String>) : H265CompressEvent
    data class QualitySelected(val quality: H265Quality) : H265CompressEvent
    data class Compress(val outputPath: String) : H265CompressEvent
    data object Cancel : H265CompressEvent
    data class LogReceived(val text: String) : H265CompressEvent
    data object Succeeded : H265CompressEvent
    data object Failed : H265CompressEvent
}

class H265CompressViewModel :
    OperationViewModel<H265CompressState, H265CompressEvent>(H265CompressState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: H265CompressEvent) {
        when (event) {
            is H265CompressEvent.VideoPicked -> {
                val path = event.paths.firstOrNull()
                if (path == null) {
                    setState { copy(error = "Video not selected") }
                    return
                }
                setState {
                    copy(
                        inputPath = path,
                        inputSize = Common.getFileSize(File(path)),
                        error = null
                    )
                }
            }
            is H265CompressEvent.QualitySelected -> setState { copy(quality = event.quality) }
            is H265CompressEvent.Compress -> {
                val input = state.value.inputPath ?: return
                val quality = state.value.quality
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.compressH265(input, event.outputPath, quality.crf, quality.preset)
                executeQuery(
                    query,
                    onLog = { onEvent(H265CompressEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(H265CompressEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(H265CompressEvent.Failed) }
                )
            }
            H265CompressEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is H265CompressEvent.LogReceived -> setState { copy(log = event.text) }
            H265CompressEvent.Succeeded -> setState { copy(isRunning = false) }
            H265CompressEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun H265CompressScreen(
    onBack: () -> Unit,
    viewModel: H265CompressViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(H265CompressEvent.VideoPicked(paths))
    }
    OperationScaffold(title = stringResource(R.string.h265_compress), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = state.inputPath,
            buttonText = stringResource(R.string.uploadvideo)
        ) { picker.pickAnyVideo() }
        state.inputSize?.let { Text("Input file Size : $it") }
        Text(
            "Any video file works as input. Output is H.265 video with " +
                "high-quality AAC 192k audio, at about half the size of H.264. " +
                "Preset Auto.",
            style = MaterialTheme.typography.bodySmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            H265Quality.entries.forEach { quality ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onEvent(H265CompressEvent.QualitySelected(quality)) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = state.quality == quality,
                        onClick = { viewModel.onEvent(H265CompressEvent.QualitySelected(quality)) }
                    )
                    Text(quality.label)
                }
            }
        }
        RunControls(
            running = state.isRunning,
            canRun = state.inputPath != null,
            runLabel = stringResource(R.string.compress),
            onRun = {
                viewModel.onEvent(
                    H265CompressEvent.Compress(Common.getFilePath(context, Common.VIDEO))
                )
            },
            onCancel = { viewModel.onEvent(H265CompressEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.error?.let { ErrorText(it) }
    }
}
