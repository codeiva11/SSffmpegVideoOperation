package com.simform.videoimageeditor.ui.service

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the YouTube download thread.
 *
 * The download used to run on a raw Thread started from
 * YoutubeDownloadViewModel — a thread that lives only as long as the
 * app process — and the foreground service around it was stopped and
 * restarted between the download, merge and convert steps. Minimise
 * the app in one of those gaps (or while a step re-started the
 * service from the background, which Android 12+ refuses) and the
 * process could be killed, so the download died — and because the
 * work file was named with a timestamp, re-tapping Download made a
 * NEW file and started from zero instead of resuming.
 *
 * The runner fixes the ownership half: the thread is created here,
 * in the service layer, and the ViewModel only observes it through
 * the Listener. The foreground session itself is held by
 * EncodeService for the whole chain (see its session counting), so
 * the process stays foreground from the Download tap until the
 * final success / failure / cancel.
 *
 * `chainActive` tracks the merge/convert half of the chain, which
 * still runs through OperationViewModel.executeQuery: it is set when
 * the download hands over to merge/convert and cleared on the final
 * success / failure / cancel. It is in-memory only, so it is false
 * after a process death — which is exactly how a re-opened screen
 * can tell a dead chain (resume the partial file) from a live one
 * (wait for it to finish).
 */
object YoutubeDownloadRunner {

    interface Listener {
        fun onProgress(percent: Int, downloaded: Long, total: Long)
        fun onLog(text: String)
        fun onFinished(videoPath: String, audioPath: String?)
        fun onFailed(message: String)
        fun onCancelled()
    }

    data class Params(
        val videoUrl: String,
        val audioUrl: String?,
        val videoFile: File,
        val audioFile: File?
    )

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * True while the merge/convert half of a YouTube job is running
     * (set by the ViewModel when the download hands over, cleared on
     * the final outcome). In-memory only — false after process death.
     */
    @Volatile
    var chainActive: Boolean = false

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var jobThread: Thread? = null

    @Volatile
    private var listener: Listener? = null

    // java.net.HttpURLConnection has no named constant for 416
    // ("Range Not Satisfiable").
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    @Volatile
    private var lastProgressPercent = -1

    // Last known progress, so a screen that re-opens mid-download can
    // show where the download is without waiting for the next percent.
    @Volatile
    var currentPercent: Int = 0
        private set

    @Volatile
    var currentDownloaded: Long = 0L
        private set

    @Volatile
    var currentTotal: Long = 0L
        private set

    @Volatile
    var currentLog: String = ""
        private set

    /**
     * Starts the download job on a thread owned by this runner.
     * Returns false when a job is already running.
     */
    @Synchronized
    fun start(params: Params, listener: Listener): Boolean {
        if (jobThread?.isAlive == true) return false
        this.listener = listener
        cancelled = false
        isRunning = true
        lastProgressPercent = -1
        currentPercent = 0
        currentDownloaded = 0L
        currentTotal = 0L
        currentLog = "Downloading video…"
        val thread = Thread {
            try {
                listener.onLog("Downloading video…")
                if (!downloadFile(params.videoUrl, params.videoFile)) {
                    if (cancelled) {
                        listener.onCancelled()
                    } else {
                        listener.onFailed(
                            "Download beech me ruk gaya — dobara try karo, " +
                                "download wahin se resume hoga"
                        )
                    }
                    return@Thread
                }
                if (cancelled) {
                    listener.onCancelled()
                    return@Thread
                }
                var audioFile: File? = null
                if (params.audioUrl != null) {
                    audioFile = params.audioFile
                    if (audioFile == null) {
                        listener.onFailed("Audio file nahi bana")
                        return@Thread
                    }
                    listener.onLog("Downloading audio…")
                    currentLog = "Downloading audio…"
                    if (!downloadFile(params.audioUrl, audioFile)) {
                        if (cancelled) {
                            listener.onCancelled()
                        } else {
                            listener.onFailed(
                                "Audio download beech me ruk gaya — dobara try karo, " +
                                    "download wahin se resume hoga"
                            )
                        }
                        return@Thread
                    }
                    if (cancelled) {
                        listener.onCancelled()
                        return@Thread
                    }
                }
                listener.onFinished(params.videoFile.absolutePath, audioFile?.absolutePath)
            } finally {
                jobThread = null
                activeConnection = null
                isRunning = false
                this.listener = null
            }
        }
        jobThread = thread
        thread.start()
        return true
    }

    /**
     * Re-attaches a (new) screen to a download that is already
     * running — e.g. the app was minimised and the screen re-opened.
     * The current progress is replayed through the listener at once,
     * so the screen shows where the download is. No-op when nothing
     * is running.
     */
    @Synchronized
    fun attach(listener: Listener) {
        if (jobThread?.isAlive != true) return
        this.listener = listener
        listener.onProgress(currentPercent, currentDownloaded, currentTotal)
        listener.onLog(currentLog)
    }

    /**
     * Stops the running download, if any. The job thread reports the
     * cancel itself; this only signals it and drops the connection.
     */
    fun cancel() {
        if (jobThread?.isAlive != true) return
        cancelled = true
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }
    }

    /**
     * Resumable download: an interrupted file continues with a Range
     * header where it stopped; when the server ignores Range (plain
     * 200) the file restarts from zero. A 416 answer to a Range
     * request means the file is already complete, so it counts as
     * success — without this, resuming a fully downloaded file
     * would fail. Returns false on any failure or when cancelled.
     *
     * Note: java.net.HttpURLConnection has NO named constant for
     * 416, so the literal is used here.
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
            if (code == HTTP_RANGE_NOT_SATISFIABLE && existing > 0) {
                // The file is already fully downloaded.
                currentPercent = 100
                currentDownloaded = existing
                currentTotal = existing
                listener?.onProgress(100, existing, existing)
                return true
            }
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
                        // One listener update per percent point — a 64 KB
                        // chunk loop would otherwise post thousands of
                        // state updates.
                        if (percent != lastProgressPercent) {
                            lastProgressPercent = percent
                            currentPercent = percent
                            currentDownloaded = downloaded
                            currentTotal = total
                            listener?.onProgress(percent, downloaded, total)
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
}
