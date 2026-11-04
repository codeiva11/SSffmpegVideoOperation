package com.simform.videoimageeditor.ui.mvi

import com.simform.videooperations.CallBackOfQuery
import com.simform.videooperations.FFmpegCallBack
import com.simform.videooperations.LogMessage

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
        onFailed: () -> Unit
    ) {
        callBackOfQuery.callQuery(query, object : FFmpegCallBack {
            override fun process(logMessage: LogMessage) {
                onLog(logMessage)
            }

            override fun success() {
                onSuccess()
            }

            override fun cancel() {
                onCancel()
            }

            override fun failed() {
                onFailed()
            }
        })
    }

    fun cancelRunning() {
        callBackOfQuery.cancelProcess()
    }
}
