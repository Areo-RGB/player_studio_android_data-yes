package com.playwe.playlist.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playwe.playlist.data.VideoRepository
import com.playwe.playlist.model.Chapter
import com.playwe.playlist.model.Playlist
import com.playwe.playlist.model.PlaylistType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED
}

data class TimerUiState(
    val isTimerDialogOpen: Boolean = false,
    val timerMinutes: Int = 1,
    val timerSeconds: Int = 30,
    val timerRemainingSeconds: Int = 90,
    val timerState: TimerState = TimerState.IDLE
)

data class PlayerUiState(
    val playlists: List<Playlist> = emptyList(),
    val activePlaylistIndex: Int = 0,
    val activeChapterIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val isSlowMotion: Boolean = false,
    val currentTimeSeconds: Float = 0f,
    val isPlaylistSidebarOpen: Boolean = false,
    val isChapterSidebarOpen: Boolean = false,
    val directSourceMode: String = "online", // "online" or "local"
    val isFullscreen: Boolean = false
) {
    val activePlaylist: Playlist?
        get() = playlists.getOrNull(activePlaylistIndex)

    val chapters: List<Chapter>
        get() = activePlaylist?.chapters ?: emptyList()

    val activeChapter: Chapter?
        get() = chapters.getOrNull(activeChapterIndex)

    val isDirectVideo: Boolean
        get() {
            val pl = activePlaylist ?: return false
            return pl.type == PlaylistType.DIRECT || pl.chapters.any { it.videoUrl != null }
        }

    val playbackSpeed: Float
        get() = if (isSlowMotion) 0.25f else 1.0f
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _timerUiState = MutableStateFlow(TimerUiState())
    val timerUiState: StateFlow<TimerUiState> = _timerUiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = VideoRepository.fetchPlaylistsWithLocal(appContext)
            result.onSuccess { fetchedList ->
                _uiState.update {
                    it.copy(
                        playlists = fetchedList,
                        activePlaylistIndex = 0,
                        activeChapterIndex = 0,
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = err.message ?: "Failed to load playlists"
                    )
                }
            }
        }
    }

    fun selectLocalFolder(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some document providers grant only a temporary read permission.
        }
        VideoRepository.saveLocalFolderUri(appContext, uri)
        loadPlaylists()
    }

    fun hasLocalFolder(): Boolean {
        return VideoRepository.getSavedLocalFolderUri(appContext) != null
    }

    fun selectPlaylist(index: Int) {
        if (index in _uiState.value.playlists.indices) {
            _uiState.update {
                it.copy(
                    activePlaylistIndex = index,
                    activeChapterIndex = 0,
                    isPlaying = false,
                    currentTimeSeconds = 0f,
                    isPlaylistSidebarOpen = false
                )
            }
        }
    }

    fun selectChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index in chapters.indices) {
            _uiState.update {
                it.copy(
                    activeChapterIndex = index,
                    isPlaying = true,
                    currentTimeSeconds = chapters[index].startSeconds.toFloat(),
                    isChapterSidebarOpen = false
                )
            }
        }
    }

    fun nextChapter() {
        val state = _uiState.value
        if (state.activeChapterIndex < state.chapters.size - 1) {
            selectChapter(state.activeChapterIndex + 1)
        }
    }

    fun prevChapter() {
        val state = _uiState.value
        if (state.activeChapterIndex > 0) {
            selectChapter(state.activeChapterIndex - 1)
        }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun toggleLooping() {
        _uiState.update { it.copy(isLooping = !it.isLooping) }
    }

    fun toggleSlowMotion() {
        _uiState.update { it.copy(isSlowMotion = !it.isSlowMotion) }
    }

    fun updateCurrentTime(seconds: Float) {
        _uiState.update { it.copy(currentTimeSeconds = seconds) }
    }

    fun togglePlaylistSidebar() {
        _uiState.update {
            val willOpen = !it.isPlaylistSidebarOpen
            it.copy(
                isPlaylistSidebarOpen = willOpen,
                isChapterSidebarOpen = if (willOpen) false else it.isChapterSidebarOpen
            )
        }
    }

    fun toggleChapterSidebar() {
        _uiState.update {
            val willOpen = !it.isChapterSidebarOpen
            it.copy(
                isChapterSidebarOpen = willOpen,
                isPlaylistSidebarOpen = if (willOpen) false else it.isPlaylistSidebarOpen
            )
        }
    }

    fun closeSidebars() {
        _uiState.update {
            it.copy(
                isPlaylistSidebarOpen = false,
                isChapterSidebarOpen = false
            )
        }
    }

    fun onChapterEnd() {
        val state = _uiState.value
        if (state.activeChapterIndex < state.chapters.size - 1) {
            nextChapter()
        } else {
            setPlaying(false)
        }
    }

    fun setTimerDialogOpen(open: Boolean) {
        _timerUiState.update { it.copy(isTimerDialogOpen = open) }
    }

    fun setTimerConfig(minutes: Int, seconds: Int) {
        val clampedMins = minutes.coerceIn(0, 99)
        val clampedSecs = seconds.coerceIn(0, 59)
        _timerUiState.update {
            it.copy(
                timerMinutes = clampedMins,
                timerSeconds = clampedSecs,
                timerRemainingSeconds = (clampedMins * 60) + clampedSecs
            )
        }
    }

    fun startTimer() {
        val state = _timerUiState.value
        val initialRemaining = if (state.timerState == TimerState.IDLE) {
            (state.timerMinutes * 60) + state.timerSeconds
        } else {
            state.timerRemainingSeconds
        }

        if (initialRemaining <= 0) return

        _timerUiState.update {
            it.copy(
                timerRemainingSeconds = initialRemaining,
                timerState = TimerState.RUNNING
            )
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timerUiState.value.timerRemainingSeconds > 0 && _timerUiState.value.timerState == TimerState.RUNNING) {
                delay(1000)
                _timerUiState.update {
                    val next = it.timerRemainingSeconds - 1
                    if (next <= 0) {
                        it.copy(timerRemainingSeconds = 0, timerState = TimerState.IDLE)
                    } else {
                        it.copy(timerRemainingSeconds = next)
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _timerUiState.update { it.copy(timerState = TimerState.PAUSED) }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _timerUiState.update {
            val resetTime = (it.timerMinutes * 60) + it.timerSeconds
            it.copy(
                timerState = TimerState.IDLE,
                timerRemainingSeconds = resetTime
            )
        }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
