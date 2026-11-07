package com.simform.videoimageeditor.ui.ops.audio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.PickFileRow
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.common.TextField
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.pick.rememberFilePicker
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import com.simform.videooperations.Paths
import com.simform.videooperations.SizeOfImage
import java.io.File

/**
 * Merge-GIF screen: one immutable state + one sealed event (MVI).
 *
 * Preserved from the old screen: exactly the old validation order and
 * messages; positions/scales are percentages greater than 0 and up to 100
 * of the FIRST picked
 * GIF's intrinsic size; the query overlays the second GIF onto the first
 * ([1] onto [0] in the filter), so picking more than two GIFs only feeds
 * extra unused inputs — the old screen required exactly two.
 */
data class MergeGifState(
    val gifPaths: List<String> = emptyList(),
    val gifWidth: Int? = null,
    val gifHeight: Int? = null,
    val xPos: String = "",
    val yPos: String = "",
    val widthScale: String = "",
    val heightScale: String = "",
    val outputPath: String? = null,
    val previewPath: String? = null,
    val log: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface MergeGifEvent {
    data class GifsPicked(val paths: List<String>) : MergeGifEvent
    data class XPosChanged(val value: String) : MergeGifEvent
    data class YPosChanged(val value: String) : MergeGifEvent
    data class WidthScaleChanged(val value: String) : MergeGifEvent
    data class HeightScaleChanged(val value: String) : MergeGifEvent
    data class Merge(val outputPath: String) : MergeGifEvent
    data object Cancel : MergeGifEvent
    data class LogReceived(val text: String) : MergeGifEvent
    data object Succeeded : MergeGifEvent
    data object Failed : MergeGifEvent
}

class MergeGifViewModel :
    OperationViewModel<MergeGifState, MergeGifEvent>(MergeGifState()) {
    private val queries = FFmpegQueryExtension()

    override fun onEvent(event: MergeGifEvent) {
        when (event) {
            is MergeGifEvent.GifsPicked -> {
                if (event.paths.isEmpty()) {
                    setState { copy(error = "Please select GIF file") }
                    return
                }
                if (event.paths.size < 2) {
                    setState { copy(error = "Please select min 2 Gif file") }
                    return
                }
                if (event.paths.any { File(it).extension != "gif" }) {
                    setState { copy(error = "There is other file selected, Please select  Gif file") }
                    return
                }
                val width = SizeOfImage(event.paths[0]).width()
                val height = SizeOfImage(event.paths[0]).height()
                if (width <= 0 || height <= 0) {
                    setState { copy(error = "Please select GIF file") }
                    return
                }
                setState {
                    copy(
                        gifPaths = event.paths,
                        gifWidth = width,
                        gifHeight = height,
                        error = null
                    )
                }
            }
            is MergeGifEvent.XPosChanged -> setState { copy(xPos = event.value) }
            is MergeGifEvent.YPosChanged -> setState { copy(yPos = event.value) }
            is MergeGifEvent.WidthScaleChanged -> setState { copy(widthScale = event.value) }
            is MergeGifEvent.HeightScaleChanged -> setState { copy(heightScale = event.value) }
            is MergeGifEvent.Merge -> {
                val current = state.value
                if (current.gifPaths.isEmpty()) {
                    setState { copy(error = "Please select Input GIFs") }
                    return
                }
                val x = current.xPos.toFloatOrNull()
                if (current.xPos.isEmpty()) {
                    setState { copy(error = "Please add X position") }
                    return
                }
                if (x == null || x <= 0f || x > 100f) {
                    setState { copy(error = "Please add X in percentage(%)") }
                    return
                }
                val y = current.yPos.toFloatOrNull()
                if (current.yPos.isEmpty()) {
                    setState { copy(error = "Please add Y position") }
                    return
                }
                if (y == null || y <= 0f || y > 100f) {
                    setState { copy(error = "Please add Y in percentage(%)") }
                    return
                }
                val w = current.widthScale.toFloatOrNull()
                if (current.widthScale.isEmpty()) {
                    setState { copy(error = "Please add width position") }
                    return
                }
                if (w == null || w <= 0f || w > 100f) {
                    setState { copy(error = "Please add width in percentage(%)") }
                    return
                }
                val h = current.heightScale.toFloatOrNull()
                if (current.heightScale.isEmpty()) {
                    setState { copy(error = "Please add height position") }
                    return
                }
                if (h == null || h <= 0f || h > 100f) {
                    setState { copy(error = "Please add height in percentage(%)") }
                    return
                }
                val gifWidth = current.gifWidth ?: return
                val gifHeight = current.gifHeight ?: return
                val posX = x * gifWidth / 100f
                val posY = y * gifHeight / 100f
                val widthScale = w * gifWidth / 100f
                val heightScale = h * gifHeight / 100f
                val pathsList = ArrayList<Paths>().apply {
                    current.gifPaths.forEach { path ->
                        add(Paths().apply { filePath = path; isImageFile = true })
                    }
                }
                setState { copy(isRunning = true, log = "", outputPath = event.outputPath, error = null) }
                val query = queries.mergeGIF(pathsList, posX, posY, widthScale, heightScale, event.outputPath)
                executeQuery(
                    query,
                    onLog = { onEvent(MergeGifEvent.LogReceived(it.text)) },
                    onSuccess = { onEvent(MergeGifEvent.Succeeded) },
                    onCancel = { setState { copy(isRunning = false) } },
                    onFailed = { onEvent(MergeGifEvent.Failed) }
                )
            }
            MergeGifEvent.Cancel -> {
                cancelRunning()
                setState { copy(isRunning = false) }
            }
            is MergeGifEvent.LogReceived -> setState { copy(log = event.text) }
            MergeGifEvent.Succeeded -> setState {
                copy(isRunning = false, previewPath = outputPath)
            }
            MergeGifEvent.Failed -> setState { copy(isRunning = false, error = "Failed") }
        }
    }
}

@Composable
fun MergeGifScreen(
    onBack: () -> Unit,
    viewModel: MergeGifViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberFilePicker { paths ->
        viewModel.onEvent(MergeGifEvent.GifsPicked(paths))
    }
    // Coil 3 loader with the GIF decoder, for the merged-GIF preview.
    val gifImageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
    }
    OperationScaffold(title = stringResource(R.string.merge_gif), onBack = onBack) {
        PickFileRow(
            label = stringResource(R.string.input_path),
            path = if (state.gifPaths.isNotEmpty()) "${state.gifPaths.size} GIF selected" else null,
            buttonText = stringResource(R.string.select_gif_file)
        ) { picker.pickImages() }
        Text(stringResource(R.string.please_add_x_and_y_position_of_second_gif_in_percentage))
        TextField(
            label = stringResource(R.string.x),
            value = state.xPos,
            onValueChange = { viewModel.onEvent(MergeGifEvent.XPosChanged(it)) }
        )
        TextField(
            label = stringResource(R.string.y),
            value = state.yPos,
            onValueChange = { viewModel.onEvent(MergeGifEvent.YPosChanged(it)) }
        )
        Text(stringResource(R.string.please_add_scale_second_gif_in_percentage))
        TextField(
            label = stringResource(R.string.width_scale),
            value = state.widthScale,
            onValueChange = { viewModel.onEvent(MergeGifEvent.WidthScaleChanged(it)) }
        )
        TextField(
            label = stringResource(R.string.height_scale),
            value = state.heightScale,
            onValueChange = { viewModel.onEvent(MergeGifEvent.HeightScaleChanged(it)) }
        )
        RunControls(
            running = state.isRunning,
            canRun = state.gifPaths.isNotEmpty(),
            runLabel = stringResource(R.string.merge_gif),
            onRun = {
                viewModel.onEvent(
                    MergeGifEvent.Merge(Common.getFilePath(context, Common.GIF))
                )
            },
            onCancel = { viewModel.onEvent(MergeGifEvent.Cancel) }
        )
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        state.previewPath?.let { preview ->
            AsyncImage(
                model = File(preview),
                contentDescription = null,
                imageLoader = gifImageLoader,
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.error?.let { ErrorText(it) }
    }
}
