package com.kooduXA.opendash.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kooduXA.opendash.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.kooduXA.opendash.data.repository.ConnectionRepository
import com.kooduXA.opendash.domain.model.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val repository: SettingsRepository,
    private val connectionRepository: ConnectionRepository
) : AndroidViewModel(application) {

    val settings = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.AppSettings() // Default values
        )

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo = _storageInfo.asStateFlow()

    private val _isFetchingStorageInfo = MutableStateFlow(false)
    val isFetchingStorageInfo = _isFetchingStorageInfo.asStateFlow()

    private val _formatResult = MutableStateFlow<Boolean?>(null)
    val formatResult = _formatResult.asStateFlow()

//    private val repository = SettingsRepository(application)
//
    // State for settings
    private val _settingsState = MutableStateFlow(SettingsRepository.AppSettings())
    val settingsState: StateFlow<SettingsRepository.AppSettings> = _settingsState

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        // Load settings when ViewModel is created
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _settingsState.value = settings
            }
        }
    }

    fun updateSetting(key: String, value: Any) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (key) {
                    "nightMode" -> repository.setNightMode(value as Boolean)
                    "videoResolution" -> repository.setVideoResolution(value as String)
                    "loopRecording" -> repository.setLoopRecording(value as Boolean)
                    "loopDuration" -> repository.setLoopDuration(value as Int)
                    "audioRecording" -> repository.setAudioRecording(value as Boolean)
                    "impactDetection" -> repository.setImpactDetection(value as Boolean)
                    "motionDetection" -> repository.setMotionDetection(value as Boolean)
                    // Add more cases as needed
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.resetToDefaults()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAllSettings(settings: SettingsRepository.AppSettings) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateSettings(settings)
            } finally {
                _isLoading.value = false
            }
        }
    }




    fun fetchStorageInfo() {
        viewModelScope.launch {
            _isFetchingStorageInfo.value = true
            _storageInfo.value = null
            _storageInfo.value = connectionRepository.getStorageInfo()
            _isFetchingStorageInfo.value = false
        }
    }

    fun formatSdCard() {
        viewModelScope.launch {
            val success = connectionRepository.formatSdCard()
            _formatResult.value = success
        }
    }

    fun clearFormatResult() {
        _formatResult.value = null
    }


    fun updateResolution(res: String) {
        viewModelScope.launch { repository.updateSettings(settings.value.copy(videoResolution = res)) }
    }

    fun toggleLoopRecording(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings(settings.value.copy(loopRecording = enabled)) }
    }

    fun toggleAudio(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings(settings.value.copy(audioRecording = enabled)) }
    }



    fun toggleAutoConnect(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings(settings.value.copy(wifiAutoConnect = enabled)) }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        // Example if you had app-specific UI settings
        // viewModelScope.launch { repository.updateSettings(settings.value.copy(nightMode = enabled)) }
    }

    // Reset to defaults
    fun resetSettings() {
        viewModelScope.launch {
            repository.updateSettings(SettingsRepository.AppSettings()) // Resets to default data class values
        }
    }

    fun updateWifi(ssid: String, pass: String) {
        viewModelScope.launch {
            // 1. Save to local preferences (so we remember it next time)
            repository.updateSettings(settings.value.copy(
                wifiSSID = ssid,
                wifiPassword = pass
            ))

            // 2. Send to Camera
            // We need to access ConnectionRepository here.
            // *Note: Ensure you inject ConnectionRepository into this ViewModel constructor.*
            val success = connectionRepository.updateWifi(ssid, pass)

            if (success) {
                // Ideally, show a Toast here saying "Camera restarting..."
                // The connection will drop immediately after this.
            }
        }
    }
}