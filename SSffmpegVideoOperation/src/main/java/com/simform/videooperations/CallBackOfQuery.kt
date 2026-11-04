package com.simform.videooperations

import android.os.Handler
import android.os.Looper
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.util.concurrent.CyclicBarrier

/**
 * Runs an FFmpeg command on a background thread and reports progress,
 * statistics and the result on the main thread through [FFmpegCallBack].
 *
 * Engine: ffmpeg-kit 6.0 (maintained successor of mobile-ffmpeg 4.4).
 */
class CallBackOfQuery {
    fun callQuery(query: Array<String>, fFmpegCallBack: FFmpegCallBack) {
        val gate = CyclicBarrier(2)
        object : Thread() {
            override fun run() {
                gate.await()
                process(query, fFmpegCallBack)
            }
        }.start()
        gate.await()
    }

    fun cancelProcess(executionId: Long) {
        if (executionId != 0L) {
            FFmpegKit.cancel(executionId)
        } else {
            FFmpegKit.cancel()
        }
    }

    fun cancelProcess() {
        FFmpegKit.cancel()
    }

    private fun process(query: Array<String>, ffmpegCallBack: FFmpegCallBack) {
        val processHandler = Handler(Looper.getMainLooper())
        FFmpegKitConfig.enableLogCallback { logMessage ->
            val logs = LogMessage(logMessage.sessionId, logMessage.level, logMessage.message)
            processHandler.post {
                ffmpegCallBack.process(logs)
            }
        }
        FFmpegKitConfig.enableStatisticsCallback { statistics ->
            val statisticsLog =
                Statistics(
                    statistics.sessionId,
                    statistics.videoFrameNumber,
                    statistics.videoFps,
                    statistics.videoQuality,
                    statistics.size,
                    statistics.time,
                    statistics.bitrate,
                    statistics.speed
                )
            processHandler.post {
                ffmpegCallBack.statisticsProcess(statisticsLog)
            }
        }
        val session = FFmpegKit.executeWithArguments(query)
        when {
            ReturnCode.isSuccess(session.returnCode) -> {
                processHandler.post {
                    ffmpegCallBack.success()
                }
            }
            ReturnCode.isCancel(session.returnCode) -> {
                processHandler.post {
                    ffmpegCallBack.cancel()
                    FFmpegKit.cancel()
                }
            }
            else -> {
                processHandler.post {
                    ffmpegCallBack.failed()
                }
            }
        }
    }
}
