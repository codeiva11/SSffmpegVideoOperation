package com.simform.videoimageeditor.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.common.OperationScaffold
import com.simform.videoimageeditor.ui.navigation.Routes

private data class MenuItem(@StringRes val titleRes: Int, val route: String)

/**
 * Video operations menu. Rows are added here as each screen is migrated;
 * only migrated screens are listed so every row opens a real destination.
 */
private val VIDEO_MENU = listOf(
    MenuItem(R.string.compress_a_video, Routes.COMPRESS_VIDEO)
)

/** Audio/GIF operations menu. Same rule as [VIDEO_MENU]. */
private val OTHER_MENU = listOf(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuList(items: List<MenuItem>, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            ElevatedCard(onClick = { onOpen(item.route) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
