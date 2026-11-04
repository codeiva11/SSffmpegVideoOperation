package com.simform.videoimageeditor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simform.videoimageeditor.ui.home.HomeScreen
import com.simform.videoimageeditor.ui.home.OtherMenuScreen
import com.simform.videoimageeditor.ui.home.VideoMenuScreen
import com.simform.videoimageeditor.ui.ops.audio.CompressAudioScreen
import com.simform.videoimageeditor.ui.ops.video.CompressVideoScreen

/**
 * One route per screen. Operation screens are added here in batches as they
 * are migrated; the menu lists only the screens that exist so far.
 */
object Routes {
    const val HOME = "home"
    const val VIDEO_OPS = "video_ops"
    const val OTHER_OPS = "other_ops"
    const val COMPRESS_VIDEO = "compress_video"
    const val COMPRESS_AUDIO = "compress_audio"
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                onVideoOps = { navController.navigate(Routes.VIDEO_OPS) },
                onOtherOps = { navController.navigate(Routes.OTHER_OPS) }
            )
        }
        composable(Routes.VIDEO_OPS) {
            VideoMenuScreen(
                onBack = { navController.popBackStack() },
                onOpen = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.OTHER_OPS) {
            OtherMenuScreen(
                onBack = { navController.popBackStack() },
                onOpen = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.COMPRESS_VIDEO) {
            CompressVideoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPRESS_AUDIO) {
            CompressAudioScreen(onBack = { navController.popBackStack() })
        }
    }
}
