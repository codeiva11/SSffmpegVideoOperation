package com.simform.videoimageeditor.ui.common

import android.media.MediaMetadataRetriever

/** Video width/height, or null when they cannot be read. */
fun videoDimensions(path: String): Pair<Int, Int>? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.frameAtTime?.let { it.width to it.height }
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}

/** Media duration in milliseconds, or null when it cannot be read. */
fun mediaDurationMs(path: String): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}
