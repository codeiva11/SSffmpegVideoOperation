package com.simform.videoimageeditor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simform.videoimageeditor.ui.home.HomeScreen
import com.simform.videoimageeditor.ui.ops.audio.ChangeAudioVolumeScreen
import com.simform.videoimageeditor.ui.ops.audio.CompressAudioScreen
import com.simform.videoimageeditor.ui.ops.audio.CropAudioScreen
import com.simform.videoimageeditor.ui.ops.audio.FastSlowAudioScreen
import com.simform.videoimageeditor.ui.ops.audio.MergeAudiosScreen
import com.simform.videoimageeditor.ui.ops.audio.MergeGifScreen
import com.simform.videoimageeditor.ui.ops.video.AddTextScreen
import com.simform.videoimageeditor.ui.ops.video.AddWaterMarkScreen
import com.simform.videoimageeditor.ui.ops.video.AspectRatioScreen
import com.simform.videoimageeditor.ui.ops.video.CombineImageAndVideoScreen
import com.simform.videoimageeditor.ui.ops.video.CombineImagesScreen
import com.simform.videoimageeditor.ui.ops.video.CombineVideosScreen
import com.simform.videoimageeditor.ui.ops.video.CutAudioFromVideoScreen
import com.simform.videoimageeditor.ui.ops.video.CutVideoScreen
import com.simform.videoimageeditor.ui.ops.video.ExtractAudioScreen
import com.simform.videoimageeditor.ui.ops.video.ExtractImagesScreen
import com.simform.videoimageeditor.ui.ops.video.FadeInFadeOutScreen
import com.simform.videoimageeditor.ui.ops.video.FastSlowVideoScreen
import com.simform.videoimageeditor.ui.ops.video.H265CompressScreen
import com.simform.videoimageeditor.ui.ops.video.ImageToVideoScreen
import com.simform.videoimageeditor.ui.ops.video.MergeAudioVideoScreen
import com.simform.videoimageeditor.ui.ops.video.MergeImageAndMp3Screen
import com.simform.videoimageeditor.ui.ops.video.RemoveAudioRangeScreen
import com.simform.videoimageeditor.ui.ops.video.RemoveAudioScreen
import com.simform.videoimageeditor.ui.ops.video.ReverseVideoScreen
import com.simform.videoimageeditor.ui.ops.video.RotateFlipScreen
import com.simform.videoimageeditor.ui.ops.video.VideoToGifScreen

/**
 * One route per screen — all 27 operations, plus home (which lists them
 * all flat, no menu screens).
 */
object Routes {
    const val HOME = "home"
    const val CUT_VIDEO = "cut_video"
    const val IMAGE_TO_VIDEO = "image_to_video"
    const val ADD_WATERMARK = "add_watermark"
    const val ADD_TEXT = "add_text"
    const val COMBINE_IMAGE_VIDEO = "combine_image_video"
    const val COMBINE_IMAGES = "combine_images"
    const val COMBINE_VIDEOS = "combine_videos"
    const val H265_COMPRESS = "h265_compress"
    const val EXTRACT_IMAGES = "extract_images"
    const val EXTRACT_AUDIO = "extract_audio"
    const val CUT_AUDIO_FROM_VIDEO = "cut_audio_from_video"
    const val FAST_SLOW_VIDEO = "fast_slow_video"
    const val REVERSE_VIDEO = "reverse_video"
    const val FADE_IN_OUT = "fade_in_out"
    const val VIDEO_TO_GIF = "video_to_gif"
    const val ROTATE_FLIP = "rotate_flip"
    const val REMOVE_AUDIO = "remove_audio"
    const val REMOVE_AUDIO_RANGE = "remove_audio_range"
    const val ASPECT_RATIO = "aspect_ratio"
    const val MERGE_AUDIO_VIDEO = "merge_audio_video"
    const val MERGE_IMAGE_MP3 = "merge_image_mp3"
    const val MERGE_GIF = "merge_gif"
    const val MERGE_AUDIOS = "merge_audios"
    const val CHANGE_AUDIO_VOLUME = "change_audio_volume"
    const val FAST_SLOW_AUDIO = "fast_slow_audio"
    const val CROP_AUDIO = "crop_audio"
    const val COMPRESS_AUDIO = "compress_audio"
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(onOpen = { route -> navController.navigate(route) })
        }
        val back: () -> Unit = { navController.popBackStack() }
        composable(Routes.CUT_VIDEO) { CutVideoScreen(onBack = back) }
        composable(Routes.IMAGE_TO_VIDEO) { ImageToVideoScreen(onBack = back) }
        composable(Routes.ADD_WATERMARK) { AddWaterMarkScreen(onBack = back) }
        composable(Routes.ADD_TEXT) { AddTextScreen(onBack = back) }
        composable(Routes.COMBINE_IMAGE_VIDEO) { CombineImageAndVideoScreen(onBack = back) }
        composable(Routes.COMBINE_IMAGES) { CombineImagesScreen(onBack = back) }
        composable(Routes.COMBINE_VIDEOS) { CombineVideosScreen(onBack = back) }
        composable(Routes.H265_COMPRESS) { H265CompressScreen(onBack = back) }
        composable(Routes.EXTRACT_IMAGES) { ExtractImagesScreen(onBack = back) }
        composable(Routes.EXTRACT_AUDIO) { ExtractAudioScreen(onBack = back) }
        composable(Routes.CUT_AUDIO_FROM_VIDEO) { CutAudioFromVideoScreen(onBack = back) }
        composable(Routes.FAST_SLOW_VIDEO) { FastSlowVideoScreen(onBack = back) }
        composable(Routes.REVERSE_VIDEO) { ReverseVideoScreen(onBack = back) }
        composable(Routes.FADE_IN_OUT) { FadeInFadeOutScreen(onBack = back) }
        composable(Routes.VIDEO_TO_GIF) { VideoToGifScreen(onBack = back) }
        composable(Routes.ROTATE_FLIP) { RotateFlipScreen(onBack = back) }
        composable(Routes.REMOVE_AUDIO) { RemoveAudioScreen(onBack = back) }
        composable(Routes.REMOVE_AUDIO_RANGE) { RemoveAudioRangeScreen(onBack = back) }
        composable(Routes.ASPECT_RATIO) { AspectRatioScreen(onBack = back) }
        composable(Routes.MERGE_AUDIO_VIDEO) { MergeAudioVideoScreen(onBack = back) }
        composable(Routes.MERGE_IMAGE_MP3) { MergeImageAndMp3Screen(onBack = back) }
        composable(Routes.MERGE_GIF) { MergeGifScreen(onBack = back) }
        composable(Routes.MERGE_AUDIOS) { MergeAudiosScreen(onBack = back) }
        composable(Routes.CHANGE_AUDIO_VOLUME) { ChangeAudioVolumeScreen(onBack = back) }
        composable(Routes.FAST_SLOW_AUDIO) { FastSlowAudioScreen(onBack = back) }
        composable(Routes.CROP_AUDIO) { CropAudioScreen(onBack = back) }
        composable(Routes.COMPRESS_AUDIO) { CompressAudioScreen(onBack = back) }
    }
}
