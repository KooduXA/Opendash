package com.kooduXA.opendash.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import com.kooduXA.opendash.domain.model.VideoFile

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EnhancedLocalGalleryScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    colors: DashboardColors = DayColors, // Placeholder if you removed theming
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val videos by viewModel.localVideos.collectAsState()

    // Load videos when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadLocalGallery(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("Phone Storage", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }

        // --- GRID ---
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloaded videos found.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(videos) { video ->
                    LocalVideoItem(video) {
                        playLocalVideo(context, video.downloadUrl)
                    }
                }
            }
        }
    }
}

@Composable
fun LocalVideoItem(video: VideoFile, onClick: () -> Unit) {
    val context = LocalContext.current

    // COIL Video Frame Loader
    val imageRequest = ImageRequest.Builder(context)
        .data(video.downloadUrl) // Local URI
        .decoderFactory(VideoFrameDecoder.Factory()) // Extract frame
        .videoFrameMillis(2000) // Take frame at 2 seconds
        .crossfade(true)
        .build()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(Modifier.fillMaxSize()) {
            // 1. Thumbnail
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Gradient Overlay (for text readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.8f))
                        )
                    )
            )

            // 3. Play Icon (Center)
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Play",
                tint = Color.White.copy(0.8f),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )

            // 4. Info (Bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = video.filename,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = video.size,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }

            // 5. Share Button (Top Right)
            IconButton(
                onClick = { shareVideo(context, video.downloadUrl) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Share, "Share", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// --- HELPERS ---

private fun playLocalVideo(context: android.content.Context, uriString: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle no player installed
    }
}

private fun shareVideo(context: android.content.Context, uriString: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    } catch (e: Exception) {
        // Handle error
    }
}