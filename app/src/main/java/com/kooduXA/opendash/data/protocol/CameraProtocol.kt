package com.kooduXA.opendash.data.protocol

import com.kooduXA.opendash.domain.model.CameraState
import com.kooduXA.opendash.domain.model.StorageInfo
import com.kooduXA.opendash.domain.model.VideoFile
import kotlinx.coroutines.flow.StateFlow

interface CameraProtocol {
    /**
     * A flow that emits the current state of the camera (Connected, Recording, Error, etc.)
     */
    val connectionState: StateFlow<CameraState>

    /**
     * Tries to handshake with the camera. Returns true if successful.
     * This handles the "Wake Up" sequence (Phase A).
     */
    suspend fun connect(ipAddress: String): Boolean

    /**
     * Returns the RTSP URL for the video player.
     * This handles the "Dynamic Discovery" (Phase B).
     */
    suspend fun getLiveStreamUrl(): String

    /**
     * Starts the Keep-Alive loop (Phase C).
     * Should be called automatically after a successful connection.
     */
    suspend fun startHeartbeat()

    // --- Commands ---
    suspend fun startRecording(): Boolean
    suspend fun stopRecording(): Boolean
    suspend fun takePhoto(): Boolean

    // --- Cleanup ---
    fun disconnect()

    suspend fun getFileList(): List<VideoFile>

    /**
     * Deletes a specific file from the camera.
     * @param filename The simple filename (e.g., "20230101_120000.MP4")
     * @return True if the camera responded with OK.
     */
    suspend fun deleteFile(filename: String): Boolean

    suspend fun getStorageInfo(): StorageInfo?

    suspend fun formatSdCard(): Boolean
}