package com.simform.videoimageeditor.ui.ops.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.common.TextField
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.service.EncodeService
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * YouTube video download + H.265 conversion, as one tap.
 *
 * The video link is resolved with the NewPipe Extractor library
 * (com.github.TeamNewPipe:NewPipeExtractor:v0.27.1, GPLv3 — the app is
 * already GPL through ffmpeg-kit-full-gpl). No login, no YouTube API,
 * no Google Play Services: the extractor reads YouTube's own pages.
 *
 * Flow: paste link -> extractor lists the video's formats -> pick a
 * resolution -> download -> convert. 720p and below come down as one
 * file with audio; 1080p and above are video-only DASH, so the best
 * audio stream downloads too and ffmpeg merges the two first
 * (video copied, audio to AAC 192k), then the H.265 convert runs
 * (libx265, audio copied — it is already AAC by then).
 *
 * Download and convert run under EncodeService (foreground + wake
 * lock), so they survive the screen turning off. Download resumes
 * with a Range header when it is interrupted. "Together" here means
 * one automatic flow, not a live pipe: piping a live download into
 * the encoder would lose resume and fail the whole job on any stall.
 * H.265 on the phone is slow software encoding — a long video takes
 * a while. Once the H.265 convert succeeds, the original download
 * (the downloaded video file, plus the merged file and audio file
 * when they exist) is deleted automatically, right away — only the
 * H.265 file stays. The original is never deleted on failure or
 * cancel.
 */
data class YoutubeQualityOption(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?,
    val videoSuffix: String,
    val bitrate: Int
)

enum class YoutubePhase {
    IDLE, FETCHING, DOWNLOADING, MUXING, CONVERTING, DONE
}

data class YoutubeDownloadState(
    val url: String = "",
    val title: String? = null,
    val duration: String? = null,
    val options: List<YoutubeQualityOption> = emptyList(),
    val selected: YoutubeQualityOption? = null,
    val quality: H265Quality = H265Quality.FAST,
    val phase: YoutubePhase = YoutubePhase.IDLE,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val log: String = "",
    val outputPath: String? = null,
    val downloadedPath: String? = null,
    val workBase: String? = null,
    val deleteOriginal: Boolean = false,
    val originalDeleted: Boolean = false,
    val isRunning: Boolean = false,
    val error: String? = null
)

sealed interface YoutubeDownloadEvent {
    data class UrlChanged(val url: String) : YoutubeDownloadEvent
    data object FetchFormats : YoutubeDownloadEvent
    data class FormatsFetched(
        val title: String,
        val duration: String?,
        val options: List<YoutubeQualityOption>
    ) : YoutubeDownloadEvent

    data class QualitySelected(val option: YoutubeQualityOption) : YoutubeDownloadEvent
    data class H265QualitySelected(val quality: H265Quality) : YoutubeDownloadEvent
    data class Download(val outputPath: String) : YoutubeDownloadEvent
    data class DownloadProgress(
        val percent: Int,
        val downloaded: Long,
        val total: Long
    ) : YoutubeDownloadEvent

    data class LogReceived(val text: String) : YoutubeDownloadEvent
    data class DownloadFinished(val videoPath: String, val audioPath: String?) :
        YoutubeDownloadEvent

    data class ConvertReady(val inputPath: String) : YoutubeDownloadEvent
    data object Succeeded : YoutubeDownloadEvent
    data class Failed(val message: String) : YoutubeDownloadEvent
    data object Cancel : YoutubeDownloadEvent
}

/** Minimal NewPipe Downloader over plain HttpURLConnection — the app has no OkHttp. */
private class HttpUrlConnectionDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            request.headers().forEach { (name, values) ->
                setRequestProperty(name, values.joinToString(", "))
            }
            val data = request.dataToSend()
            if (data != null) {
                doOutput = true
                outputStream.use { it.write(data) }
            }
        }
        val code = connection.responseCode
        val body = try {
            connection.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            connection.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        return Response(
            code,
            connection.responseMessage,
            connection.headerFields.filterKeys { it != null },
            body,
            connection.url.toString()
        )
    }
}

class YoutubeDownloadViewModel :
    OperationViewModel<YoutubeDownloadState, YoutubeDownloadEvent>(YoutubeDownloadState()) {
    private val queries = FFmpegQueryExtension()

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var lastProgressPercent = -1

    override fun onEvent(event: YoutubeDownloadEvent) {
        when (event) {
            is YoutubeDownloadEvent.UrlChanged -> setState { copy(url = event.url, error = null) }
            YoutubeDownloadEvent.FetchFormats -> fetchFormats()
            is YoutubeDownloadEvent.FormatsFetched -> {
                val default = event.options.firstOrNull { it.audioUrl == null && it.height <= 720 }
                    ?: event.options.firstOrNull { it.audioUrl == null }
                    ?: event.options.firstOrNull()
                setState {
                    copy(
                        title = event.title,
                        duration = event.duration,
                        options = event.options,
                        selected = default,
                        phase = YoutubePhase.IDLE,
                        isRunning = false,
                        log = "",
                        error = null
                    )
                }
            }
            is YoutubeDownloadEvent.QualitySelected -> setState { copy(selected = event.option) }
            is YoutubeDownloadEvent.H265QualitySelected -> setState { copy(quality = event.quality) }
            is YoutubeDownloadEvent.Download -> startDownload(event.outputPath)
            is YoutubeDownloadEvent.DownloadProgress -> setState {
                copy(
                    downloadProgress = event.percent,
                    downloadedBytes = event.downloaded,
                    totalBytes = event.total
                )
            }
            is YoutubeDownloadEvent.LogReceived -> setState { copy(log = event.text) }
            is YoutubeDownloadEvent.DownloadFinished -> onDownloadFinished(event)
            is YoutubeDownloadEvent.ConvertReady -> startConvert(event.inputPath)
            YoutubeDownloadEvent.Succeeded -> {
                setState {
                    copy(isRunning = false, phase = YoutubePhase.DONE, log = "Done")
                }
                // Convert succeeded: delete the original download right
                // away. Only here, on success — never on failure or cancel.
                deleteOriginal(true)
            }
            is YoutubeDownloadEvent.Failed -> setState {
                copy(isRunning = false, error = event.message)
            }
            YoutubeDownloadEvent.Cancel -> cancelAll()
        }
    }

    private fun fetchFormats() {
        val url = state.value.url.trim()
        if (url.isEmpty()) {
            setState { copy(error = "YouTube link paste karo") }
            return
        }
        if (!url.contains("youtube.com/") && !url.contains("youtu.be/")) {
            setState {
                copy(error = "Ye YouTube video link nahi lagta — poora watch ya shorts link paste karo")
            }
            return
        }
        setState {
            copy(
                isRunning = true,
                error = null,
                title = null,
                duration = null,
                options = emptyList(),
                selected = null,
                outputPath = null,
                downloadedPath = null,
                originalDeleted = false,
                deleteOriginal = false,
                phase = YoutubePhase.FETCHING,
                log = "Getting video details…"
            )
        }
        Thread {
            try {
                ensureNewPipeInit()
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val options = buildOptions(info)
                if (options.isEmpty()) {
                    onEvent(
                        YoutubeDownloadEvent.Failed(
                            "Is video ke liye MP4 download nahi mila"
                        )
                    )
                    return@Thread
                }
                val duration = if (info.duration > 0) {
                    Common.stringForTime(info.duration * 1000)
                } else {
                    null
                }
                onEvent(
                    YoutubeDownloadEvent.FormatsFetched(
                        info.name ?: "YouTube video",
                        duration,
                        options
                    )
                )
            } catch (e: Exception) {
                onEvent(
                    YoutubeDownloadEvent.Failed(
                        "YouTube se details nahi mili — link private ya deleted ho sakta hai, " +
                            "ya YouTube ne kuch badla hai (extractor update chahiye ho sakta hai)."
                    )
                )
            }
        }.start()
    }

    private fun buildOptions(info: StreamInfo): List<YoutubeQualityOption> {
        val byHeight = LinkedHashMap<Int, YoutubeQualityOption>()
        for (stream in info.videoStreams) {
            if (stream.isVideoOnly) continue
            val height = stream.height
            if (height <= 0) continue
            if (stream.format?.suffix != "mp4") continue
            val url = stream.url
            if (url.isNullOrBlank()) continue
            val prev = byHeight[height]
            if (prev == null || stream.bitrate > prev.bitrate) {
                byHeight[height] = YoutubeQualityOption(
                    "${height}p", height, url, null, "mp4", stream.bitrate
                )
            }
        }
        val options = byHeight.values.toMutableList()
        val bestAudio = info.audioStreams
            .filter { !it.url.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
        if (bestAudio != null) {
            for (stream in info.videoOnlyStreams) {
                val height = stream.height
                if (height <= 0 || height in byHeight) continue
                if (stream.format?.suffix != "mp4") continue
                val url = stream.url
                if (url.isNullOrBlank()) continue
                options += YoutubeQualityOption(
                    "${height}p", height, url, bestAudio.url, "mp4", stream.bitrate
                )
            }
        }
        return options.sortedByDescending { it.height }
    }

    private fun startDownload(outputPath: String) {
        val option = state.value.selected ?: return
        val base = sanitizeTitle(state.value.title) + "_" + System.currentTimeMillis() / 1000
        cancelled = false
        setState {
            copy(
                isRunning = true,
                error = null,
                log = "",
                outputPath = outputPath,
                workBase = base,
                downloadProgress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                phase = YoutubePhase.DOWNLOADING
            )
        }
        EncodeService.start("Downloading YouTube video")
        Thread {
            val dir = File(outputPath).parentFile
            if (dir == null) {
                EncodeService.stop()
                onEvent(YoutubeDownloadEvent.Failed("Output folder nahi mila"))
                return@Thread
            }
            val videoFile = File(dir, base + "_original." + option.videoSuffix)
            onEvent(YoutubeDownloadEvent.LogReceived("Downloading video…"))
            if (!downloadFile(option.videoUrl, videoFile)) {
                EncodeService.stop()
                if (cancelled) return@Thread
                onEvent(
                    YoutubeDownloadEvent.Failed(
                        "Download beech me ruk gaya — dobara try karo, download wahin se resume hoga"
                    )
                )
                return@Thread
            }
            var audioFile: File? = null
            if (option.audioUrl != null) {
                audioFile = File(dir, base + "_audio.m4a")
                onEvent(YoutubeDownloadEvent.LogReceived("Downloading audio…"))
                if (!downloadFile(option.audioUrl, audioFile)) {
                    EncodeService.stop()
                    if (cancelled) return@Thread
                    onEvent(
                        YoutubeDownloadEvent.Failed(
                            "Audio download beech me ruk gaya — dobara try karo, download wahin se resume hoga"
                        )
                    )
                    return@Thread
                }
            }
            EncodeService.stop()
            if (cancelled) return@Thread
            onEvent(
                YoutubeDownloadEvent.DownloadFinished(
                    videoFile.absolutePath,
                    audioFile?.absolutePath
                )
            )
        }.start()
    }

    private fun onDownloadFinished(event: YoutubeDownloadEvent.DownloadFinished) {
        setState { copy(downloadedPath = event.videoPath) }
        val audioPath = event.audioPath
        if (audioPath != null) {
            val output = state.value.outputPath ?: return
            val muxedPath = File(
                File(output).parent,
                "${state.value.workBase}_merged.mp4"
            ).absolutePath
            setState { copy(phase = YoutubePhase.MUXING, log = "Merging video and audio…") }
            executeQuery(
                queries.muxVideoAudio(event.videoPath, audioPath, muxedPath),
                onLog = { onEvent(YoutubeDownloadEvent.LogReceived(it.text)) },
                onSuccess = { onEvent(YoutubeDownloadEvent.ConvertReady(muxedPath)) },
                onCancel = { setState { copy(isRunning = false, phase = YoutubePhase.IDLE) } },
                onFailed = {
                    onEvent(YoutubeDownloadEvent.Failed("Video aur audio merge nahi hua"))
                },
                label = "Merging video and audio"
            )
        } else {
            onEvent(YoutubeDownloadEvent.ConvertReady(event.videoPath))
        }
    }

    private fun startConvert(inputPath: String) {
        val output = state.value.outputPath ?: return
        val quality = state.value.quality
        setState {
            copy(
                phase = YoutubePhase.CONVERTING,
                log = "Converting to H.265 (CRF ${quality.crf})…"
            )
        }
        executeQuery(
            queries.convertToH265(inputPath, output, quality.crf, quality.preset),
            onLog = { onEvent(YoutubeDownloadEvent.LogReceived(it.text)) },
            onSuccess = { onEvent(YoutubeDownloadEvent.Succeeded) },
            onCancel = { setState { copy(isRunning = false, phase = YoutubePhase.IDLE) } },
            onFailed = { onEvent(YoutubeDownloadEvent.Failed("H.265 convert fail hua")) },
            label = "Converting to H.265"
        )
    }

    private fun cancelAll() {
        cancelled = true
        activeConnection?.disconnect()
        cancelRunning()
        EncodeService.stop()
        setState { copy(isRunning = false, phase = YoutubePhase.IDLE) }
    }

    private fun deleteOriginal(delete: Boolean) {
        if (!delete) {
            setState { copy(deleteOriginal = false) }
            return
        }
        setState { copy(deleteOriginal = true) }
        Thread {
            state.value.downloadedPath?.let { File(it).delete() }
            val output = state.value.outputPath
            val base = state.value.workBase
            if (output != null && base != null) {
                File(File(output).parent, base + "_merged.mp4").delete()
                File(File(output).parent, base + "_audio.m4a").delete()
            }
            setState { copy(originalDeleted = true, downloadedPath = null) }
        }.start()
    }

    /**
     * Resumable download: an interrupted file continues with a Range
     * header where it stopped; when the server ignores Range (plain
     * 200) the file restarts from zero. Returns false on any failure
     * or when the run was cancelled.
     */
    private fun downloadFile(url: String, dest: File): Boolean {
        lastProgressPercent = -1
        var connection: HttpURLConnection? = null
        try {
            val existing = if (dest.exists()) dest.length() else 0L
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            activeConnection = connection
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL
            ) {
                return false
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (!append && dest.exists()) dest.delete()
            val total = if (append) {
                connection.contentLengthLong + existing
            } else {
                connection.contentLengthLong
            }
            var downloaded = if (append) existing else 0L
            connection.inputStream.use { input ->
                FileOutputStream(dest, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0
                    while (!cancelled && input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) {
                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        // One state update per percent point — a 64 KB chunk
                        // loop would otherwise recompose thousands of times.
                        if (percent != lastProgressPercent) {
                            lastProgressPercent = percent
                            onEvent(
                                YoutubeDownloadEvent.DownloadProgress(percent, downloaded, total)
                            )
                        }
                    }
                }
            }
            return !cancelled
        } catch (_: Exception) {
            return false
        } finally {
            if (activeConnection === connection) activeConnection = null
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun sanitizeTitle(title: String?): String {
        val clean = (title ?: "youtube-video")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(80)
        return clean.ifEmpty { "youtube-video" }
    }

    companion object {
        @Volatile
        private var newPipeInit = false

        private fun ensureNewPipeInit() {
            if (newPipeInit) return
            synchronized(YoutubeDownloadViewModel::class.java) {
                if (!newPipeInit) {
                    NewPipe.init(HttpUrlConnectionDownloader())
                    newPipeInit = true
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    return "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
fun YoutubeDownloadScreen(
    onBack: () -> Unit,
    viewModel: YoutubeDownloadViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    OperationScaffold(title = stringResource(R.string.youtube_video_download), onBack = onBack) {
        Text(
            "Paste a YouTube video link — the video downloads, then " +
                "converts to H.265 (HEVC) automatically. No YouTube " +
                "account needed. 720p and below download as one file; " +
                "1080p and above download video and audio separately " +
                "and merge first. H.265 encoding on the phone is slow — " +
                "a long video takes a while, so keep the app running. " +
                "After a successful convert the original download is " +
                "deleted automatically — only the H.265 file stays.",
            style = MaterialTheme.typography.bodySmall
        )
        TextField(
            label = "YouTube link",
            value = state.url,
            onValueChange = { viewModel.onEvent(YoutubeDownloadEvent.UrlChanged(it)) }
        )
        Button(
            onClick = { viewModel.onEvent(YoutubeDownloadEvent.FetchFormats) },
            enabled = state.url.isNotBlank() && !state.isRunning,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Get video details") }
        state.title?.let {
            Text("Title: $it", style = MaterialTheme.typography.bodyMedium)
        }
        state.duration?.let {
            Text("Duration: $it", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.options.isNotEmpty()) {
            Text("Quality (video):", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onEvent(YoutubeDownloadEvent.QualitySelected(option))
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = state.selected == option,
                            onClick = {
                                viewModel.onEvent(YoutubeDownloadEvent.QualitySelected(option))
                            }
                        )
                        Text(
                            option.label + if (option.audioUrl != null) {
                                " (video + audio, merged first)"
                            } else {
                                ""
                            }
                        )
                    }
                }
            }
            Text("H.265 quality:", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Downloader order is Fast first: CRF 28, then 26, then 24.
                // (The H.265 compress screen keeps its own Best-first order.)
                listOf(H265Quality.FAST, H265Quality.BALANCED, H265Quality.BEST)
                    .forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onEvent(
                                    YoutubeDownloadEvent.H265QualitySelected(quality)
                                )
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = state.quality == quality,
                            onClick = {
                                viewModel.onEvent(
                                    YoutubeDownloadEvent.H265QualitySelected(quality)
                                )
                            }
                        )
                        Text(quality.label)
                    }
                }
            }
        }
        RunControls(
            running = state.isRunning,
            canRun = state.selected != null,
            runLabel = "Download + Convert to H.265",
            onRun = {
                viewModel.onEvent(
                    YoutubeDownloadEvent.Download(
                        Common.getFilePath(context, Common.VIDEO)
                    )
                )
            },
            onCancel = { viewModel.onEvent(YoutubeDownloadEvent.Cancel) }
        )
        if (state.phase != YoutubePhase.IDLE) {
            val phaseText = when (state.phase) {
                YoutubePhase.FETCHING -> "Getting video details…"
                YoutubePhase.DOWNLOADING -> {
                    val total = if (state.totalBytes > 0) {
                        formatBytes(state.totalBytes)
                    } else {
                        "?"
                    }
                    "Downloading… ${state.downloadProgress}% " +
                        "(${formatBytes(state.downloadedBytes)} / $total)"
                }
                YoutubePhase.MUXING -> "Merging video and audio…"
                YoutubePhase.CONVERTING -> "Converting to H.265 (CRF ${state.quality.crf})…"
                YoutubePhase.DONE -> "Done"
                YoutubePhase.IDLE -> ""
            }
            Text(phaseText, style = MaterialTheme.typography.bodyMedium)
        }
        RunStatus(log = state.log, output = state.outputPath, running = state.isRunning)
        if (state.originalDeleted) {
            Text("Original download deleted.")
        }
        state.error?.let { ErrorText(it) }
    }
}
