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
import com.example.recorder.ExportedImage
import com.example.recorder.ExportedVideo
import com.example.recorder.Mp4VideoRecorder
import com.example.recorder.RecordingStatus
import com.example.visualizer.CuratedSkin
import com.example.visualizer.CylinderFontFamily
import com.example.visualizer.StageAspectMode
import com.example.visualizer.VinylRpmPreset
import com.example.visualizer.VisualCompositionConfig
import com.example.visualizer.VisualizerEngine
import com.example.visualizer.VisualizerFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VisualizerStyle(val displayName: String) {
    NEON_SUNSET_GRID("1. Neon Sunset Grid (Default)"),
    SPECTRUM_BARS("2. Neon Spectrum"),
    RADIAL_CIRCLE("3. Radial Pulse"),
    MIRRORED_WAVE("4. Mirrored Wave"),
    RETRO_HORIZON_WAVE("5. Retro Horizon")
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
    val visualizerStyle: VisualizerStyle = VisualizerStyle.NEON_SUNSET_GRID,
    val visualConfig: VisualCompositionConfig = VisualCompositionConfig(),
    val showSettings: Boolean = false,
    val isFullscreenStage: Boolean = false,
    val autoAdvanceEnabled: Boolean = true,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val orientationPreference: OrientationPreference = OrientationPreference.AUTO,
    val showLyricsOnStage: Boolean = true,
    val lastExportedVideo: ExportedVideo? = null,
    val lastExportedImage: ExportedImage? = null,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val artworkManager = ArtworkManager(application)
    val exportManager = ExportManager(application)
    private val musicLibraryManager = MusicLibraryManager(application, artworkManager)
    val playerEngine = AudioPlayerEngine(application, viewModelScope)
    val visualizerEngine = VisualizerEngine(application, viewModelScope)
    val recorder = Mp4VideoRecorder(application, artworkManager, exportManager)

    private val _uiState = MutableStateFlow(
        UiState(
            visualConfig = VisualCompositionConfig(
                wallpaperUri = artworkManager.getWallpaperUri()
            )
        )
    )
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
                    statusMessage = "Left cover artwork updated for ${current.songTitle}"
                )
            }
        }
    }

    fun clearArtworkForCurrentSong() {
        val current = playbackState.value.currentSong ?: return
        artworkManager.clearArtworkForSong(current.id, current.songTitle)
        val updatedSong = current.copy(customArtworkUri = null)
        playerEngine.updateCurrentSongMetadata(updatedSong)
        loadLibrary()
        _uiState.value = _uiState.value.copy(
            statusMessage = "Cover artwork reset for ${current.songTitle}"
        )
    }

    fun updateWallpaper(uri: Uri) {
        viewModelScope.launch {
            val savedUri = artworkManager.saveWallpaper(uri)
            if (savedUri != null) {
                updateVisualConfig { it.copy(wallpaperUri = savedUri) }
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Background wallpaper updated"
                )
            }
        }
    }

    fun clearWallpaper() {
        artworkManager.clearWallpaper()
        updateVisualConfig { it.copy(wallpaperUri = null) }
        _uiState.value = _uiState.value.copy(
            statusMessage = "Background wallpaper cleared"
        )
    }

    fun updateVisualConfig(transform: (VisualCompositionConfig) -> VisualCompositionConfig) {
        val updated = transform(_uiState.value.visualConfig)
        _uiState.value = _uiState.value.copy(
            visualConfig = updated,
            visualizerStyle = updated.visualizerStyle
        )
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        updateVisualConfig { it.copy(visualizerStyle = style) }
    }

    fun setCuratedSkin(skin: CuratedSkin) {
        updateVisualConfig { it.copy(curatedSkin = skin) }
    }

    fun setVinylRpmPreset(preset: VinylRpmPreset) {
        updateVisualConfig { it.copy(vinylRpmPreset = preset) }
    }

    fun setStageAspectMode(mode: StageAspectMode) {
        updateVisualConfig { it.copy(stageAspectMode = mode) }
    }

    fun setWallpaperOpacityPercent(percent: Int) {
        updateVisualConfig { it.copy(wallpaperOpacityPercent = percent.coerceIn(0, 100)) }
    }

    fun setSunsetOffsets(offsetX: Int = _uiState.value.visualConfig.sunsetOffsetX, offsetY: Int = _uiState.value.visualConfig.sunsetOffsetY) {
        updateVisualConfig {
            it.copy(
                sunsetOffsetX = offsetX.coerceIn(-350, 350),
                sunsetOffsetY = offsetY.coerceIn(-250, 250)
            )
        }
    }

    fun setCoverPositionAndScale(
        offsetX: Int = _uiState.value.visualConfig.coverOffsetX,
        offsetY: Int = _uiState.value.visualConfig.coverOffsetY,
        scalePercent: Int = _uiState.value.visualConfig.coverScalePercent
    ) {
        updateVisualConfig {
            it.copy(
                coverOffsetX = offsetX.coerceIn(0, 350),
                coverOffsetY = offsetY.coerceIn(-250, 250),
                coverScalePercent = scalePercent.coerceIn(35, 160)
            )
        }
    }

    fun setPlaylistOverlayConfig(
        show: Boolean = _uiState.value.visualConfig.showPlaylistOverlay,
        offsetY: Int = _uiState.value.visualConfig.playlistOffsetY,
        customText: String = _uiState.value.visualConfig.customTracklistText,
        highlightedIndex: Int = _uiState.value.visualConfig.highlightedTrackIndex
    ) {
        updateVisualConfig {
            it.copy(
                showPlaylistOverlay = show,
                playlistOffsetY = offsetY.coerceIn(-150, 100),
                customTracklistText = customText,
                highlightedTrackIndex = highlightedIndex
            )
        }
    }

    fun setCylinderTypographyConfig(
        topText: String = _uiState.value.visualConfig.cylinderTopText,
        bottomTextOverride: String = _uiState.value.visualConfig.cylinderBottomTextOverride,
        rotationSpeed: Float = _uiState.value.visualConfig.cylinderRotationSpeed,
        fontFamily: CylinderFontFamily = _uiState.value.visualConfig.cylinderFontFamily,
        fontSizePx: Int = _uiState.value.visualConfig.cylinderFontSizePx,
        topColorHex: String = _uiState.value.visualConfig.cylinderTopColorHex,
        bottomColorHex: String = _uiState.value.visualConfig.cylinderBottomColorHex
    ) {
        updateVisualConfig {
            it.copy(
                cylinderTopText = topText,
                cylinderBottomTextOverride = bottomTextOverride,
                cylinderRotationSpeed = rotationSpeed.coerceIn(-5.0f, 5.0f),
                cylinderFontFamily = fontFamily,
                cylinderFontSizePx = fontSizePx.coerceIn(12, 42),
                cylinderTopColorHex = topColorHex,
                cylinderBottomColorHex = bottomColorHex
            )
        }
    }

    fun setTimeFlowSpeed(speed: Float) {
        updateVisualConfig { it.copy(timeFlowSpeed = speed.coerceIn(0.5f, 4.0f)) }
    }

    fun setAudioVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        playerEngine.setUserVolumePercent(clamped)
        updateVisualConfig { it.copy(audioVolumePercent = clamped) }
    }

    fun resetVisualPositions() {
        updateVisualConfig { it.resetPositions() }
        _uiState.value = _uiState.value.copy(
            statusMessage = "Layout element positions reset to default"
        )
    }

    fun toggleSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettings = show)
    }

    fun toggleFullscreenStage(fullscreen: Boolean = !_uiState.value.isFullscreenStage) {
        _uiState.value = _uiState.value.copy(isFullscreenStage = fullscreen)
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

    fun clearLastExportedImage() {
        _uiState.value = _uiState.value.copy(lastExportedImage = null)
    }

    fun exportFramePng(isPortrait: Boolean = false) {
        val state = _uiState.value
        val currentSong = playbackState.value.currentSong
        val currentPos = playbackState.value.currentPositionMs
        val freqs = visualizerFrame.value.frequencies
        val wave = visualizerFrame.value.waveform
        viewModelScope.launch {
            val exported = withContext(Dispatchers.IO) {
                val artBmp = artworkManager.loadBitmap(currentSong?.customArtworkUri) ?: defaultAlbumArtBitmap
                val wpBmp = artworkManager.loadBitmap(state.visualConfig.wallpaperUri)
                exportManager.exportPngFrame(
                    song = currentSong,
                    songs = state.songs,
                    config = state.visualConfig,
                    artworkBitmap = artBmp,
                    wallpaperBitmap = wpBmp,
                    songTimeMs = currentPos,
                    frequencies = freqs,
                    waveform = wave,
                    isPortrait = isPortrait,
                    showLyrics = state.showLyricsOnStage
                )
            }
            if (exported != null) {
                _uiState.value = _uiState.value.copy(
                    lastExportedImage = exported,
                    statusMessage = "PNG frame saved to ${exported.displayLocation}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to export PNG frame"
                )
            }
        }
    }

    fun startRecording(isPortrait: Boolean, durationSec: Int = 15) {
        val song = playbackState.value.currentSong ?: return
        val segmentStartMs = playbackState.value.currentPositionMs
        val currentSongs = _uiState.value.songs
        val currentConfig = _uiState.value.visualConfig
        val showLyrics = _uiState.value.showLyricsOnStage
        viewModelScope.launch {
            recorder.recordComposition(
                song = song,
                isPortrait = isPortrait,
                durationSeconds = durationSec,
                startPositionMs = segmentStartMs,
                defaultArtworkBitmap = defaultAlbumArtBitmap,
                songs = currentSongs,
                config = currentConfig,
                showLyrics = showLyrics,
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

    fun shareExportedImage(image: ExportedImage) {
        exportManager.shareImage(image)
    }

    fun viewExportedImage(image: ExportedImage) {
        exportManager.viewImage(image)
    }

    fun loadCoverArtworkBitmap(song: Song?): Bitmap? {
        return artworkManager.loadBitmap(song?.customArtworkUri) ?: defaultAlbumArtBitmap
    }

    fun loadWallpaperBitmap(config: VisualCompositionConfig): Bitmap? {
        return artworkManager.loadBitmap(config.wallpaperUri)
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
