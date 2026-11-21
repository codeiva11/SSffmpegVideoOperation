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
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.ErrorText
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.common.RunControls
import com.simform.videoimageeditor.ui.common.RunStatus
import com.simform.videoimageeditor.ui.common.TextField
import com.simform.videoimageeditor.ui.mvi.OperationViewModel
import com.simform.videoimageeditor.ui.service.EncodeService
import com.simform.videoimageeditor.ui.service.YoutubeDownloadRunner
import com.simform.videooperations.Common
import com.simform.videooperations.FFmpegQueryExtension
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioTrackType
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
 * file with audio; 1080p and above are video-only DASH, so the
 * ORIGINAL-language audio stream downloads too (highest-bitrate
 * ORIGINAL track; a dubbed track never wins on bitrate alone) and
 * ffmpeg merges the two first (video copied, audio to AAC 192k),
 * then the H.265 convert runs (libx265, audio copied — it is
 * already AAC by then). 720p and below carry whatever audio
 * YouTube puts in the combined file — no language choice there.
 *
 * Download and convert run under EncodeService (foreground + wake
 * lock), so they survive the screen turning off AND the app being
 * minimised: one foreground session is held from the Download tap
 * until the final success / failure / cancel, with no stop/start
 * gaps between download, merge and convert (a step that re-started
 * the service from the background could be refused by Android 12+,
 * and the process could be killed in the gap). The download thread
 * itself is owned by YoutubeDownloadRunner in the service layer,
 * not by this ViewModel, so it keeps running when the screen is
 * gone. Download resumes with a Range header when it is
 * interrupted, and the work file is named from the video (title +
 * video id + height), never a timestamp — so re-tapping Download
 * for the same video + quality continues the same partial file
 * instead of starting over. YouTube's stream links are
 * time-limited, so when a download fails the runner fetches a
 * fresh link once per file and resumes the same partial file with
 * it; a failure after that is reported plainly, without promising
 * a resume. The in-progress job (video, height,
 * quality) is also saved, so re-opening this screen after the
 * process died offers the same resume. "Together" here means one
 * automatic flow, not a live pipe: piping a live download into the
 * encoder would lose resume and fail the whole job on any stall.
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
    val bitrate: Int,
    val audioLabel: String? = null
)

enum class YoutubePhase {
    IDLE, FETCHING, DOWNLOADING, MUXING, CONVERTING, DONE
}

data class YoutubeDownloadState(
    val url: String = "",
    val title: String? = null,
    val videoId: String? = null,
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
    val resumeAvailable: Boolean = false,
    val error: String? = null
)

sealed interface YoutubeDownloadEvent {
    data class UrlChanged(val url: String) : YoutubeDownloadEvent
    data object FetchFormats : YoutubeDownloadEvent
    data class FormatsFetched(
        val title: String,
        val duration: String?,
        val options: List<YoutubeQualityOption>,
        val videoId: String?
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

    // Resume for a download that was interrupted by the process dying
    // (or by the screen being left and re-opened): the video + height
    // the last Download tap was working on. Consumed when formats are
    // fetched, so the same height is pre-selected.
    private var pendingResumeHeight: Int? = null
    private var pendingResumeCrf: H265Quality? = null

    init {
        restorePendingJob()
    }

    override fun onEvent(event: YoutubeDownloadEvent) {
        when (event) {
            is YoutubeDownloadEvent.UrlChanged -> {
                pendingResumeHeight = null
                pendingResumeCrf = null
                setState { copy(url = event.url, error = null, resumeAvailable = false) }
            }
            YoutubeDownloadEvent.FetchFormats -> fetchFormats()
            is YoutubeDownloadEvent.FormatsFetched -> {
                val resumeHeight = pendingResumeHeight
                val resumeCrf = pendingResumeCrf
                pendingResumeHeight = null
                pendingResumeCrf = null
                val default = if (resumeHeight != null) {
                    event.options.firstOrNull { it.height == resumeHeight }
                } else {
                    null
                }
                    ?: event.options.firstOrNull { it.audioUrl == null && it.height <= 720 }
                    ?: event.options.firstOrNull { it.audioUrl == null }
                    ?: event.options.firstOrNull()
                val resumed = resumeHeight != null && default?.height == resumeHeight
                setState {
                    copy(
                        title = event.title,
                        videoId = event.videoId,
                        duration = event.duration,
                        options = event.options,
                        selected = default,
                        quality = resumeCrf ?: quality,
                        phase = YoutubePhase.IDLE,
                        isRunning = false,
                        resumeAvailable = resumed,
                        log = if (resumed) {
                            "Pichhla download mila — ${default?.height}p resume hoga, " +
                                "adhoora file wahin se aage badhega"
                        } else {
                            ""
                        },
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
                // Final outcome: end the foreground session held since
                // the Download tap, and forget the saved in-progress job.
                YoutubeDownloadRunner.chainActive = false
                EncodeService.stop()
                clearJob()
                setState {
                    copy(isRunning = false, phase = YoutubePhase.DONE, log = "Done")
                }
                // Convert succeeded: delete the original download right
                // away. Only here, on success — never on failure or cancel.
                deleteOriginal(true)
            }
            is YoutubeDownloadEvent.Failed -> {
                // Final outcome: end the foreground session (a no-op
                // when the failure came from fetching formats, where no
                // session was started) and forget the saved job.
                YoutubeDownloadRunner.chainActive = false
                EncodeService.stop()
                clearJob()
                setState {
                    copy(isRunning = false, error = event.message)
                }
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
                        options,
                        info.id
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
        // Separate audio (1080p+): prefer the ORIGINAL-language track —
        // a dubbed track can otherwise win on bitrate alone. Fall back
        // to the highest-bitrate track when no ORIGINAL track exists.
        // (StreamInfo.getLanguageInfo() is null for YouTube, so the
        // track type itself is the reference.)
        val audioCandidates = info.audioStreams
            .filter { !it.url.isNullOrBlank() }
        val bestAudio = audioCandidates
            .filter { it.audioTrackType == AudioTrackType.ORIGINAL }
            .maxByOrNull { it.averageBitrate }
            ?: audioCandidates.maxByOrNull { it.averageBitrate }
        val audioLabel = bestAudio?.audioTrackName?.takeIf { it.isNotBlank() }
            ?: bestAudio?.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
        if (bestAudio != null) {
            for (stream in info.videoOnlyStreams) {
                val height = stream.height
                if (height <= 0 || height in byHeight) continue
                if (stream.format?.suffix != "mp4") continue
                val url = stream.url
                if (url.isNullOrBlank()) continue
                options += YoutubeQualityOption(
                    "${height}p", height, url, bestAudio.url, "mp4", stream.bitrate,
                    audioLabel = audioLabel
                )
            }
        }
        return options.sortedByDescending { it.height }
    }

    private fun startDownload(outputPath: String) {
        val option = state.value.selected ?: return
        // A download is already running (this screen was re-opened
        // mid-download): re-attach to it instead of starting a second.
        if (YoutubeDownloadRunner.isRunning) {
            attachToRunner()
            return
        }
        // A merge/convert from a previous screen is still running in
        // this process: starting another job would fight over the
        // same files, so refuse until it finishes.
        if (YoutubeDownloadRunner.chainActive) {
            setState {
                copy(
                    error = "Pehla video abhi merge/convert ho raha hai — " +
                        "uske khatm hone ka wait karo"
                )
            }
            return
        }
        // Stable work file: title + video id + height, never a
        // timestamp — so re-tapping Download for the same video +
        // quality continues the SAME partial file (Range resume)
        // instead of starting a new file from zero.
        val videoId = state.value.videoId?.takeIf { it.isNotBlank() }
            ?: "u" + abs(state.value.url.hashCode()).toString()
        val base = sanitizeTitle(state.value.title) + "_" + videoId +
            "_" + option.height + "p"
        setState {
            copy(
                isRunning = true,
                error = null,
                log = "",
                outputPath = outputPath,
                workBase = base,
                videoId = videoId,
                downloadProgress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                phase = YoutubePhase.DOWNLOADING,
                resumeAvailable = false
            )
        }
        saveJob(PHASE_DOWNLOADING)
        // One foreground session for the whole chain — held from here
        // until the final success / failure / cancel, with no
        // stop/start gaps between download, merge and convert.
        EncodeService.start("Downloading YouTube video")
        val dir = File(outputPath).parentFile
        if (dir == null) {
            onEvent(YoutubeDownloadEvent.Failed("Output folder nahi mila"))
            return
        }
        val videoFile = File(dir, base + "_original." + option.videoSuffix)
        val audioFile =
            if (option.audioUrl != null) File(dir, base + "_audio.m4a") else null
        // Fresh links for the runner's one-time retry: re-fetch the
        // formats for the same video and pick the option for the
        // height this job is downloading. Captures the page URL and
        // height at tap time, so a quality change mid-download
        // cannot switch the retry to another height.
        val pageUrl = state.value.url.trim()
        val jobHeight = option.height
        val refreshUrls: () -> Pair<String, String?>? = {
            try {
                ensureNewPipeInit()
                val info = StreamInfo.getInfo(ServiceList.YouTube, pageUrl)
                val freshOptions = buildOptions(info)
                val fresh = freshOptions.firstOrNull { it.height == jobHeight }
                if (fresh == null) {
                    null
                } else {
                    // Keep the screen on the fresh links, so a later
                    // manual retry also uses them.
                    setState { copy(options = freshOptions, selected = fresh) }
                    fresh.videoUrl to fresh.audioUrl
                }
            } catch (_: Exception) {
                null
            }
        }
        if (!YoutubeDownloadRunner.start(
                YoutubeDownloadRunner.Params(
                    option.videoUrl,
                    option.audioUrl,
                    videoFile,
                    audioFile,
                    refreshUrls
                ),
                downloadListener()
            )
        ) {
            // Raced with another start: give the session back and
            // attach to the running job instead.
            EncodeService.stop()
            attachToRunner()
        }
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
            // From here the merge/convert half of the chain owns the job.
            YoutubeDownloadRunner.chainActive = true
            updatePhase(PHASE_MUXING)
            setState { copy(phase = YoutubePhase.MUXING, log = "Merging video and audio…") }
            executeQuery(
                queries.muxVideoAudio(event.videoPath, audioPath, muxedPath),
                onLog = { onEvent(YoutubeDownloadEvent.LogReceived(it.text)) },
                onSuccess = { onEvent(YoutubeDownloadEvent.ConvertReady(muxedPath)) },
                onCancel = { onEvent(YoutubeDownloadEvent.Cancel) },
                onFailed = {
                    onEvent(YoutubeDownloadEvent.Failed("Video aur audio merge nahi hua"))
                },
                label = "Merging video and audio"
            )
        } else {
            YoutubeDownloadRunner.chainActive = true
            onEvent(YoutubeDownloadEvent.ConvertReady(event.videoPath))
        }
    }

    private fun startConvert(inputPath: String) {
        val output = state.value.outputPath ?: return
        val quality = state.value.quality
        updatePhase(PHASE_CONVERTING)
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
            onCancel = { onEvent(YoutubeDownloadEvent.Cancel) },
            onFailed = { onEvent(YoutubeDownloadEvent.Failed("H.265 convert fail hua")) },
            label = "Converting to H.265"
        )
    }

    private fun cancelAll() {
        // Guard against the double call: the UI Cancel and the
        // ffmpeg-cancel callback both end up here.
        if (!state.value.isRunning && state.value.phase == YoutubePhase.IDLE) return
        YoutubeDownloadRunner.cancel()
        cancelRunning()
        // Ends the foreground session held since the Download tap
        // (a no-op when the cancel came from fetching formats, where
        // no session was started).
        YoutubeDownloadRunner.chainActive = false
        EncodeService.stop()
        clearJob()
        setState { copy(isRunning = false, phase = YoutubePhase.IDLE) }
    }

    private fun deleteOriginal(delete: Boolean) {
        if (!delete) {
            setState { copy(deleteOriginal = false) }
            return
        }
        setState { copy(deleteOriginal = true) }
        Thread {
            val deleted = mutableListOf<String>()
            val failed = mutableListOf<String>()
            fun tryDelete(file: File) {
                if (!file.exists()) return
                if (file.delete()) deleted.add(file.name) else failed.add(file.name)
            }
            // Exact deletes for this job's intermediates.
            state.value.downloadedPath?.let { tryDelete(File(it)) }
            val output = state.value.outputPath
            val base = state.value.workBase
            val outputFile = output?.let { File(it) }
            val dir = outputFile?.parentFile
            // Same-video prefix: workBase is
            // sanitizedTitle_videoId_<height>p — drop the trailing
            // height segment, so stale intermediates from other
            // heights/attempts of this video are swept too.
            val sweepPrefix = if (dir != null && base != null) {
                base.substringBeforeLast("_") + "_"
            } else {
                null
            }
            if (dir != null && base != null && sweepPrefix != null) {
                tryDelete(File(dir, base + "_merged.mp4"))
                tryDelete(File(dir, base + "_audio.m4a"))
                // Sweep: stale intermediates of the SAME video from other
                // heights/attempts (e.g. 1080p tried, then 720p succeeded).
                // The final output (Output<epoch>.mp4) never matches the
                // prefix, but skip it explicitly anyway.
                val outputAbs = outputFile?.absolutePath
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(sweepPrefix) &&
                        file.absolutePath != outputAbs
                    ) {
                        tryDelete(file)
                    }
                }
            }
            // Only claim deletion when nothing of this video remains.
            val remaining = if (dir != null && sweepPrefix != null) {
                val outputAbs = outputFile?.absolutePath
                dir.listFiles()?.any { file ->
                    file.isFile && file.name.startsWith(sweepPrefix) &&
                        file.absolutePath != outputAbs
                } ?: false
            } else {
                false
            }
            val summary = buildString {
                append("Intermediate files deleted: ${deleted.size}.")
                if (failed.isNotEmpty()) {
                    append(" Delete nahi hui: ${failed.joinToString(", ")}")
                }
            }
            onEvent(YoutubeDownloadEvent.LogReceived(summary))
            setState {
                copy(originalDeleted = failed.isEmpty() && !remaining, downloadedPath = null)
            }
        }.start()
    }

    /** Listener that funnels the runner's callbacks into state events. */
    private fun downloadListener() = object : YoutubeDownloadRunner.Listener {
        override fun onProgress(percent: Int, downloaded: Long, total: Long) {
            onEvent(YoutubeDownloadEvent.DownloadProgress(percent, downloaded, total))
        }

        override fun onLog(text: String) {
            onEvent(YoutubeDownloadEvent.LogReceived(text))
        }

        override fun onFinished(videoPath: String, audioPath: String?) {
            onEvent(YoutubeDownloadEvent.DownloadFinished(videoPath, audioPath))
        }

        override fun onFailed(message: String) {
            onEvent(YoutubeDownloadEvent.Failed(message))
        }

        override fun onCancelled() {
            onEvent(YoutubeDownloadEvent.Cancel)
        }
    }

    /**
     * Re-attaches this screen to a download that is already running
     * (same process, screen re-opened): the runner replays its
     * current progress through the fresh listener at once.
     */
    private fun attachToRunner() {
        setState {
            copy(
                isRunning = true,
                phase = YoutubePhase.DOWNLOADING,
                log = "Download background me chal raha hai…"
            )
        }
        YoutubeDownloadRunner.attach(downloadListener())
    }

    // ---- In-progress job, saved so a killed process can resume ----
    // SharedPreferences in the app-private store: just the video, the
    // chosen height/quality and the work file — enough to re-fetch
    // formats and continue the same partial file. No new permission.

    private fun prefs(): SharedPreferences? {
        val context = EncodeService.appContext ?: return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveJob(phase: String) {
        val state = state.value
        val option = state.selected ?: return
        prefs()?.edit()
            ?.putString(KEY_URL, state.url)
            ?.putString(KEY_VIDEO_ID, state.videoId)
            ?.putString(KEY_TITLE, state.title)
            ?.putInt(KEY_HEIGHT, option.height)
            ?.putString(KEY_CRF, state.quality.name)
            ?.putString(KEY_PHASE, phase)
            ?.putString(KEY_OUTPUT_PATH, state.outputPath)
            ?.putString(KEY_WORK_BASE, state.workBase)
            ?.apply()
    }

    private fun updatePhase(phase: String) {
        prefs()?.edit()?.putString(KEY_PHASE, phase)?.apply()
    }

    private fun clearJob() {
        prefs()?.edit()?.clear()?.apply()
    }

    /**
     * Runs once when this ViewModel is created (screen opened). When
     * a previous Download tap left a saved in-progress job:
     * - a download is still running in this process -> re-attach to
     *   it and show its progress;
     * - a merge/convert is still running in this process -> say so
     *   and don't start anything new;
     * - otherwise the process died mid-job -> re-fetch the formats
     *   and pre-select the same height, so the same Download tap
     *   resumes the same partial file instead of starting over.
     */
    private fun restorePendingJob() {
        val p = prefs() ?: return
        val url = p.getString(KEY_URL, null) ?: return
        val videoId = p.getString(KEY_VIDEO_ID, null)
        val title = p.getString(KEY_TITLE, null)
        val height = p.getInt(KEY_HEIGHT, -1).takeIf { it > 0 }
        val crf = p.getString(KEY_CRF, null)
            ?.let { runCatching { H265Quality.valueOf(it) }.getOrNull() }
        val outputPath = p.getString(KEY_OUTPUT_PATH, null)
        val workBase = p.getString(KEY_WORK_BASE, null)
        pendingResumeHeight = height
        pendingResumeCrf = crf
        if (YoutubeDownloadRunner.isRunning) {
            setState {
                copy(
                    url = url,
                    title = title,
                    videoId = videoId,
                    outputPath = outputPath,
                    workBase = workBase,
                    quality = crf ?: quality,
                    isRunning = true,
                    phase = YoutubePhase.DOWNLOADING,
                    log = "Download background me chal raha hai…"
                )
            }
            attachToRunner()
            return
        }
        if (YoutubeDownloadRunner.chainActive) {
            setState {
                copy(
                    url = url,
                    title = title,
                    videoId = videoId,
                    log = "Pehla video abhi merge/convert ho raha hai — " +
                        "uske khatm hone ka wait karo"
                )
            }
            return
        }
        setState {
            copy(
                url = url,
                title = title,
                videoId = videoId,
                outputPath = outputPath,
                workBase = workBase,
                quality = crf ?: quality,
                log = "Pichhla download mila — details nikaal rahe hain, resume hoga…"
            )
        }
        fetchFormats()
    }

    companion object {
        private const val PREFS_NAME = "youtube_download_job"
        private const val KEY_URL = "url"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_TITLE = "title"
        private const val KEY_HEIGHT = "height"
        private const val KEY_CRF = "crf"
        private const val KEY_PHASE = "phase"
        private const val KEY_OUTPUT_PATH = "output_path"
        private const val KEY_WORK_BASE = "work_base"
        private const val PHASE_DOWNLOADING = "downloading"
        private const val PHASE_MUXING = "muxing"
        private const val PHASE_CONVERTING = "converting"

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

private fun sanitizeTitle(title: String?): String {
    val clean = (title ?: "youtube-video")
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim()
        .take(80)
    return clean.ifEmpty { "youtube-video" }
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
                "a long video takes a while. Download and convert keep " +
                "running in the background when you minimise the app, " +
                "and an interrupted download resumes from where it " +
                "stopped instead of starting over. After a successful " +
                "convert the original download is deleted automatically " +
                "— only the H.265 file stays.",
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
                                if (option.audioLabel != null) {
                                    " (audio: ${option.audioLabel}, merged first)"
                                } else {
                                    " (video + audio, merged first)"
                                }
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
            runLabel = if (state.resumeAvailable) {
                "Resume download + Convert to H.265"
            } else {
                "Download + Convert to H.265"
            },
            onRun = {
                viewModel.onEvent(
                    YoutubeDownloadEvent.Download(
                        Common.getFilePath(
                            context,
                            Common.VIDEO,
                            state.title?.let { sanitizeTitle(it) }
                        )
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
