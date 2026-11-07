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

/**
 * Strict HH:MM:SS to seconds, or null when the text is not a valid time.
 * Shared by the time-range screens (Cut Audio From Video, Remove Audio
 * Range From Video) so the parser is not copied into every screen file.
 */
fun parseHhMmSsToSeconds(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 3) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    val seconds = parts[2].toIntOrNull() ?: return null
    if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null
    return hours * 3600 + minutes * 60 + seconds
}
