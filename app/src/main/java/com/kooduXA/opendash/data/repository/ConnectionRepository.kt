package com.kooduXA.opendash.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.kooduXA.opendash.data.protocol.CameraProtocol
import com.kooduXA.opendash.data.protocol.NovatekHiHzProtocol
import com.kooduXA.opendash.domain.model.CameraState
import com.kooduXA.opendash.domain.model.VideoFile
import com.kooduXA.opendash.domain.model.WifiInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import com.kooduXA.opendash.data.protocol.DeviceStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val _connectionState = MutableStateFlow<CameraState>(CameraState.Disconnected)
    val connectionState: StateFlow<CameraState> = _connectionState.asStateFlow()

    private val _activeProtocol = MutableStateFlow<CameraProtocol?>(null)
    val activeProtocol: StateFlow<CameraProtocol?> = _activeProtocol.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- FIX 1: DEFINE THE OKHTTPCLIENT ---
    private val downloadClient = OkHttpClient()

    /**
     * Main entry point: Scans network and tries to connect to the camera.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun startDiscovery() {
        scope.launch {
            _connectionState.value = CameraState.Scanning

            val wifiInfo = getGatewayInfo()
            if (wifiInfo == null) {
                _connectionState.value = CameraState.Error("No Wi-Fi Connection Found")
                return@launch
            }

            Log.d("Repo", "Gateway found: ${wifiInfo.gatewayIp}")

            // FACTORY LOGIC:
            // In the future, we will try multiple drivers here (e.g., check port 80 vs 3333).
            // For now, we hardcode the Novatek driver we just built.
            val protocol = NovatekHiHzProtocol()

            // --- THE FIX IS HERE ---
            if (protocol.connect(wifiInfo.gatewayIp)) {
                _activeProtocol.value = protocol
                // Explicitly update the state so the UI knows to switch screens!
                _connectionState.value = CameraState.Connected
            } else {
                _connectionState.value = CameraState.Error("Camera Handshake Failed")
            }
        }
    }

    fun disconnect() {
        _activeProtocol.value?.disconnect()
        _activeProtocol.value = null
        _connectionState.value = CameraState.Disconnected
    }

    /**
     * Android 12+ compatible way to get the Gateway IP.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getGatewayInfo(): WifiInfo? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network = connectivityManager.activeNetwork ?: return null
        val caps: NetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

        // Find the gateway (router) IP
        val gateway = linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        // Fallback for some older Android versions or specific setups
            ?: linkProperties.dhcpServerAddress?.hostAddress

        return if (gateway != null) {
            WifiInfo(ssid = "Unknown", gatewayIp = gateway)
        } else {
            null
        }
    }

    suspend fun getRecordings(): List<VideoFile> {
        return _activeProtocol.value?.getFileList() ?: emptyList()
    }


    suspend fun downloadFile(
        url: String,
        filename: String,
        onProgress: (Float) -> Unit // <--- NEW CALLBACK
    ) {
        withContext(Dispatchers.IO) { // Use withContext instead of launching a new scope
            try {
                Log.d("Repo", "Starting direct download for: $filename")

                // 1. Create an entry in the Phone's Gallery (MediaStore)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    // Stores in "Movies/OpenDash"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OpenDash")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        val request = Request.Builder().url(url).build()
                        val response = downloadClient.newCall(request).execute()

                        if (!response.isSuccessful) return@use

                        val body = response.body ?: return@use
                        val totalBytes = body.contentLength()
                        var bytesCopied: Long = 0
                        val buffer = ByteArray(8 * 1024)
                        val inputStream = body.byteStream()

                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytesCopied += bytes

                            // REPORT PROGRESS
                            if (totalBytes > 0) {
                                val progress = bytesCopied.toFloat() / totalBytes.toFloat()
                                onProgress(progress)
                            }

                            bytes = inputStream.read(buffer)
                        }
                    }

                    Log.d("Repo", "Download SUCCESS: Saved to $uri")
                } else {
                    Log.e("Repo", "Failed to create MediaStore entry")
                }

            } catch (e: Exception) {
                Log.e("Repo", "Download Exception", e)
            }
        }
    }

    suspend fun getStorageInfo() = activeProtocol.value?.getStorageInfo()

    suspend fun formatSdCard() = activeProtocol.value?.formatSdCard() ?: false

    suspend fun setAudioRecording(enabled: Boolean): Boolean {
        return (activeProtocol.value as? NovatekHiHzProtocol)?.setAudioRecording(enabled) ?: false
    }

    suspend fun getDeviceStatus(): DeviceStatus {
        return (activeProtocol.value as? NovatekHiHzProtocol)?.getDeviceStatus()
            ?: DeviceStatus(isRecording = false, hasSdCard = false)
    }

    suspend fun updateWifi(ssid: String, pass: String): Boolean {
        return (activeProtocol.value as? NovatekHiHzProtocol)?.setWifiCredentials(ssid, pass) ?: false
    }

}