package com.simform.videoimageeditor.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.navigation.Routes

private data class HomeItem(@StringRes val titleRes: Int, val route: String)

/**
 * Home screen: every operation on the front screen at once, as one flat
 * scrollable list of full-width teal buttons — the same buttons as the
 * original video menu (activity_video_process.xml), with the audio
 * operations after them. No two-category home: the two big buttons hid
 * everything inside them, so all features were invisible up front.
 * Order is the original layout order (Merge Video And Audio, Remove
 * Audio, Merge Image And MP3, Aspect Ratio at the tail), with H.265
 * where Compress Video used to be (Compress Video was removed:
 * H.265 HEVC is the compression now).
 */
private val ALL_OPERATIONS = listOf(
    HomeItem(R.string.cut_video_using_time, Routes.CUT_VIDEO),
    HomeItem(R.string.image_to_video, Routes.IMAGE_TO_VIDEO),
    HomeItem(R.string.add_water_mark_on_video, Routes.ADD_WATERMARK),
    HomeItem(R.string.add_text_on_video, Routes.ADD_TEXT),
    HomeItem(R.string.merge_image_and_video, Routes.COMBINE_IMAGE_VIDEO),
    HomeItem(R.string.merge_images, Routes.COMBINE_IMAGES),
    HomeItem(R.string.merge_videos, Routes.COMBINE_VIDEOS),
    HomeItem(R.string.h265_compress, Routes.H265_COMPRESS),
    HomeItem(R.string.extract_frame_from_video, Routes.EXTRACT_IMAGES),
    HomeItem(R.string.extract_audio_from_video, Routes.EXTRACT_AUDIO),
    HomeItem(R.string.fast_slow_motion_video, Routes.FAST_SLOW_VIDEO),
    HomeItem(R.string.reverse_video, Routes.REVERSE_VIDEO),
    HomeItem(R.string.video_fade_in_and_fade_out, Routes.FADE_IN_OUT),
    HomeItem(R.string.video_to_gif, Routes.VIDEO_TO_GIF),
    HomeItem(R.string.video_rotate, Routes.ROTATE_FLIP),
    HomeItem(R.string.merge_video_and_audio, Routes.MERGE_AUDIO_VIDEO),
    HomeItem(R.string.audio_remove_from_video, Routes.REMOVE_AUDIO),
    HomeItem(R.string.merge_image_and_audio, Routes.MERGE_IMAGE_MP3),
    HomeItem(R.string.set_aspect_ratio, Routes.ASPECT_RATIO),
    HomeItem(R.string.merge_gif, Routes.MERGE_GIF),
    HomeItem(R.string.merge_audios, Routes.MERGE_AUDIOS),
    HomeItem(R.string.change_audio_volume, Routes.CHANGE_AUDIO_VOLUME),
    HomeItem(R.string.fast_slow_audio_convert, Routes.FAST_SLOW_AUDIO),
    HomeItem(R.string.crop_audio_using_time, Routes.CROP_AUDIO),
    HomeItem(R.string.compress_audio, Routes.COMPRESS_AUDIO)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ALL_OPERATIONS.forEach { item ->
                Button(
                    onClick = { onOpen(item.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(item.titleRes).uppercase())
                }
            }
        }
    }
}
