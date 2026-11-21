package com.simform.videooperations

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.text.TextUtils
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import java.util.Formatter
import java.util.Locale

/**
 * Shared constants and file helpers for the video operations.
 * File picking lives in the app (Activity Result API); this object has no UI.
 */
object Common {
    const val TIME_FORMAT = "HH:mm:ss"
    const val OUT_PUT_DIR: String = "Output"
    private val format: DecimalFormat = DecimalFormat("#.##")
    private const val MB = (1024 * 1024).toLong()
    private const val KB: Long = 1024

    //Output Files
    const val IMAGE: String = "IMAGE"
    const val VIDEO: String = "VIDEO"
    const val GIF: String = "GIF"
    const val MP3: String = "MP3"

    // Standard Ratio
    const val RATIO_1: String = "16:9"
    const val RATIO_2: String = "4:3"
    const val RATIO_3: String = "16:10"
    const val RATIO_4: String = "5:4"
    const val RATIO_5: String = "2:21:1"
    const val RATIO_6: String = "2:35:1"
    const val RATIO_7: String = "2:39:1"

    // Standard  Bitrate
    const val BITRATE_96: String = "96" //kbps
    const val BITRATE_112: String = "112" //kbps
    const val BITRATE_128: String = "128" //kbps
    const val BITRATE_160: String = "160" //kbps
    const val BITRATE_192: String = "192" //kbps
    const val BITRATE_256: String = "256" //kbps
    const val BITRATE_320: String = "320" //kbps

    const val DURATION_LONGEST: String = "longest"
    const val DURATION_SHORTEST: String = "shortest"
    const val DURATION_FIRST: String = "first"

    fun stringForTime(timeMs: Long?): String {
        val mFormatBuilder = StringBuilder()
        val mFormatter = Formatter(mFormatBuilder, Locale.getDefault())
        val totalSeconds = timeMs?.div(1000)
        val seconds = (totalSeconds?.rem(60))?.toInt()
        val minutes = ((totalSeconds?.div(60))?.rem(60))?.toInt()
        val hours = (totalSeconds?.div(3600))?.toInt()
        mFormatBuilder.setLength(0)
        return mFormatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString()
    }

    /**
     * Reads the frame rate of the video track, or null when it cannot be read.
     * Callers set the result on their own FFmpegQueryExtension instance:
     * `ffmpegQueryExtension.FRAME_RATE = Common.getFrameRate(path) ?: 25`.
     * (Previously this wrote to a throwaway instance, so detection never took effect.)
     */
    fun getFrameRate(fileString: String): Int? {
        val extractor = MediaExtractor()
        val file = File(fileString)
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            extractor.setDataSource(fis.fd)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    return format.getInteger(MediaFormat.KEY_FRAME_RATE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
            try {
                fis?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    fun getFileSize(file: File): String {
        require(file.isFile) { "Expected a file" }
        val length = file.length().toDouble()
        if (length > MB) {
            return format.format(length / MB).toString() + " MB"
        }
        return if (length > KB) {
            format.format(length / KB).toString() + " KB"
        } else {
            format.format(length).toString() + " B"
        }
    }

    /**
     * Output files go to the public "Ffmpeg Output" folder at the root of the
     * phone storage (/storage/emulated/0/Ffmpeg Output/), so they are visible
     * in Files and survive an uninstall. That folder needs all-files access
     * (MANAGE_EXTERNAL_STORAGE on Android 11+); when it cannot be created the
     * app-private Output folder is used instead so operations still work.
     *
     * Video outputs are named after the original input file: pass the input
     * file's base name (no extension) as [inputName] and the output becomes
     * "<name>.mp4" instead of "Output<epoch>.mp4". When a file with that
     * name already exists in the folder it is never overwritten — a counter
     * is appended ("<name> (1).mp4", then " (2)"...). Audio, image and GIF
     * outputs ignore [inputName] and keep the old "Output<epoch>" name, as
     * does a video output with a missing or blank [inputName].
     */
    fun getFilePath(context: Context, fileExtension: String, inputName: String? = null): String {
        val publicDir = File(android.os.Environment.getExternalStorageDirectory(), "Ffmpeg Output")
        val dir = if (publicDir.exists() || publicDir.mkdirs()) {
            publicDir
        } else {
            File(context.getExternalFilesDir(Common.OUT_PUT_DIR).toString()).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        var extension: String? = null
        when {
            TextUtils.equals(fileExtension, IMAGE) -> {
                extension = "%03d.jpg"
            }
            TextUtils.equals(fileExtension, VIDEO) -> {
                extension = ".mp4"
            }
            TextUtils.equals(fileExtension, GIF) -> {
                extension = ".gif"
            }
            TextUtils.equals(fileExtension, MP3) -> {
                extension = ".mp3"
            }
        }
        val dest = if (TextUtils.equals(fileExtension, VIDEO) && !inputName.isNullOrBlank()) {
            uniqueFile(dir, sanitizeFileName(inputName), extension ?: ".mp4")
        } else {
            File(dir.path + File.separator + Common.OUT_PUT_DIR + System.currentTimeMillis().div(1000L) + extension)
        }
        return dest.absolutePath
    }

    /**
     * Strips characters illegal in a file name and trims the result, so a
     * picked file's name is safe to reuse as an output name. Falls back to
     * "Output" when nothing usable is left.
     */
    fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.', ' ')
            .take(100)
        return clean.ifEmpty { OUT_PUT_DIR }
    }

    /**
     * A file in [dir] named "<base><extension>", or "<base> (n)<extension>"
     * with the lowest free n when that name is already taken — an existing
     * file is never overwritten.
     */
    private fun uniqueFile(dir: File, base: String, extension: String): File {
        var candidate = File(dir, base + extension)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$extension")
            n++
        }
        return candidate
    }

    @Throws(java.io.IOException::class)
    fun getFileFromAssets(context: Context, fileName: String): File =
        File(context.cacheDir, fileName).also {
            if (!it.exists()) {
                it.outputStream().use { cache ->
                    context.assets.open(fileName).use { inputStream ->
                        inputStream.copyTo(cache)
                    }
                }
            }
        }
}
