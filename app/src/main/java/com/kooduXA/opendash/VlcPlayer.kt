package com.kooduXA.opendash

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer


// Helper function to format video duration
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

// Video player controls component
@Composable
fun VideoPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }

        Text(
            formatDuration(currentPosition),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelMedium
        )

        Slider(
            value = currentPosition.toFloat(),
            onValueChange = onSeek,
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = androidx.compose.ui.graphics.Color.White,
                activeTrackColor = androidx.compose.ui.graphics.Color.Red,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.Gray
            )
        )

        Text(
            formatDuration(duration),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}


@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val context = LocalContext.current

    // 1. Initialize LibVLC and MediaPlayer once per composition lifecycle
    val libVlc = remember {
        val options = arrayListOf(
            "--rtsp-tcp",
            "--network-caching=300",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--drop-late-frames",
            "--skip-frames"
        )
        LibVLC(context, options)
    }

    val mediaPlayer = remember { MediaPlayer(libVlc) }

    // 2. Handle URL changes and playing state
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            val media = Media(libVlc, Uri.parse(url)).apply {
                addOption(":rtsp-tcp")
                addOption(":network-caching=300")
            }
            mediaPlayer.media = media
            media.release() // Release the java object, C++ ref is held by player
            mediaPlayer.play()
        }
    }

    // 3. Cleanup when the Composable is removed (e.g., navigating away)
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVlc.release()
        }
    }

    // 4. The View: Handle Surface creation/destruction
    AndroidView(
        factory = { ctx ->
            android.view.SurfaceView(ctx).apply {
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        // Attach to VLC
                        mediaPlayer.vlcVout.setVideoView(this@apply)
                        mediaPlayer.vlcVout.attachViews()
                        mediaPlayer.vlcVout.setWindowSize(width, height) // Force initial size
                    }

                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
                        // CRITICAL FOR ROTATION: Update VLC with new surface dimensions
                        mediaPlayer.vlcVout.setWindowSize(w, h)
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        mediaPlayer.vlcVout.detachViews()
                    }
                })
            }
        },
        modifier = modifier.fillMaxSize() // Ensure the SurfaceView tries to fill the Box
    )
}


//@Composable
//fun VideoPlayer(
//    url: String,
//    modifier: Modifier = Modifier,
//    isPlaying: Boolean = true
//) {
//    val context = LocalContext.current
//
//    val libVlc = remember {
//        val options = arrayListOf(
//            "--rtsp-tcp",           // Mandatory for stable dashcam stream
//            "--network-caching=300", // 300ms latency (balance between stable and live)
//            "--clock-jitter=0",
//            "--clock-synchro=0",
//            "--drop-late-frames",
//            "--skip-frames"
//        )
//        LibVLC(context, options)
//    }
//
//    val mediaPlayer = remember { MediaPlayer(libVlc) }
//
//    DisposableEffect(url) {
//        val media = Media(libVlc, Uri.parse(url)).apply {
//            addOption(":rtsp-tcp")
//            addOption(":network-caching=300")
//        }
//        mediaPlayer.media = media
//        mediaPlayer.play()
//
//        // This ensures the video fills the surface view logic
//        mediaPlayer.aspectRatio = null
//        mediaPlayer.setScale(0f)
//
//        onDispose {
//            mediaPlayer.stop()
//            mediaPlayer.release()
//            libVlc.release()
//        }
//    }
//
//    // Use a Layout wrapper to handle Aspect Ratio correctly
//    AndroidView(
//        factory = { ctx ->
//            val frame = FrameLayout(ctx)
//            val surface = SurfaceView(ctx)
//            val params = FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
//            frame.addView(surface, params)
//
//            mediaPlayer.vlcVout.setVideoView(surface)
//            mediaPlayer.vlcVout.attachViews()
//            frame
//        },
//        modifier = modifier
//    )
//}