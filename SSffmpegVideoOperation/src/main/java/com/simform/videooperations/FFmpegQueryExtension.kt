package com.simform.videooperations

import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Created by Ashvin Vavaliya on 10,December,2020
 * Simform Solutions Pvt Ltd.
 */
public class FFmpegQueryExtension {
    public var FRAME_RATE: Int = 25 // Default value

    /**
     * startTime = 00:00:00 HH:MM:SS
     * endTime = 00:00:00 HH:MM:SS
     */
    /**
     * Frame-accurate cut (seek after -i, so the video is re-encoded).
     * Video is H.265 HEVC at CRF 28 — the Fast profile's quality: about
     * H.264 CRF 23 quality at half the size — with -tag:v hvc1. Audio
     * is copied untouched (-c:a copy): a cut does not change the audio,
     * so re-encoding it would only lose quality.
     */
    fun cutVideo(inputVideoPath: String, startTime: String?, endTime: String?, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideoPath)
            add("-ss")
            add(startTime.toString())
            add("-to")
            add(endTime.toString())
            add("-r")
            add("$FRAME_RATE")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("copy")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * second = how many long create the video in second
     * input = Local image path
     * width = video width
     * height = video height
     */
    fun imageToVideo(input: String, output: String, second: Int, width: Int?, height: Int?): Array<String> {
        val fadeEndDuration = second - 0.5
        val fade = "fps=$FRAME_RATE,fade=type=in:duration=1,fade=type=out:duration=0.5:start_time=$fadeEndDuration"
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-loop")
            add("1")
            add("-i")
            add(input)
            add("-s")
            add("${width}x${height}")
            add("-vf")
            add("format=yuv420p,$fade")
            add("-t")
            add("$second")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * posX = X position of water-mark in percentage (1% to 100%)
     * posY = Y position of water-mark in percentage (1% to 100%)
     */
    fun addVideoWaterMark(inputVideo: String, imageInput: String, posX: Float?, posY: Float?, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-i")
            add(imageInput)
            add("-filter_complex")
            add("overlay=$posX:$posY")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("copy")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun addTextOnVideo(inputVideo: String, textInput: String, posX: Float?, posY: Float?, fontPath: String, isTextBackgroundDisplay: Boolean, fontSize: Int, fontcolor: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        var borderQuery = ""
        if (isTextBackgroundDisplay) {
            borderQuery = ":box=1:boxcolor=black@0.5:boxborderw=5"
        }
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-vf")
            add("drawtext=text='$textInput':fontfile=$fontPath:x=$posX:y=$posY:fontsize=$fontSize:fontcolor=$fontcolor${borderQuery.trim()}")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("copy")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun combineImagesAndVideos(paths: ArrayList<Paths>, width: Int?, height: Int?, second: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        for (i in 0 until paths.size) {
            //for input
            if (paths[i].isImageFile) {
                inputs.add("-loop")
                inputs.add("1")
                inputs.add("-framerate")
                inputs.add("$FRAME_RATE")
                inputs.add("-t")
                inputs.add(second)
                inputs.add("-i")
                inputs.add(paths[i].filePath)
            } else {
                inputs.add("-i")
                inputs.add(paths[i].filePath)
            }
        }

        var query: String? = ""
        var queryAudio: String? = ""
        for (i in 0 until paths.size) {
            query = query?.trim()
            query += "[" + i + ":v]scale=${width}x${height},setdar=$width/$height[" + i + "v];"

            queryAudio = queryAudio?.trim()
            queryAudio += if (paths[i].isImageFile) {
                "[" + i + "v][" + paths.size + ":a]"
            } else {
                "[" + i + "v][" + i + ":a]"
            }
        }
        return getResult(inputs, query, queryAudio, paths, output)
    }

    fun combineVideos(paths: ArrayList<Paths>, width: Int?, height: Int?, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        var query: String? = ""
        var queryAudio: String? = ""
        for (i in 0 until paths.size) {
            //for input
            inputs.add("-i")
            inputs.add(paths[i].filePath)

            //for video setting with width and height
            query = query?.trim()
            query += "[" + i + ":v]scale=${width}x${height},setdar=$width/$height[" + i + "v];"

            //for video and audio combine {without audio this query not supported so applied this function}
            queryAudio = queryAudio?.trim()
            queryAudio += if (paths[i].isImageFile) {
                "[" + i + "v][" + paths.size + ":a]"
            } else {
                "[" + i + "v][" + i + ":a]"
            }
        }
        return getResult(inputs, query, queryAudio, paths, output)
    }

    /**
     * Video is H.265 HEVC at CRF 28 (-tag:v hvc1) — the Fast profile's
     * quality — in every operation that re-encodes video. Audio is
     * copied untouched wherever the operation does not change it
     * (-c:a copy); where audio must be re-encoded it follows the H.265
     * rule: AAC at input bitrate + 64k, capped at 320k, 192k fallback
     * (see audioBitrateKbps). The multi-input concat below is the one
     * exception: with N inputs there is no single input bitrate to
     * probe, so it uses the rule's 192k fallback directly.
     */
    private fun audioBitrateKbps(inputPath: String): Int {
        return probeAudioBitrateKbps(inputPath)?.let { minOf(it + 64, 320) } ?: 192
    }

    private fun getResult(inputs: java.util.ArrayList<String>, query: String?, queryAudio: String?, paths: ArrayList<Paths>, output: String): Array<String> {
        inputs.apply {
            add("-f")
            add("lavfi")
            add("-t")
            add("0.1")
            add("-i")
            add("anullsrc")
            add("-filter_complex")
            add(query + queryAudio + "concat=n=" + paths.size + ":v=1:a=1 [v][a]")
            add("-map")
            add("[v]")
            add("-map")
            add("[a]")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("aac")
            add("-b:a")
            add("192k")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun compressor(inputVideo: String, width: Int?, height: Int?, outputVideo: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-i")
            add(inputVideo)
            add("-s")
            add("${width}x${height}")
            add("-r")
            add("${if (FRAME_RATE >= 10) FRAME_RATE - 5 else FRAME_RATE}")
            add("-vcodec")
            add("mpeg4")
            add("-b:v")
            add("150k")
            add("-b:a")
            add("48000")
            add("-ac")
            add("2")
            add("-ar")
            add("22050")
            add("-preset")
            add("ultrafast")
            add(outputVideo)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * H.265 (HEVC) compression, Sunil's three commands:
     * `-c:v libx265 -crf 28` (Fast, preset Auto),
     * `-preset slower -crf 26` (Balanced),
     * `-preset slow -crf 24` (Best, default).
     *
     * Preset "Auto": x265 has NO preset named "auto" (valid presets are
     * ultrafast..placebo, FFmpeg Wiki H.265 guide) and its DEFAULT preset
     * is medium — so Auto means the `-preset` flag is not passed at all
     * and x265 uses medium. Slower presets squeeze smaller files out of
     * the same CRF, at the cost of longer encode time on the phone.
     *
     * `-tag:v hvc1` on every output: FFmpeg otherwise writes the HEVC
     * tag as hev1 in MP4, which Apple players (iOS/macOS/QuickTime)
     * refuse — hvc1 is the tag they accept.
     *
     * Audio is NOT the fixed 96k/128k from those commands: a fixed
     * bitrate would be WORSE than the input for high-bitrate sources
     * (e.g. 192k in -> 96k out). Audio stays AAC at a HIGHER bitrate
     * than the input audio (input + 64k, capped at 320k), so the output
     * always carries H.265 video together with better-quality AAC audio.
     * When the input audio bitrate cannot be probed, or the input has no
     * audio stream, 192k is used. Video keeps its original frame rate.
     *
     * Input may be any video format / container and any audio codec:
     * FFmpeg detects the format automatically from the file itself.
     *
     * NOTE: probing runs FFprobeKit, which blocks — callers must build
     * this query off the main thread.
     */
    fun compressH265(
        inputVideo: String,
        output: String,
        crf: Int = 28,
        preset: String = "auto"
    ): Array<String> {
        val audioKbps = probeAudioBitrateKbps(inputVideo)
            ?.let { minOf(it + 64, 320) } ?: 192
        val args = mutableListOf(
            "-y", "-i", inputVideo,
            "-c:v", "libx265",
            "-crf", crf.toString(),
            "-tag:v", "hvc1"
        )
        if (!preset.equals("auto", ignoreCase = true)) {
            args.add("-preset")
            args.add(preset)
        }
        args.add("-c:a")
        args.add("aac")
        args.add("-b:a")
        args.add("${audioKbps}k")
        args.add(output)
        return args.toTypedArray()
    }

    /**
     * First audio stream bitrate in kbps, or null when the input has no
     * audio stream, the bitrate is not recorded, or the probe fails.
     * Blocking — call only off the main thread.
     */
    private fun probeAudioBitrateKbps(inputPath: String): Int? {
        return try {
            val session = FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=bit_rate",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    inputPath
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode)) return null
            session.output?.trim()?.toIntOrNull()?.let { it / 1000 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * True when the input has at least one audio stream, else false.
     * Blocking — call only off the main thread.
     */
    private fun hasAudioStream(inputPath: String): Boolean {
        return try {
            val session = FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-select_streams", "a",
                    "-show_entries", "stream=index",
                    "-of", "csv=p=0",
                    inputPath
                )
            )
            ReturnCode.isSuccess(session.returnCode) && !session.output.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun extractImages(inputVideo: String, output: String, spaceOfFrame: Float): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-vf")
            add("fps=$spaceOfFrame") // here It is 4 frames per second,
            // If you will get frame per second then replace fps=1 and
            // if you will get frames per 30 seconds then replace it into fps = 1/30
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun extractAudio(inputVideo: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-i")
            add(inputVideo)
            add("-vn")
            add("-ar")
            add("44100")
            add("-ac")
            add("2")
            add("-b:a")
            add("256k")
            add("-f")
            add("mp3")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Fast/slow motion: video is H.265 HEVC at CRF 28 (-tag:v hvc1)
     * instead of the old MPEG-4 at 2097k. Audio goes through atempo,
     * so it is re-encoded with the H.265 audio rule (input + 64k, max
     * 320k, 192k fallback). Probing blocks — callers must build this
     * query off the main thread (FastSlowVideoViewModel does).
     * -r 60 is kept exactly as before.
     */
    fun videoMotion(inputVideo: String, output: String, setpts: Double, atempo: Double): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-i")
            add(inputVideo)
            add("-filter_complex")
            add("[0:v]setpts=${setpts}*PTS[v];[0:a]atempo=${atempo}[a]")
            add("-map")
            add("[v]")
            add("-map")
            add("[a]")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("aac")
            add("-b:a")
            add("${audioBitrateKbps(inputVideo)}k")
            add("-r")
            add("60")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Reverse: video is H.265 HEVC at CRF 28 (-tag:v hvc1). With audio
     * reversed, audio is re-encoded with the H.265 audio rule (probing
     * blocks — callers must build this query off the main thread,
     * ReverseVideoViewModel does); without audio the stream is dropped
     * (-an) as before.
     */
    fun videoReverse(inputVideo: String, isWithAudioReverse: Boolean, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            if (isWithAudioReverse) {
                add("-vf")
                add("reverse")
                add("-af")
                add("areverse")
                add("-c:v")
                add("libx265")
                add("-crf")
                add("28")
                add("-tag:v")
                add("hvc1")
                add("-c:a")
                add("aac")
                add("-b:a")
                add("${audioBitrateKbps(inputVideo)}k")
            } else {
                add("-an")
                add("-vf")
                add("reverse")
                add("-c:v")
                add("libx265")
                add("-crf")
                add("28")
                add("-tag:v")
                add("hvc1")
            }
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun videoFadeInFadeOut(inputVideo: String, duration: Long, fadeInEndSeconds: Int, fadeOutStartSeconds: Int, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-i")
            add(inputVideo)
            add("-acodec")
            add("copy")
            add("-vf")
            add("fade=t=in:st=0:d=$fadeInEndSeconds,fade=t=out:st=${duration - fadeOutStartSeconds}:d=$fadeOutStartSeconds")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * GIF with a proper palette: the old command had no palette at all,
     * so FFmpeg used a single fixed 256-colour palette and the GIF came
     * out washed out. This is the FFmpeg Wiki's single-pass high-quality
     * GIF recipe — split the stream, generate a palette from this video,
     * use it — same single invocation, same fps and resolution.
     */
    fun convertVideoToGIF(inputVideo: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-vf")
            add("split[s0][s1];[s0]palettegen=max_colors=256[p];[s1][p]paletteuse")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun rotateVideo(inputVideo: String, degree: Int, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-map_metadata")
            add("0")
            add("-metadata:s:v")
            add("rotate=$degree")
            add("-codec")
            add("copy")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * degree = 0: 90 Counter Clockwise and Vertical Flip  (default)
     * degree = 1: 90 Clockwise
     * degree = 2: 90 Counter Clockwise
     * degree = 3: 90 Clockwise and Vertical Flip
    */
    fun flipVideo(inputVideo: String, degree: Int, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-vf")
            add("transpose=$degree")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("copy")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun mergeAudioVideo(inputVideo: String, inputAudio: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-i")
            add(inputAudio)
            add("-filter_complex")
            add("[1:a]volume=0.15,apad[A];[0:a][A]amerge[out]")
            add("-c:v")
            add("copy")
            add("-map")
            add("0:v")
            add("-map")
            add("[out]")
            add("-y")
            add("-shortest")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Removing audio needs no video re-encode at all: stream copy is
     * instant and lossless. The old command re-encoded the whole video
     * at ultrafast for nothing — quality loss and a slow encode.
     */
    fun removeAudioFromVideo(inputVideo: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-c:v")
            add("copy")
            add("-an")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Remove the audio ONLY in [startSeconds, endSeconds] from a video:
     * the video stream is copied untouched (`-c:v copy` — lossless, fast,
     * no re-encode) and the audio is muted in the range via
     * `-af "volume=0:enable='between(t,START,END)'"`, re-encoded AAC at
     * the H.265 audio rule (input + 64k, capped at 320k, 192k fallback).
     * The output video keeps its full length; only the chosen range is
     * silent. Throws IllegalArgumentException when the video has no
     * audio stream. Blocking (probes) — call only off the main thread.
     */
    fun removeAudioRangeFromVideo(
        inputVideo: String,
        startSeconds: Int,
        endSeconds: Int,
        output: String
    ): Array<String> {
        if (!hasAudioStream(inputVideo)) {
            throw IllegalArgumentException("No audio stream found in the selected video")
        }
        val audioKbps = probeAudioBitrateKbps(inputVideo)
            ?.let { minOf(it + 64, 320) } ?: 192
        val args = mutableListOf(
            "-y", "-i", inputVideo,
            "-c:v", "copy",
            "-af", "volume=0:enable='between(t,$startSeconds,$endSeconds)'",
            "-c:a", "aac",
            "-b:a", "${audioKbps}k",
            output
        )
        return args.toTypedArray()
    }

    fun mergeImageAndAudio(inputImage: String, inputAudio: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-loop")
            add("1")
            add("-i")
            add(inputImage)
            add("-i")
            add(inputAudio)
            add("-shortest")
            add("-c:v")
            add("libx265")
            add("-crf")
            add("28")
            add("-tag:v")
            add("hvc1")
            add("-c:a")
            add("copy")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Aspect ratio is container metadata only — no pixel changes — so
     * stream copy does it instantly and losslessly. The old command
     * re-encoded the whole video at ultrafast for nothing.
     */
    fun applyRatio(inputVideo: String, ratio: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-c")
            add("copy")
            add("-aspect")
            add(ratio)
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun mergeGIF(gifInput: ArrayList<Paths>, posX: Float?, posY: Float?, width: Float?, height: Float?, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-ignore_loop")
            add("0")
            for (i in 0 until gifInput.size) {
                add("-i")
                add(gifInput[i].filePath)
            }
            add("-filter_complex")
            add("[1]scale=$width:$height[s1];[0][s1]overlay=$posX:$posY:shortest=1")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun mergeAudios(inputAudioList: ArrayList<Paths>, duration: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            for (i in 0 until inputAudioList.size) {
                add("-i")
                add(inputAudioList[i].filePath)
            }
            add("-filter_complex")
            add("amix=inputs=${inputAudioList.size}:duration=$duration:dropout_transition=${inputAudioList.size}")
            add("-codec:a")
            add("libmp3lame")
            add("-q:a")
            add("0")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun audioVolumeUpdate(inputFile: String, volume: Float, output: String): Array<String>
    {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputFile)
            add("-af")
            add("volume=$volume")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun audioMotion(inputVideo: String, output: String, atempo: Double): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideo)
            add("-filter:a")
            add("atempo=$atempo")
            add("-vn")
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun cutAudio(inputVideoPath: String, startTime: String?, endTime: String?, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputVideoPath)
            add("-ss")
            add(startTime.toString())
            add("-to")
            add(endTime.toString())
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    /**
     * Cut a time range of a VIDEO's audio into a separate audio file:
     * `-i video -ss start -to end -map 0:a -c:a aac -b:a <rule> out.mp3`.
     * Only the audio stream is mapped — no video stream in the output.
     * Audio bitrate follows the H.265 rule (input + 64k, capped at 320k,
     * 192k fallback). No `-preset ultrafast` — audio encoding needs none.
     * Unlike [cutAudio] (audio-file input, used by Crop Audio), this
     * takes a video file. Throws IllegalArgumentException when the video
     * has no audio stream. Blocking (probes) — call only off the main
     * thread.
     */
    fun cutAudioFromVideo(
        inputVideo: String,
        startTime: String?,
        endTime: String?,
        output: String
    ): Array<String> {
        if (!hasAudioStream(inputVideo)) {
            throw IllegalArgumentException("No audio stream found in the selected video")
        }
        val audioKbps = probeAudioBitrateKbps(inputVideo)
            ?.let { minOf(it + 64, 320) } ?: 192
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-y")
            add("-i")
            add(inputVideo)
            add("-ss")
            add(startTime.toString())
            add("-to")
            add(endTime.toString())
            add("-map")
            add("0:a")
            add("-c:a")
            add("aac")
            add("-b:a")
            add("${audioKbps}k")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }

    fun compressAudio(inputAudioPath: String, bitrate: String, output: String): Array<String> {
        val inputs: ArrayList<String> = ArrayList()
        inputs.apply {
            add("-i")
            add(inputAudioPath)
            add("-ab")
            add(bitrate)
            add("-preset")
            add("ultrafast")
            add(output)
        }
        return inputs.toArray(arrayOfNulls<String>(inputs.size))
    }
}

