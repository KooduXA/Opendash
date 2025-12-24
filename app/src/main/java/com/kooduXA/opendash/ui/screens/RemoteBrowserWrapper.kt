package com.kooduXA.opendash.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kooduXA.opendash.R // Added import

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EnhancedRemoteBrowserWrapper(
    viewModel: DashboardViewModel,
    colors: DashboardColors,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val files by viewModel.recordings.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    // Listen for Feedback (Delete Success/Error)
    val dialogState by viewModel.dialogState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchRecordings()
    }

    // FEEDBACK TOASTS
    LaunchedEffect(dialogState) {
        if (dialogState is DialogState.Success) {
            Toast.makeText(context, (dialogState as DialogState.Success).message, Toast.LENGTH_SHORT).show()
        }
        if (dialogState is DialogState.Error) {
            Toast.makeText(context, (dialogState as DialogState.Error).message, Toast.LENGTH_LONG).show()
        }
    }

    // Show Download Feedback
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Success) {
            Toast.makeText(context, context.getString(R.string.remote_browser_download_complete), Toast.LENGTH_SHORT).show()
        }
        if (downloadState is DownloadState.Error) {
            Toast.makeText(context, context.getString(R.string.remote_browser_download_failed, (downloadState as DownloadState.Error).message), Toast.LENGTH_LONG).show()
        }
    }

    FileBrowserScreen(
        files = files,
        // PASS COLORS DOWN (You need to update FileBrowserScreen signature)
        // colors = colors,

        onFileClick = { file ->
            // PLAY VIDEO
            // We use an Intent to open a video player (either system or internal)
            playRemoteVideo(context, file.downloadUrl)
        },
        onDownloadClick = { file ->
            viewModel.downloadVideo(file)
        },
        onDeleteClick = { file ->
            viewModel.deleteFile(file)
        },
        onClose = onBack,

        // Pass download progress to UI
        downloadProgress = if (downloadState is DownloadState.Downloading) {
            (downloadState as DownloadState.Downloading).progress
        } else null
    )
}

private fun playRemoteVideo(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.remote_browser_no_video_player_found), Toast.LENGTH_SHORT).show()
    }
}