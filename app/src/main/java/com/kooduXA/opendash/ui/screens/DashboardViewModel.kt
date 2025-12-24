package com.kooduXA.opendash.ui.screens

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kooduXA.opendash.data.repository.ConnectionRepository
import com.kooduXA.opendash.data.repository.SettingsRepository
import com.kooduXA.opendash.domain.model.CameraState
import com.kooduXA.opendash.domain.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

@HiltViewModel
@RequiresApi(Build.VERSION_CODES.R)
class DashboardViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // --- NEW STATES ---

    private var lastCommandTime: Long = 0
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled = _isAudioEnabled.asStateFlow()

    private val _hasSdCard = MutableStateFlow(true) // Default to true until checked
    val hasSdCard = _hasSdCard.asStateFlow()

    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState = _dialogState.asStateFlow()

    // Recording Timer
    private var recordingStartTimestamp: Long = 0
    private val _recordingDuration = MutableStateFlow("00:00")
    val recordingDuration = _recordingDuration.asStateFlow()



    // UI State
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Idle)
    val uiState: StateFlow<DashboardUiState> = _uiState

    // Download State
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    // Recordings
    private val _recordings = MutableStateFlow<List<VideoFile>>(emptyList())
    val recordings: StateFlow<List<VideoFile>> = _recordings

    // Settings
    val settings = settingsRepository.settingsFlow

    init {
        // Observe Connection State
        viewModelScope.launch {
            connectionRepository.connectionState.collectLatest { state ->
                when (state) {
                    is CameraState.Connected -> {
                        val url = connectionRepository.activeProtocol.value?.getLiveStreamUrl() ?: ""
                        _uiState.value = DashboardUiState.Streaming(url)
                    }
                    is CameraState.Error -> {
                        _uiState.value = DashboardUiState.Error(state.message)
                    }
                    is CameraState.Scanning -> {
                        _uiState.value = DashboardUiState.Loading
                    }
                    else -> {
                        _uiState.value = DashboardUiState.Idle
                    }
                }
            }
        }

        // Auto-connect if enabled
        viewModelScope.launch {
            settings.collect { settings ->
                if (settings.wifiAutoConnect && _uiState.value is DashboardUiState.Idle) {
                    connect()
                }
            }
        }
    }

    init {
        // Start a Status Polling Loop when connected
        viewModelScope.launch {
            connectionRepository.connectionState.collectLatest { state ->
                if (state is CameraState.Connected) {
                    startStatusPolling()
                }
            }
        }
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                // Fetch status from Protocol
                val status = connectionRepository.getDeviceStatus()

                _hasSdCard.value = status.hasSdCard

                // Sync Recording State
                if (status.isRecording && !_isRecording.value) {
                    _isRecording.value = true
                    recordingStartTimestamp = System.currentTimeMillis() // Approximate
                } else if (!status.isRecording && _isRecording.value) {
                    _isRecording.value = false
                }

                // Update Timer String
                if (_isRecording.value) {
                    val seconds = (System.currentTimeMillis() - recordingStartTimestamp) / 1000
                    val m = seconds / 60
                    val s = seconds % 60
                    _recordingDuration.value = "%02d:%02d".format(m, s)
                }

                delay(2000) // Poll every 2 seconds
            }
        }
    }



    fun toggleRecording() {
        Log.d("DashboardViewModel", "Record Button Clicked! Verifying state first...")

        // 1. SILENCE POLLING LOOP IMMEDIATELY
        // This prevents the background loop from overwriting your UI while we are thinking.
        lastCommandTime = System.currentTimeMillis()

        viewModelScope.launch {
            // 2. FETCH TRUE STATUS
            val status = connectionRepository.getDeviceStatus()
            val cameraIsRecording = status?.isRecording ?: false
            val appThinksRecording = _isRecording.value

            Log.d("DashboardViewModel", "Logic Check -> App: $appThinksRecording | Camera: $cameraIsRecording")

            // 3. DETERMINE INTENT (Smart Logic)
            if (appThinksRecording) {
                // APP SAYS RECORDING...
                if (!cameraIsRecording) {
                    // But Camera is Stopped? -> Just Sync UI.
                    Log.d("DashboardViewModel", "Sync Fix: Camera already stopped. Updating UI only.")
                    _isRecording.value = false
                    recordingStartTimestamp = 0
                } else {
                    // Camera is also Recording -> Send Stop.
                    Log.d("DashboardViewModel", "Action: Sending STOP command.")
                    val success = connectionRepository.activeProtocol.value?.stopRecording() ?: false
                    if (success) {
                        _isRecording.value = false
                        recordingStartTimestamp = 0
                        _dialogState.value = DialogState.Success("Recording Saved")
                        // Short delay to let user see the message
                        delay(500)
                        _dialogState.value = DialogState.None
                    }
                }
            } else {
                // APP SAYS STOPPED...
                if (cameraIsRecording) {
                    // But Camera is Recording? -> Just Sync UI.
                    Log.d("DashboardViewModel", "Sync Fix: Camera already recording. Updating UI only.")
                    _isRecording.value = true
                    if (recordingStartTimestamp == 0L) recordingStartTimestamp = System.currentTimeMillis()
                } else {
                    // Camera is also Stopped -> Send Start.
                    Log.d("DashboardViewModel", "Action: Sending START command.")
                    val success = connectionRepository.activeProtocol.value?.startRecording() ?: false
                    if (success) {
                        _isRecording.value = true
                        recordingStartTimestamp = System.currentTimeMillis()
                        _dialogState.value = DialogState.Success("Recording Started")
                        delay(500)
                        _dialogState.value = DialogState.None
                    } else {
                        _dialogState.value = DialogState.Error("Failed to start recording")
                    }
                }
            }

            // 4. RESET SILENCE TIMER
            // Reset the timer NOW so the polling loop stays quiet for another 3 seconds
            // allowing the camera firmware to fully process the command.
            lastCommandTime = System.currentTimeMillis()
        }
    }

    fun toggleAudio() {
        viewModelScope.launch {
            val newState = !_isAudioEnabled.value
            val success = connectionRepository.setAudioRecording(newState)
            if (success) {
                _isAudioEnabled.value = newState
            }
        }
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.None
    }


    @RequiresApi(Build.VERSION_CODES.R)
    fun connect() {
        connectionRepository.startDiscovery()
    }

    fun disconnect() {
        connectionRepository.disconnect()
    }

    fun fetchRecordings() {
        viewModelScope.launch {
            val files = connectionRepository.getRecordings()
            _recordings.value = files
        }
    }

    fun downloadVideo(file: VideoFile) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0f)

            connectionRepository.downloadFile(file.downloadUrl, file.filename) { progress ->
                _downloadState.value = DownloadState.Downloading(progress)
            }

            _downloadState.value = DownloadState.Success(file.filename)
            delay(3000)
            _downloadState.value = DownloadState.Idle
        }
    }

    fun deleteFile(file: VideoFile) {
        viewModelScope.launch {
            // 1. Show Loading (Optional, usually fast enough to skip)

            // 2. Perform Delete
            // Remember: The protocol now handles the "$" path logic automatically
            val success = connectionRepository.activeProtocol.value?.deleteFile(file.filename) ?: false

            if (success) {
                // 3. Refresh List
                fetchRecordings()

                // 4. Send Feedback
                _dialogState.value = DialogState.Success("File deleted successfully")

                // Clear feedback after 2 seconds
                delay(2000)
                _dialogState.value = DialogState.None
            } else {
                _dialogState.value = DialogState.Error("Failed to delete file")
            }
        }
    }

    fun takePhoto() {
        viewModelScope.launch {
            val success = connectionRepository.activeProtocol.value?.takePhoto() ?: false
            // Handle photo taken feedback
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            connectionRepository.activeProtocol.value?.startRecording()
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            connectionRepository.activeProtocol.value?.stopRecording()
        }
    }

    private val _localVideos = MutableStateFlow<List<VideoFile>>(emptyList())
    val localVideos = _localVideos.asStateFlow()

    fun loadLocalGallery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoList = mutableListOf<VideoFile>()

            // Query MediaStore for videos
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )

            // Sort by Date Descending (Newest first)
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null, // Select all (or filter by specific folder if needed)
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val date = cursor.getLong(dateColumn) * 1000L // Convert to ms

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    // Filter: Only show videos that look like Dashcam files (optional)
                    // if (name.endsWith(".mp4", true)) {
                    videoList.add(
                        VideoFile(
                            filename = name,
                            downloadUrl = contentUri.toString(), // Local URI
                            thumbnailUrl = contentUri.toString(), // Coil loads thumb from URI
                            size = formatSize(size),
                            time = formatDate(date)
                        )
                    )
                    // }
                }
            }
            _localVideos.value = videoList
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return "${mb}MB"
    }

    private fun formatDate(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

sealed class DashboardUiState {
    object Idle : DashboardUiState()
    object Loading : DashboardUiState()
    data class Streaming(val url: String) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Success(val filename: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// UI State for Dialogs
sealed class DialogState {
    object None : DialogState()
    data class Loading(val message: String) : DialogState()
    data class Success(val message: String) : DialogState()
    data class Error(val message: String) : DialogState()
}
