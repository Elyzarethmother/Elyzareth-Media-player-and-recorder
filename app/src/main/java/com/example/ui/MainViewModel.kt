package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ArtworkManager
import com.example.data.MusicLibraryManager
import com.example.data.SongGrouper
import com.example.data.model.Song
import com.example.data.model.SongGroup
import com.example.player.AudioPlayerEngine
import com.example.player.PlaybackMediaSessionService
import com.example.player.PlaybackState
import com.example.recorder.ExportManager
import com.example.recorder.ExportedVideo
import com.example.recorder.Mp4VideoRecorder
import com.example.recorder.RecordingStatus
import com.example.visualizer.VisualizerEngine
import com.example.visualizer.VisualizerFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisualizerStyle(val displayName: String) {
    SPECTRUM_BARS("Neon Spectrum"),
    RADIAL_CIRCLE("Radial Pulse"),
    MIRRORED_WAVE("Mirrored Wave")
}

enum class RepeatMode(val displayName: String) {
    OFF("Repeat Off"),
    REPEAT_ALL("Repeat All"),
    REPEAT_ONE("Repeat One")
}

enum class OrientationPreference(val displayName: String) {
    AUTO("Auto-Rotate"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

data class UiState(
    val songs: List<Song> = emptyList(),
    val songGroups: List<SongGroup> = emptyList(),
    val currentSongGroup: SongGroup? = null,
    val isScanning: Boolean = false,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.SPECTRUM_BARS,
    val showSettings: Boolean = false,
    val autoAdvanceEnabled: Boolean = true,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val orientationPreference: OrientationPreference = OrientationPreference.AUTO,
    val showLyricsOnStage: Boolean = true,
    val lastExportedVideo: ExportedVideo? = null,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val artworkManager = ArtworkManager(application)
    private val exportManager = ExportManager(application)
    private val musicLibraryManager = MusicLibraryManager(application, artworkManager)
    val playerEngine = AudioPlayerEngine(application, viewModelScope)
    val visualizerEngine = VisualizerEngine(application, viewModelScope)
    val recorder = Mp4VideoRecorder(application, artworkManager, exportManager)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerEngine.playbackState
    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerEngine.frame
    val recordingStatus: StateFlow<RecordingStatus> = recorder.status

    private var defaultAlbumArtBitmap: Bitmap? = null
    private var lastObservedSessionId: Int = -1
    private var lastObservedIsPlaying: Boolean = false

    init {
        try {
            defaultAlbumArtBitmap = BitmapFactory.decodeResource(
                application.resources,
                R.drawable.elyzareth_album_art
            )
        } catch (_: Exception) {
        }

        // Register background MediaSession transport callbacks
        PlaybackMediaSessionService.transportCallback = object : PlaybackMediaSessionService.TransportCallback {
            override fun onPlay() {
                playerEngine.play()
            }

            override fun onPause() {
                playerEngine.pause()
            }

            override fun onSkipNext() {
                skipNext()
            }

            override fun onSkipPrevious() {
                skipPrevious()
            }

            override fun onSeekTo(posMs: Long) {
                seekTo(posMs)
            }
        }

        viewModelScope.launch {
            playerEngine.playbackState.collect { state ->
                // Only reconfigure visualizer when audioSessionId or isPlaying actually changes
                if (state.audioSessionId != lastObservedSessionId || state.isPlaying != lastObservedIsPlaying) {
                    lastObservedSessionId = state.audioSessionId
                    lastObservedIsPlaying = state.isPlaying
                    visualizerEngine.setAudioSession(state.audioSessionId, state.isPlaying)
                    visualizerEngine.onPlaybackStateChanged(state.isPlaying)
                }

                state.currentSong?.let { curr ->
                    val group = _uiState.value.songGroups.find { g ->
                        g.baseTitle.equals(curr.songTitle, ignoreCase = true)
                    }
                    if (group != null && group != _uiState.value.currentSongGroup) {
                        _uiState.value = _uiState.value.copy(currentSongGroup = group)
                    }
                }
            }
        }

        viewModelScope.launch {
            recorder.status.collect { status ->
                if (status is RecordingStatus.Completed) {
                    _uiState.value = _uiState.value.copy(lastExportedVideo = status.exportedVideo)
                }
            }
        }

        playerEngine.setOnCompletionListener {
            onTrackCompleted()
        }

        loadLibrary()
    }

    internal fun onTrackCompleted() {
        val current = playbackState.value.currentSong ?: return
        val state = _uiState.value
        val allSongs = state.songs
        if (allSongs.isEmpty()) return

        if (state.repeatMode == RepeatMode.REPEAT_ONE) {
            playSongInternal(current, keepSettingsOpen = state.showSettings)
            return
        }

        if (!state.autoAdvanceEnabled) {
            return
        }

        val currentIndex = allSongs.indexOfFirst { it.id == current.id }
        if (currentIndex in 0 until (allSongs.size - 1)) {
            playSongInternal(allSongs[currentIndex + 1], keepSettingsOpen = state.showSettings)
        } else if (state.repeatMode == RepeatMode.REPEAT_ALL) {
            playSongInternal(allSongs[0], keepSettingsOpen = state.showSettings)
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val songs = musicLibraryManager.loadFullLibrary()
            val groups = SongGrouper.groupSongs(songs)
            val currentSong = playerEngine.playbackState.value.currentSong
            val activeGroup = if (currentSong != null) {
                groups.find { it.baseTitle.equals(currentSong.songTitle, ignoreCase = true) }
                    ?: groups.firstOrNull()
            } else {
                groups.firstOrNull()
            }
            val defaultSong = activeGroup?.versions?.firstOrNull() ?: songs.firstOrNull()

            _uiState.value = _uiState.value.copy(
                songs = songs,
                songGroups = groups,
                currentSongGroup = activeGroup,
                isScanning = false
            )

            // Only initialize player if no song is currently loaded (never interrupt active playback!)
            if (currentSong == null && defaultSong != null) {
                playerEngine.loadAndPlay(defaultSong, autoPlay = false)
            } else if (currentSong != null) {
                val refreshedCurrent = songs.find { it.id == currentSong.id }
                if (refreshedCurrent != null) {
                    playerEngine.updateCurrentSongMetadata(refreshedCurrent)
                }
            }
        }
    }

    fun rescanLibrary() {
        loadLibrary()
    }

    fun importAudioFiles(uris: List<Uri>) {
        viewModelScope.launch {
            var importedCount = 0
            var firstImported: Song? = null
            for (uri in uris) {
                val song = musicLibraryManager.importAudioUri(uri)
                if (song != null) {
                    importedCount++
                    if (firstImported == null) firstImported = song
                }
            }
            loadLibrary()
            if (playerEngine.playbackState.value.currentSong == null && firstImported != null) {
                playSongInternal(firstImported, keepSettingsOpen = _uiState.value.showSettings)
            }
            _uiState.value = _uiState.value.copy(
                statusMessage = "Imported $importedCount audio file(s) into Elyzareth library"
            )
        }
    }

    fun playSong(song: Song) {
        playSongInternal(song, keepSettingsOpen = _uiState.value.showSettings)
    }

    private fun playSongInternal(song: Song, keepSettingsOpen: Boolean) {
        val group = _uiState.value.songGroups.find { g ->
            g.baseTitle.equals(song.songTitle, ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(
            currentSongGroup = group,
            showSettings = keepSettingsOpen
        )
        playerEngine.loadAndPlay(song, autoPlay = true)
    }

    fun selectVersion(versionSong: Song) {
        val currPos = playbackState.value.currentPositionMs
        val isPlaying = playbackState.value.isPlaying
        playerEngine.loadAndPlay(versionSong, autoPlay = isPlaying, startPositionMs = currPos)
    }

    fun togglePlayPause() {
        playerEngine.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    fun skipNext() {
        val allSongs = _uiState.value.songs
        if (allSongs.isEmpty()) return
        val current = playbackState.value.currentSong
        val currentIndex = if (current != null) allSongs.indexOfFirst { it.id == current.id } else -1
        val nextIndex = if (currentIndex in allSongs.indices) (currentIndex + 1) % allSongs.size else 0
        playSongInternal(allSongs[nextIndex], keepSettingsOpen = _uiState.value.showSettings)
    }

    fun skipPrevious() {
        val allSongs = _uiState.value.songs
        if (allSongs.isEmpty()) return
        val current = playbackState.value.currentSong
        val currentIndex = if (current != null) allSongs.indexOfFirst { it.id == current.id } else -1
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else allSongs.size - 1
        playSongInternal(allSongs[prevIndex], keepSettingsOpen = _uiState.value.showSettings)
    }

    fun updateArtworkForCurrentSong(uri: Uri) {
        val current = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            val savedUri = artworkManager.saveArtworkForSong(current.id, current.songTitle, uri)
            if (savedUri != null) {
                val updatedSong = current.copy(customArtworkUri = savedUri)
                // Update metadata in-place without interrupting playback
                playerEngine.updateCurrentSongMetadata(updatedSong)
                loadLibrary()
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Artwork updated for ${current.songTitle}"
                )
            }
        }
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _uiState.value = _uiState.value.copy(visualizerStyle = style)
    }

    fun toggleSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettings = show)
    }

    fun setAutoAdvanceEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoAdvanceEnabled = enabled)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _uiState.value = _uiState.value.copy(repeatMode = mode)
    }

    fun setOrientationPreference(pref: OrientationPreference) {
        _uiState.value = _uiState.value.copy(orientationPreference = pref)
    }

    fun setShowLyricsOnStage(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLyricsOnStage = show)
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun startRecording(isPortrait: Boolean, durationSec: Int = 15) {
        val song = playbackState.value.currentSong ?: return
        val segmentStartMs = playbackState.value.currentPositionMs
        viewModelScope.launch {
            recorder.recordComposition(
                song = song,
                isPortrait = isPortrait,
                durationSeconds = durationSec,
                startPositionMs = segmentStartMs,
                defaultArtworkBitmap = defaultAlbumArtBitmap,
                realtimeFrequenciesProvider = { visualizerFrame.value.frequencies }
            )
        }
    }

    fun stopRecording() {
        recorder.stopRecording()
    }

    fun cancelRecording() {
        recorder.cancelRecording()
    }

    fun resetRecordingStatus() {
        recorder.resetStatus()
    }

    fun shareExportedVideo(video: ExportedVideo) {
        exportManager.shareVideo(video)
    }

    fun playExportedVideo(video: ExportedVideo) {
        exportManager.playVideo(video)
    }

    internal fun setSongsForTest(songs: List<Song>) {
        val groups = SongGrouper.groupSongs(songs)
        _uiState.value = _uiState.value.copy(
            songs = songs,
            songGroups = groups,
            currentSongGroup = groups.firstOrNull()
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (PlaybackMediaSessionService.transportCallback != null) {
            PlaybackMediaSessionService.transportCallback = null
        }
        playerEngine.release()
        visualizerEngine.release()
    }
}
