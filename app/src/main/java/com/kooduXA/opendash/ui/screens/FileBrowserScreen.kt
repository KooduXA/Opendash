package com.kooduXA.opendash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kooduXA.opendash.domain.model.VideoFile

@Composable
fun FileBrowserScreen(
    files: List<VideoFile>,
    onFileClick: (VideoFile) -> Unit,
    onDownloadClick: (VideoFile) -> Unit,
    onDeleteClick: (VideoFile) -> Unit,
    onClose: () -> Unit,
    downloadProgress: Float? = null
) {
    // STATE: Track which file is queued for deletion
    var fileToDelete by remember { mutableStateOf<VideoFile?>(null) }
    // STATE: Track View Mode (List vs Grid) - Default to Grid
    var isGridView by remember { mutableStateOf(true) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF121212))) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            // HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Camera Storage",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // VIEW TOGGLE BUTTON
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "Switch View",
                        tint = Color.White
                    )
                }
            }

            // CONTENT
            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files found or not connected.", color = Color.Gray)
                }
            } else {
                if (isGridView) {
                    // GRID VIEW
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files) { file ->
                            FileGridItem(
                                file = file,
                                onClick = onFileClick,
                                onDownload = onDownloadClick,
                                onDelete = { fileToDelete = it }
                            )
                        }
                    }
                } else {
                    // LIST VIEW
                    LazyColumn {
                        items(files) { file ->
                            FileItem(
                                file = file,
                                onClick = onFileClick,
                                onDownload = onDownloadClick,
                                onDelete = { fileToDelete = it }
                            )
                        }
                    }
                }
            }
        }

        // ... (Keep Download Overlay and Delete Dialog exactly as they were) ...
        // DOWNLOAD OVERLAY
        if (downloadProgress != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = downloadProgress, color = Color(0xFF00E676))
                    Spacer(Modifier.height(16.dp))
                    Text("Downloading... ${(downloadProgress * 100).toInt()}%", color = Color.White)
                }
            }
        }

        // CONFIRMATION DIALOG
        if (fileToDelete != null) {
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text("Delete File?") },
                text = { Text("Are you sure you want to delete '${fileToDelete?.filename}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            fileToDelete?.let { onDeleteClick(it) }
                            fileToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }
    }
}

@Composable
fun FileGridItem(
    file: VideoFile,
    onClick: (VideoFile) -> Unit,
    onDownload: (VideoFile) -> Unit,
    onDelete: (VideoFile) -> Unit
) {
    val context = LocalContext.current

    // Logic to handle thumbnail switching (copied from your FileItem logic)
    // You might want to extract this logic to a ViewModel or shared state
    var currentModel by remember { mutableStateOf<Any>(file.thumbnailUrl) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp) // Fixed height for grid uniformity
            .clickable { onClick(file) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // IMAGE AREA (Takes up most space)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentModel)
                        .crossfade(true)
                        .error(android.R.drawable.ic_menu_report_image)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .build(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (currentModel == file.thumbnailUrl) {
                            currentModel = file.downloadUrl
                        }
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )

                // Play Icon Overlay
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
            }

            // INFO AREA
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = Color.White
                )

                // ACTIONS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1
                    )

                    Row {
                        IconButton(
                            onClick = { onDownload(file) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Download, "Download", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDelete(file) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: VideoFile,
    onClick: (VideoFile) -> Unit,
    onDownload: (VideoFile) -> Unit,
    onDelete: (VideoFile) -> Unit
) {
    val context = LocalContext.current

    // Logic: If it's a thumbnail URL (.jpg), load it.
    // If we were forced to use the video URL, the video decoder would kick in.
    // For now, we rely on the .tb.jpg from the protocol.
    val imageRequest = ImageRequest.Builder(context)
        .data(file.thumbnailUrl)
        .crossfade(true)
        .error(android.R.drawable.ic_menu_report_image) // Fallback icon if thumbnail missing
        .placeholder(android.R.drawable.ic_menu_gallery) // Placeholder while loading
        .build()

    // NOTE: If you want to support video frame extraction for local files,
    // you must configure the ImageLoader in your MainActivity or passing it here.
    // But for remote dashcam files, extracting frames is too slow, so we stick to thumbnails.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(80.dp)
            .clickable { onClick(file) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // THUMBNAIL
            AsyncImage(
                model = imageRequest,
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(Color.Black)
            )

            // INFO
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = Color.White
                )
                Text(
                    text = "${file.size} • ${file.time}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // ACTIONS
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDownload(file) }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color(0xFF00E676)
                    )
                }
                IconButton(onClick = { onDelete(file) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF5350)
                    )
                }
            }
        }
    }
}