package com.simform.videoimageeditor.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.navigation.Routes

private data class MenuItem(@StringRes val titleRes: Int, val route: String)

/** Video operations menu — all 19 screens, in the old feature order. */
private val VIDEO_MENU = listOf(
    MenuItem(R.string.cut_video_using_time, Routes.CUT_VIDEO),
    MenuItem(R.string.image_to_video, Routes.IMAGE_TO_VIDEO),
    MenuItem(R.string.add_water_mark_on_video, Routes.ADD_WATERMARK),
    MenuItem(R.string.add_text_on_video, Routes.ADD_TEXT),
    MenuItem(R.string.merge_image_and_video, Routes.COMBINE_IMAGE_VIDEO),
    MenuItem(R.string.merge_images, Routes.COMBINE_IMAGES),
    MenuItem(R.string.merge_videos, Routes.COMBINE_VIDEOS),
    MenuItem(R.string.compress_a_video, Routes.COMPRESS_VIDEO),
    MenuItem(R.string.h265_compress, Routes.H265_COMPRESS),
    MenuItem(R.string.extract_frame_from_video, Routes.EXTRACT_IMAGES),
    MenuItem(R.string.extract_audio_from_video, Routes.EXTRACT_AUDIO),
    MenuItem(R.string.fast_slow_motion_video, Routes.FAST_SLOW_VIDEO),
    MenuItem(R.string.reverse_video, Routes.REVERSE_VIDEO),
    MenuItem(R.string.video_fade_in_and_fade_out, Routes.FADE_IN_OUT),
    MenuItem(R.string.video_to_gif, Routes.VIDEO_TO_GIF),
    MenuItem(R.string.video_rotate, Routes.ROTATE_FLIP),
    MenuItem(R.string.audio_remove_from_video, Routes.REMOVE_AUDIO),
    MenuItem(R.string.set_aspect_ratio, Routes.ASPECT_RATIO),
    MenuItem(R.string.merge_video_and_audio, Routes.MERGE_AUDIO_VIDEO),
    MenuItem(R.string.merge_image_and_audio, Routes.MERGE_IMAGE_MP3)
)

/** Audio/GIF operations menu — all 6 screens. */
private val OTHER_MENU = listOf(
    MenuItem(R.string.merge_gif, Routes.MERGE_GIF),
    MenuItem(R.string.merge_audios, Routes.MERGE_AUDIOS),
    MenuItem(R.string.change_audio_volume, Routes.CHANGE_AUDIO_VOLUME),
    MenuItem(R.string.fast_slow_audio_convert, Routes.FAST_SLOW_AUDIO),
    MenuItem(R.string.crop_audio_using_time, Routes.CROP_AUDIO),
    MenuItem(R.string.compress_audio, Routes.COMPRESS_AUDIO)
)

@Composable
fun VideoMenuScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    OperationScaffold(title = stringResource(R.string.video_operations), onBack = onBack) {
        MenuList(VIDEO_MENU, onOpen)
    }
}

@Composable
fun OtherMenuScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    OperationScaffold(title = stringResource(R.string.other_ffmpeg_operations), onBack = onBack) {
        MenuList(OTHER_MENU, onOpen)
    }
}

/**
 * Menu exactly as the original activity_video_process.xml /
 * activity_other_ffmpeg_process.xml: full-width teal buttons
 * (@style/ButtonStyle — 16dp side margins from the scaffold, 12dp
 * vertical padding, white text). The old Button widget uppercased
 * labels automatically; Compose does not, so uppercase() is applied.
 */
@Composable
private fun MenuList(items: List<MenuItem>, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
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
