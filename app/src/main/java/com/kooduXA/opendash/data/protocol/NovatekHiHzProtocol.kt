package com.kooduXA.opendash.data.protocol

import android.util.Log
import com.kooduXA.opendash.domain.model.CameraState
import com.kooduXA.opendash.domain.model.VideoFile
//import com.google.android.gms.appsearch.StorageInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit
import com.kooduXA.opendash.domain.model.StorageInfo

class NovatekHiHzProtocol : CameraProtocol {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()



    private val _connectionState = MutableStateFlow<CameraState>(CameraState.Disconnected)
    override val connectionState: StateFlow<CameraState> = _connectionState

    private var cameraIp: String = "192.168.0.1"
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        cameraIp = ipAddress
        _connectionState.value = CameraState.Connecting

        try {
            // PHASE A: WAKE UP SEQUENCE
            val commands = listOf(
                "action=set&property=Net&value=connect",
                "action=set&property=MovieLive&value=1",
                "action=play&property=Live&value=1" // Critical trigger
            )

            commands.forEach { query ->
                sendCgiCommand(query)
                delay(150)
            }

            _connectionState.value = CameraState.Connected
            startHeartbeat() // Start Phase C immediately
            return@withContext true

        } catch (e: Exception) {
            Log.e("Novatek", "Connection failed", e)
            _connectionState.value = CameraState.Error("Handshake Failed")
            return@withContext false
        }
    }

    override suspend fun getLiveStreamUrl(): String = withContext(Dispatchers.IO) {
        // PHASE B: DYNAMIC DISCOVERY
        // We ask the camera which AV channel is active.
        val avResponse = sendCgiCommand("action=get&property=Camera.Preview.RTSP.av")

        // Parse "Camera.Preview.RTSP.av=4" -> 4
        val avValue = avResponse?.substringAfter("av=")?.trim()?.toIntOrNull() ?: 1

        val path = when (avValue) {
            1 -> "av1"
            2 -> "v1"
            3 -> "av2"
            4 -> "av4" // This matched your device
            else -> "av1"
        }

        // Return standard RTSP format
        return@withContext "rtsp://$cameraIp/liveRTSP/$path"
    }

    override suspend fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    // PHASE C: HEARTBEAT
                    val code = sendCgiCommand("action=get&property=hbt&value=playback", returnCode = true)
                    if (code != "200") {
                        Log.w("Novatek", "Heartbeat missed: $code")
                    }
                } catch (e: Exception) {
                    Log.e("Novatek", "Heartbeat failed", e)
                }
                delay(3000) // 3 seconds interval
            }
        }
    }



//    override suspend fun takePhoto(): Boolean {
//        // Placeholder: Command usually involves 'action=set&property=Camera.Capture&value=1'
//        return false
//    }

    override fun disconnect() {
        heartbeatJob?.cancel()
        _connectionState.value = CameraState.Disconnected
    }

    // --- Helper for HTTP Commands ---
    private fun sendCgiCommand(query: String, returnCode: Boolean = false): String? {
        val url = "http://$cameraIp/cgi-bin/Config.cgi?$query"
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (returnCode) return response.code.toString()
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendCommandAndGetResponse(commandCode: Int): Response {
        val url = "http://$cameraIp/cgi-bin/ipc"
        // Note: Check if your URL construction needs ? or & correctly
        val request = Request.Builder().url("$url?-function=exec&-command=$commandCode").build()
        return client.newCall(request).execute()
    }

    override suspend fun getFileList(): List<VideoFile> = withContext(Dispatchers.IO) {
        // Standard Novatek command to list files in the "Normal" (Driving) folder
        val xmlResponse = sendCgiCommand("action=dir&property=Normal&format=all&count=100&from=0")

        if (xmlResponse.isNullOrEmpty()) return@withContext emptyList()

        return@withContext parseFileListXml(xmlResponse)
    }

    private fun parseFileListXml(xml: String): List<VideoFile> {
        val files = mutableListOf<VideoFile>()
        try {
            Log.d("Novatek", "Parsing XML Response: $xml") // Debug Log 1

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var rawName: String? = null
            var currentSize: String? = null
            var currentTime: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "name" -> rawName = parser.nextText()
                            "size" -> currentSize = parser.nextText()
                            "time" -> currentTime = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "file" && rawName != null) {

                            // --- PATH CLEANING LOGIC ---
                            // 1. Determine the relative path from root
                            // If rawName is "/EMMC/Normal/F/Video.mp4", keep it.
                            // If rawName is "Video.mp4", prepend "/SD/Normal/".
                            val relativePath = if (rawName.startsWith("/")) {
                                rawName
                            } else {
                                "/SD/Normal/$rawName"
                            }

                            // 2. CONSTRUCT VIDEO URL
                            val fullUrl = "http://$cameraIp$relativePath"

                            // 3. CONSTRUCT THUMBNAIL URL
                            // Target Format: /EMMC/Normal/F/.20250203_tb.jpg
                            val directory = relativePath.substringBeforeLast("/") + "/"
                            val filename = relativePath.substringAfterLast("/")

                            // Handle cases where filename has no extension
                            val nameNoExt = if (filename.contains(".")) {
                                filename.substringBeforeLast(".")
                            } else {
                                filename
                            }

                            // Construct the hidden thumbnail name
                            val thumbFilename = ".$nameNoExt" + "_tb.jpg"
                            val thumbPath = directory + thumbFilename
                            val thumbUrl = "http://$cameraIp$thumbPath"

                            // 4. PRETTY DISPLAY NAME
                            val displayName = filename

                            Log.d("Novatek", "Found File: $displayName | URL: $fullUrl | Thumb: $thumbUrl")

                            files.add(VideoFile(
                                filename = displayName,
                                downloadUrl = fullUrl,
                                thumbnailUrl = thumbUrl,
                                size = currentSize ?: "Unknown",
                                time = currentTime ?: ""
                            ))

                            // Reset for next item
                            rawName = null
                            currentSize = null
                            currentTime = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("Novatek", "XML Parse Error", e)
        }

        Log.d("Novatek", "Total Files Parsed: ${files.size}")
        return files
    }

    override suspend fun deleteFile(filename: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("Novatek", "Request to delete: $filename")

        var finalPath = filename

        // 1. If the input is a full URL (http://...), strip the HTTP part
        if (finalPath.startsWith("http")) {
            finalPath = finalPath.replace("http://$cameraIp", "")
        }

        // 2. Convert standard slashes to Dollars (Standard Novatek Logic)
        finalPath = finalPath.replace("/", "$")

        // 3. CRITICAL FIX: If the path doesn't start with '$', it means we only have the filename.
        // We MUST prepend the folder path found in the working logs ($EMMC$Normal$F$)
        if (!finalPath.startsWith("$")) {
            // FIX: Use backslash '\' to tell Kotlin these are real Dollar signs, not variables
            finalPath = "\$EMMC\$Normal\$F\$$finalPath"
        }

        Log.d("Novatek", "Sending Delete Command: property=$finalPath")

        val result = sendCgiCommand("action=del&property=$finalPath")

        val success = isSuccess(result)

        if (success) {
            Log.d("Novatek", "Delete Success")
        } else {
            Log.e("Novatek", "Delete Failed. Response: $result")
        }

        return@withContext success
    }




    override suspend fun formatSdCard(): Boolean {
        val response = sendCommandAndGetResponse(3015)
        return if (response.isSuccessful && response.body != null) {
            val xml = response.body!!.string()
            xml.contains("<Status>0</Status>")
        } else {
            false
        }
    }

    suspend fun setAudioRecording(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val value = if (enabled) "On" else "Off"
        // Try standard Novatek command for audio
        val result = sendCgiCommand("action=set&property=SoundRecord&value=$value")
        return@withContext result?.contains("OK") == true
    }

    suspend fun getDeviceStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        var isRecording = false
        var hasSdCard = true // Default to TRUE to prevent UI blocking bugs

        try {
            // 1. Check Recording (Using the property from your working logs)
            val recResponse = sendCgiCommand("action=get&property=Camera.Preview.MJPEG.status.record")
            if (recResponse?.contains("Recording", ignoreCase = true) == true) {
                isRecording = true
            }

            // 2. Check SD Card (Using the property from your working logs)
            val sdResponse = sendCgiCommand("action=get&property=Camera.Menu.SD0")
            // If it explicitly says "Insert_SD" or "Card_Error", then we mark it false.
            // Otherwise, assume it's fine.
            if (sdResponse?.contains("Insert", ignoreCase = true) == true ||
                sdResponse?.contains("Error", ignoreCase = true) == true) {
                hasSdCard = false
            }

            // Log the status for debugging
            // Log.d("Novatek", "Status Poll -> Rec: $isRecording, SD: $hasSdCard")

        } catch (e: Exception) {
            Log.e("Novatek", "Status Poll Failed", e)
        }

        return@withContext DeviceStatus(
            isRecording = isRecording,
            hasSdCard = hasSdCard
        )
    }



    // ... existing connect/heartbeat ...

    // UPDATE: getStorageInfo to be more robust
    override suspend fun getStorageInfo(): StorageInfo? = withContext(Dispatchers.IO) {
        val response = sendCgiCommand("action=get&property=SDCard.Capacity")
        // Expected format: 3015\n0\n[Total]\n[Free]
        // Or sometimes XML

        if (response != null) {
            try {
                // Try XML parsing first (as per your previous code)
                // ... (keep your existing XML parser logic) ...

                // Fallback for Plain Text responses (Common in Novatek)
                val lines = response.lines()
                if (lines.size >= 3) {
                    val total = lines.find { it.toLongOrNull() != null && it.toLong() > 1000 }?.toLong() ?: 0
                    // Find the next number as free
                    return@withContext StorageInfo(total * 1024 * 1024, (total/2) * 1024 * 1024) // Simplified
                }
            } catch (e: Exception) {
                return@withContext null
            }
        }
        return@withContext null
    }


override suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
    Log.d("Novatek", "Sending Rec Toggle (Start)...")

    // This is the Toggle Command found in your logs
    val result = sendCgiCommand("action=set&property=Video&value=record")

    Log.d("Novatek", "Rec Toggle Response: '$result'")
    return@withContext isSuccess(result)
}

    // FIX: Add 'withContext(Dispatchers.IO)'
    override suspend fun stopRecording(): Boolean = withContext(Dispatchers.IO) {
        Log.d("Novatek", "Sending Rec Toggle (Stop)...")

        val result = sendCgiCommand("action=set&property=Video&value=record")

        Log.d("Novatek", "Rec Toggle Response: '$result'")
        return@withContext isSuccess(result)
    }

    // FIX: Add 'withContext(Dispatchers.IO)'
    override suspend fun takePhoto(): Boolean = withContext(Dispatchers.IO) {
        Log.d("Novatek", "Taking Photo...")

        val result = sendCgiCommand("action=set&property=Camera.Capture&value=1")

        Log.d("Novatek", "Photo Response: '$result'")
        return@withContext isSuccess(result)
    }

    // --- Helper to debug why sendCgiCommand might fail ---
    private fun sendCgiCommand(query: String): String? {
        val url = "http://$cameraIp/cgi-bin/Config.cgi?$query"
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else {
                    Log.w("Novatek", "HTTP Error: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            // THIS LOG IS CRITICAL to seeing the NetworkOnMainThreadException
            Log.e("Novatek", "CGI Request Failed: $url", e)
            null
        }
    }

    private fun isSuccess(response: String?): Boolean {
        if (response == null) return false
        val clean = response.trim()
        // Accept "OK", "0", or "0\nOK"
        return clean.contains("OK", ignoreCase = true) || clean.startsWith("0")
    }

    suspend fun setWifiCredentials(ssid: String, password: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("Novatek", "Starting WiFi Update Sequence...")

        // 1. Set SSID (Standard Novatek Property)
        val ssidCmd = "action=set&property=Net.WIFI_AP.SSID&value=$ssid"
        val ssidResult = sendCgiCommand(ssidCmd)
        if (!isSuccess(ssidResult)) {
            Log.e("Novatek", "Failed to set SSID")
            return@withContext false
        }

        // 2. Set Password (Property found in your logs: Net.WIFI_AP.CryptoKey)
        val passCmd = "action=set&property=Net.WIFI_AP.CryptoKey&value=$password"
        val passResult = sendCgiCommand(passCmd)
        if (!isSuccess(passResult)) {
            Log.e("Novatek", "Failed to set Password")
            return@withContext false
        }

        // 3. THE MAGIC RESET COMMAND (Found in your logs)
        // This tells the camera to apply the new settings and restart the WiFi interface.
        // URL: ...action=set&property=Net.Dev.1.Type&value=AP&property=Net&value=reset
        val resetCmd = "action=set&property=Net.Dev.1.Type&value=AP&property=Net&value=reset"
        val resetResult = sendCgiCommand(resetCmd)

        Log.d("Novatek", "WiFi Update Sequence Complete. Reset Result: $resetResult")

        return@withContext isSuccess(resetResult)
    }

}


// Add this Data Class to the bottom of the file or in models
data class DeviceStatus(
    val isRecording: Boolean,
    val hasSdCard: Boolean
)

