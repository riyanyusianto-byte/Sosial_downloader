package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.data.local.UserPreferences
import com.example.data.model.DownloadStatus
import com.example.data.model.ExtractedMediaInfo
import com.example.data.model.ExtractionUiState
import com.example.data.model.MediaPlatform
import com.example.data.model.VideoFormatOption
import com.example.data.remote.YouTubeBotCheckException
import com.example.data.remote.YouTubeCookieHelper
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = DownloadRepository(application, database.downloadDao())
    private val preferences = UserPreferences(application)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    val detectedPlatform: StateFlow<MediaPlatform> = _urlInput
        .map { text ->
            val extracted = MediaPlatform.extractFirstUrl(text)
            if (extracted == null) MediaPlatform.OTHER else MediaPlatform.detectFromUrl(extracted)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaPlatform.OTHER)

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    private val _selectedFormatId = MutableStateFlow<String?>(null)
    val selectedFormatId: StateFlow<String?> = _selectedFormatId.asStateFlow()

    private val _selectedFilter = MutableStateFlow<MediaPlatform?>(null)
    val selectedFilter: StateFlow<MediaPlatform?> = _selectedFilter.asStateFlow()

    private val _isYouTubeLoggedIn = MutableStateFlow(YouTubeCookieHelper.isLoggedIn())
    val isYouTubeLoggedIn: StateFlow<Boolean> = _isYouTubeLoggedIn.asStateFlow()

    private val allDownloads: StateFlow<List<DownloadEntity>> = repository.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDownloads: StateFlow<List<DownloadEntity>> =
        combine(allDownloads, _selectedFilter) { list, filter ->
            if (filter == null) list else list.filter { it.platformEnum() == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloadCount: StateFlow<Int> = allDownloads
        .map { list ->
            list.count {
                it.statusEnum() == DownloadStatus.DOWNLOADING ||
                    it.statusEnum() == DownloadStatus.QUEUED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDownloadCount: StateFlow<Int> = allDownloads
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isDarkTheme: StateFlow<Boolean> = preferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    fun refreshYouTubeAuthStatus() {
        _isYouTubeLoggedIn.value = YouTubeCookieHelper.isLoggedIn()
    }

    fun onYouTubeAuthSuccess() {
        refreshYouTubeAuthStatus()
        _snackbarEvents.tryEmit("Akun YouTube terhubung! Menganalisis ulang tautan…")
        if (_urlInput.value.isNotBlank()) {
            analyzeCurrentLink()
        }
    }

    fun logoutYouTube() {
        YouTubeCookieHelper.clearYouTubeCookies {
            _isYouTubeLoggedIn.value = false
            _snackbarEvents.tryEmit("Sesi YouTube telah keluar.")
        }
    }

    fun updateUrlInput(newUrl: String) {
        _urlInput.value = newUrl
        if (newUrl.isBlank() && _extractionState.value !is ExtractionUiState.Loading) {
            _extractionState.value = ExtractionUiState.Idle
            _selectedFormatId.value = null
        }
    }

    fun pasteUrlAndAnalyze(clipboardText: String?) {
        val cleaned = clipboardText?.let { MediaPlatform.extractFirstUrl(it) }
        if (cleaned.isNullOrBlank()) {
            _snackbarEvents.tryEmit("Clipboard kosong atau tidak berisi tautan.")
            return
        }
        _urlInput.value = cleaned
        analyzeCurrentLink()
    }

    fun handleIncomingSharedText(sharedText: String) {
        val cleaned = MediaPlatform.extractFirstUrl(sharedText) ?: return
        _urlInput.value = cleaned
        analyzeCurrentLink()
    }

    fun clearInput() {
        _urlInput.value = ""
        _extractionState.value = ExtractionUiState.Idle
        _selectedFormatId.value = null
    }

    fun analyzeCurrentLink() {
        val raw = _urlInput.value.trim()
        val url = MediaPlatform.extractFirstUrl(raw)
        if (url.isNullOrBlank()) {
            _extractionState.value = ExtractionUiState.Error(
                message = "Masukkan tautan video yang valid (dimulai dengan https://).",
                platform = MediaPlatform.OTHER
            )
            return
        }

        val platform = MediaPlatform.detectFromUrl(url)
        _extractionState.value = ExtractionUiState.Loading(platform)

        viewModelScope.launch {
            val result = repository.analyzeLink(url)
            result.fold(
                onSuccess = { mediaInfo ->
                    _selectedFormatId.value = mediaInfo.formats.firstOrNull()?.id
                    _extractionState.value = ExtractionUiState.Success(mediaInfo)
                },
                onFailure = { error ->
                    val needsAuth = error is YouTubeBotCheckException ||
                        (platform == MediaPlatform.YOUTUBE &&
                            (error.message?.contains("bot", ignoreCase = true) == true ||
                                error.message?.contains("Sign in", ignoreCase = true) == true))
                    _extractionState.value = ExtractionUiState.Error(
                        message = error.localizedMessage ?: "Gagal menganalisis tautan video.",
                        platform = platform,
                        requiresYouTubeAuth = needsAuth
                    )
                }
            )
        }
    }

    fun selectFormat(formatId: String) {
        _selectedFormatId.value = formatId
    }

    fun startSelectedDownload(onStarted: (() -> Unit)? = null) {
        val currentState = _extractionState.value as? ExtractionUiState.Success ?: return
        val media = currentState.media
        val chosen = media.formats.firstOrNull { it.id == _selectedFormatId.value }
            ?: media.formats.firstOrNull()
            ?: return

        startDownload(media, chosen, onStarted)
    }

    private fun startDownload(
        media: ExtractedMediaInfo,
        format: VideoFormatOption,
        onStarted: (() -> Unit)?
    ) {
        viewModelScope.launch {
            repository.startDownload(media, format)
            _snackbarEvents.tryEmit("Mengunduh ${media.platform.displayName} (${format.qualityBadge})…")
            onStarted?.invoke()
        }
    }

    fun setFilter(platform: MediaPlatform?) {
        _selectedFilter.value = platform
    }

    fun pauseDownload(id: Long) {
        repository.pauseDownload(id)
    }

    fun cancelDownload(id: Long) {
        repository.cancelDownload(id)
    }

    fun retryDownload(item: DownloadEntity) {
        repository.resumeOrRetryDownload(item)
    }

    fun deleteDownload(item: DownloadEntity) {
        viewModelScope.launch {
            repository.deleteDownload(item)
        }
    }

    fun clearCompletedHistory() {
        viewModelScope.launch {
            repository.clearCompletedHistory()
            _snackbarEvents.tryEmit("Riwayat unduhan selesai dibersihkan.")
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            preferences.setDarkTheme(!isDarkTheme.value)
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }
}
