package com.simform.videoimageeditor.ui.mvi

import com.simform.videooperations.CallBackOfQuery
import com.simform.videooperations.FFmpegCallBack
import com.simform.videooperations.LogMessage
import com.simform.videoimageeditor.ui.service.EncodeService

/**
 * Shared runner for the operation screens: builds the FFmpeg query (in the
 * concrete ViewModel), runs it on a background thread, and funnels the
 * callbacks into state updates. Every operation screen is one state +
 * one sealed event, per the MVI migration guide.
 */
abstract class OperationViewModel<S, E>(initialState: S) : MviViewModel<S, E>(initialState) {
    private val callBackOfQuery = CallBackOfQuery()

    protected fun executeQuery(
        query: Array<String>,
        onLog: (LogMessage) -> Unit,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
        onFailed: () -> Unit,
        label: String = "Video operation running"
    ) {
        // Foreground service: the encode keeps running when the app is
        // minimised, with a notification the user can tap or cancel.
        EncodeService.start(label)
        callBackOfQuery.callQuery(query, object : FFmpegCallBack {
            override fun process(logMessage: LogMessage) {
                onLog(logMessage)
            }

            override fun success() {
                EncodeService.stop()
                onSuccess()
            }

            override fun cancel() {
                EncodeService.stop()
                onCancel()
            }

            override fun failed() {
                EncodeService.stop()
                onFailed()
            }
        })
    }

    fun cancelRunning() {
        callBackOfQuery.cancelProcess()
    }
}
