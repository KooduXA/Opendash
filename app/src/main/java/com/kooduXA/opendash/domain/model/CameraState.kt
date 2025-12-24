package com.kooduXA.opendash.domain.model

sealed class CameraState {
    // 1. Initial state before anything happens
    object Disconnected : CameraState()

    // 2. We found the Wi-Fi and are trying to shake hands
    object Scanning : CameraState()
    object Connecting : CameraState()

    // 3. Success! We are connected and ready to stream
    object Connected : CameraState()

    // 4. Something went wrong (e.g., "Authentication Failed", "Wifi lost")
    data class Error(val message: String) : CameraState()
}