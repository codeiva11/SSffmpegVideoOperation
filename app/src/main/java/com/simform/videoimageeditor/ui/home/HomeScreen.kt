package com.simform.videoimageeditor.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simform.videoimageeditor.R
import com.simform.videoimageeditor.ui.navigation.Routes

private data class HomeItem(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: String,
    val subtitle: String? = null
)

private data class HomeTile(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val items: List<HomeItem>
)

/**
 * Home screen (redesigned 2026-11-21): the 28 operations live inside
 * tiles, grouped by what they do — so the home fits on one screen with
 * almost no scrolling. A tile expands IN PLACE on the home screen: tap
 * it and its operations appear inside the tile; tap again and it
 * collapses. No sub-screens, no menus, no tabs. YouTube Video Download
 * is a standalone hero tile at the very top, always visible, never
 * buried inside another tile. Expanded/collapsed state is saved, so
 * the tiles you use stay open.
 *
 * Order inside each tile follows the original layout order. Labels are
 * title case (the strings already are; the old uppercase() is gone).
 */
private val YOUTUBE_ITEM = HomeItem(
    R.string.youtube_video_download, Icons.Filled.Download,
    Routes.YOUTUBE_DOWNLOAD, "Download & convert to H.265"
)

private val TILES = listOf(
    HomeTile(
        "video_edit", "Video Edit", Icons.Filled.Movie,
        listOf(
            HomeItem(R.string.cut_video_using_time, Icons.Filled.ContentCut, Routes.CUT_VIDEO),
            HomeItem(R.string.fast_slow_motion_video, Icons.Filled.Speed, Routes.FAST_SLOW_VIDEO),
            HomeItem(R.string.reverse_video, Icons.Filled.Replay, Routes.REVERSE_VIDEO),
            HomeItem(R.string.video_fade_in_and_fade_out, Icons.Filled.BrightnessMedium, Routes.FADE_IN_OUT),
            HomeItem(R.string.video_rotate, Icons.Filled.Flip, Routes.ROTATE_FLIP),
            HomeItem(R.string.set_aspect_ratio, Icons.Filled.AspectRatio, Routes.ASPECT_RATIO),
            HomeItem(R.string.add_water_mark_on_video, Icons.Filled.BrandingWatermark, Routes.ADD_WATERMARK),
            HomeItem(R.string.add_text_on_video, Icons.Filled.TextFields, Routes.ADD_TEXT)
        )
    ),
    HomeTile(
        "video_convert", "Video Convert", Icons.Filled.SwapHoriz,
        listOf(
            HomeItem(R.string.image_to_video, Icons.Filled.Image, Routes.IMAGE_TO_VIDEO),
            HomeItem(
                R.string.h265_compress, Icons.Filled.Compress,
                Routes.H265_COMPRESS, "Smaller files, same quality"
            ),
            HomeItem(R.string.extract_frame_from_video, Icons.Filled.BurstMode, Routes.EXTRACT_IMAGES),
            HomeItem(R.string.video_to_gif, Icons.Filled.Gif, Routes.VIDEO_TO_GIF)
        )
    ),
    HomeTile(
        "combine", "Combine & Merge", Icons.Filled.GroupWork,
        listOf(
            HomeItem(R.string.merge_image_and_video, Icons.Filled.Movie, Routes.COMBINE_IMAGE_VIDEO),
            HomeItem(R.string.merge_images, Icons.Filled.Collections, Routes.COMBINE_IMAGES),
            HomeItem(R.string.merge_videos, Icons.Filled.VideoLibrary, Routes.COMBINE_VIDEOS),
            HomeItem(R.string.merge_video_and_audio, Icons.Filled.CallMerge, Routes.MERGE_AUDIO_VIDEO),
            HomeItem(R.string.merge_image_and_audio, Icons.Filled.MergeType, Routes.MERGE_IMAGE_MP3),
            HomeItem(R.string.merge_gif, Icons.Filled.Animation, Routes.MERGE_GIF),
            HomeItem(R.string.merge_audios, Icons.Filled.LibraryMusic, Routes.MERGE_AUDIOS)
        )
    ),
    HomeTile(
        "audio", "Audio Tools", Icons.Filled.MusicNote,
        listOf(
            HomeItem(R.string.extract_audio_from_video, Icons.Filled.Audiotrack, Routes.EXTRACT_AUDIO),
            HomeItem(R.string.cut_audio_from_video, Icons.Filled.AudioFile, Routes.CUT_AUDIO_FROM_VIDEO),
            HomeItem(R.string.audio_remove_from_video, Icons.Filled.VolumeOff, Routes.REMOVE_AUDIO),
            HomeItem(R.string.remove_audio_range_from_video, Icons.Filled.MusicOff, Routes.REMOVE_AUDIO_RANGE),
            HomeItem(R.string.change_audio_volume, Icons.Filled.VolumeUp, Routes.CHANGE_AUDIO_VOLUME),
            HomeItem(R.string.fast_slow_audio_convert, Icons.Filled.FastForward, Routes.FAST_SLOW_AUDIO),
            HomeItem(R.string.crop_audio_using_time, Icons.Filled.Timer, Routes.CROP_AUDIO),
            HomeItem(R.string.compress_audio, Icons.Filled.Archive, Routes.COMPRESS_AUDIO)
        )
    )
)

private const val PREFS_NAME = "home_tiles"
private const val KEY_EXPANDED = "expanded_tiles"

private fun loadExpanded(context: Context): Set<String> {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(KEY_EXPANDED, emptySet())?.toSet().orEmpty()
}

private fun saveExpanded(context: Context, expanded: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_EXPANDED, expanded).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(loadExpanded(context)) }
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search operations…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(12.dp))
            // Searching: a tile shows only when it holds a match, and
            // it opens by itself to show the matching operations.
            // Not searching: tiles follow their saved open/closed state.
            // (The title lookup is a @Composable call, so the filter
            // is inline here — a local fun would need @Composable too.)
            val searching = query.isNotBlank()
            val youtubeVisible = !searching ||
                stringResource(YOUTUBE_ITEM.titleRes)
                    .contains(query, ignoreCase = true)
            val visibleTiles = TILES.mapNotNull { tile ->
                val shown = if (searching) {
                    tile.items.filter {
                        stringResource(it.titleRes)
                            .contains(query, ignoreCase = true)
                    }
                } else {
                    tile.items
                }
                if (shown.isEmpty()) null else tile to shown
            }
            if (!youtubeVisible && visibleTiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No operations match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (youtubeVisible) {
                        item(key = Routes.YOUTUBE_DOWNLOAD) {
                            YoutubeHeroTile(onClick = { onOpen(YOUTUBE_ITEM.route) })
                        }
                    }
                    items(visibleTiles, key = { (tile, _) -> tile.id }) { (tile, shown) ->
                        val isOpen = searching || tile.id in expanded
                        TileCard(
                            tile = tile,
                            shownItems = shown,
                            isOpen = isOpen,
                            onToggle = {
                                val next = if (tile.id in expanded) {
                                    expanded - tile.id
                                } else {
                                    expanded + tile.id
                                }
                                expanded = next
                                saveExpanded(context, next)
                            },
                            onOpen = onOpen
                        )
                    }
                }
            }
        }
    }
}

/** YouTube Video Download: standalone hero tile, always first.
 * Its row uses the exact same metrics as a group tile's header row
 * (12dp vertical padding, 40dp icon circle, titleMedium title,
 * single-line title + subtitle), so it is the same height as the
 * other tiles — only the teal background sets it apart. */
@Composable
private fun YoutubeHeroTile(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(YOUTUBE_ITEM.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                YOUTUBE_ITEM.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileCard(
    tile: HomeTile,
    shownItems: List<HomeItem>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tile.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${tile.items.size} tools",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isOpen) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = if (isOpen) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOpen) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Column(
                    modifier = Modifier.padding(
                        start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    shownItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpen(item.route) }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary
                                            .copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.subtitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
