package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioSessionId: Int = 0,
    val isPrepared: Boolean = false,
    val isDucked: Boolean = false,
    val error: String? = null
)

class AudioPlayerEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Single persistent listener & request so AudioManager never revokes focus from itself
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
    } else {
        null
    }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressJob: Job? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var activeLoadToken: Long = 0L
    private var isTestPreparedMode: Boolean = false
    private var userVolumeScalar: Float = 1.0f

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionCallback = listener
    }

    fun setUserVolumePercent(percent: Int) {
        userVolumeScalar = (percent.coerceIn(0, 100)) / 100f
        val effectiveVol = if (_playbackState.value.isDucked) userVolumeScalar * 0.25f else userVolumeScalar
        try {
            mediaPlayer?.setVolume(effectiveVol, effectiveVol)
        } catch (_: Exception) {
        }
    }

    fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                pauseInternal(preserveResumeFlag = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                val wasPlaying = _playbackState.value.isPlaying
                resumeOnFocusGain = wasPlaying
                pauseInternal(preserveResumeFlag = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val duckVol = userVolumeScalar * 0.25f
                try {
                    mediaPlayer?.setVolume(duckVol, duckVol)
                } catch (_: Exception) {
                }
                _playbackState.value = _playbackState.value.copy(isDucked = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                try {
                    mediaPlayer?.setVolume(userVolumeScalar, userVolumeScalar)
                } catch (_: Exception) {
                }
                _playbackState.value = _playbackState.value.copy(isDucked = false)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    startPlaybackInternal()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
                result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            )
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        resumeOnFocusGain = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Updates current song metadata (such as custom artwork URI) in place without interrupting
     * or releasing the active MediaPlayer.
     */
    fun updateCurrentSongMetadata(updatedSong: Song) {
        val curr = _playbackState.value.currentSong ?: return
        if (curr.id == updatedSong.id) {
            _playbackState.value = _playbackState.value.copy(currentSong = updatedSong)
            PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
        }
    }

    fun loadAndPlay(song: Song, autoPlay: Boolean = true, startPositionMs: Long = 0L) {
        isTestPreparedMode = false
        activeLoadToken++
        val currentToken = activeLoadToken
        releasePlayerOnly()

        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                player.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
            } catch (_: Exception) {
            }

            configureDataSource(player, song)

            player.setOnPreparedListener { mp ->
                if (currentToken != activeLoadToken) return@setOnPreparedListener
                val rawDuration = try { mp.duration.toLong() } catch (_: Exception) { 0L }
                val duration = if (rawDuration > 0L) rawDuration else song.durationMs
                if (startPositionMs > 0L && (duration <= 0L || startPositionMs < duration)) {
                    try {
                        mp.seekTo(startPositionMs.toInt())
                    } catch (_: Exception) {
                    }
                }
                _playbackState.value = _playbackState.value.copy(
                    currentSong = song,
                    isPrepared = true,
                    currentPositionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = duration,
                    audioSessionId = mp.audioSessionId,
                    error = null
                )
                if (autoPlay) {
                    requestAudioFocus()
                    try {
                        mp.setVolume(userVolumeScalar, userVolumeScalar)
                        mp.start()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = true,
                            error = null
                        )
                        startProgressTracker()
                        PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
                    } catch (e: Exception) {
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            error = "Failed to start playback: ${e.message ?: e.javaClass.simpleName}"
                        )
                    }
                } else {
                    PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
                }
            }

            player.setOnCompletionListener {
                if (currentToken != activeLoadToken) return@setOnCompletionListener
                val currentState = _playbackState.value
                // Guard against spurious completion callbacks before preparation or on error
                if (!currentState.isPrepared || currentState.error != null) {
                    return@setOnCompletionListener
                }
                stopProgressTracker()
                _playbackState.value = currentState.copy(
                    isPlaying = false,
                    currentPositionMs = durationMsSafe()
                )
                PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
                onCompletionCallback?.invoke()
            }

            player.setOnErrorListener { _, what, extra ->
                if (currentToken != activeLoadToken) return@setOnErrorListener true
                if (what == -38) return@setOnErrorListener true
                stopProgressTracker()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPrepared = false,
                    error = "Playback decoder error (code $what / $extra) on '${song.songTitle}'."
                )
                PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
                true
            }

            mediaPlayer = player
            _playbackState.value = PlaybackState(
                currentSong = song,
                currentPositionMs = startPositionMs.coerceAtLeast(0L),
                durationMs = song.durationMs,
                audioSessionId = try { player.audioSessionId } catch (_: Exception) { 0 },
                isPrepared = false,
                error = null
            )
            player.prepareAsync()
        } catch (e: Exception) {
            _playbackState.value = PlaybackState(
                currentSong = song,
                isPlaying = false,
                isPrepared = false,
                error = "Unable to open audio source for '${song.songTitle}': ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun configureDataSource(player: MediaPlayer, song: Song) {
        var configured = false
        var lastException: Exception? = null

        // 1. Try local readable file path first (e.g. app-private imported audio)
        if (!song.filePath.isNullOrBlank()) {
            val file = File(song.filePath)
            if (file.exists() && file.canRead() && file.length() > 0L) {
                try {
                    FileInputStream(file).use { fis ->
                        player.setDataSource(fis.fd, 0L, file.length())
                        configured = true
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        // 2. Try content URI if not yet configured
        if (!configured && !song.contentUri.isNullOrBlank()) {
            try {
                player.setDataSource(appContext, Uri.parse(song.contentUri))
                configured = true
            } catch (e: Exception) {
                lastException = e
            }
        }

        // 3. Try asset path if applicable
        if (!configured && song.isAsset && !song.assetPath.isNullOrBlank()) {
            try {
                appContext.assets.openFd(song.assetPath).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    configured = true
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        if (!configured) {
            throw lastException ?: IllegalArgumentException("No readable audio source for ${song.fullTitle}")
        }
    }

    fun play() {
        resumeOnFocusGain = false
        val state = _playbackState.value
        val song = state.currentSong ?: return

        if (!state.isPrepared || (mediaPlayer == null && !isTestPreparedMode)) {
            loadAndPlay(song, autoPlay = true, startPositionMs = state.currentPositionMs)
            return
        }

        requestAudioFocus()
        startPlaybackInternal()
    }

    private fun startPlaybackInternal() {
        val state = _playbackState.value
        if (!state.isPrepared) return
        val player = mediaPlayer
        try {
            if (player != null && !player.isPlaying) {
                // If track was at the very end, restart from 0
                if (state.durationMs > 0L && state.currentPositionMs >= state.durationMs - 150L) {
                    player.seekTo(0)
                }
                player.setVolume(userVolumeScalar, userVolumeScalar)
                player.start()
            }
            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isDucked = false,
                error = null
            )
            startProgressTracker()
            PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                error = "Playback resume failed: ${e.message}"
            )
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        pauseInternal(preserveResumeFlag = false)
    }

    private fun pauseInternal(preserveResumeFlag: Boolean) {
        if (!preserveResumeFlag) {
            resumeOnFocusGain = false
        }
        stopProgressTracker()
        try {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                player.pause()
            }
        } catch (_: Exception) {
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = durationMsSafe()
        val safePos = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        try {
            if (_playbackState.value.isPrepared) {
                mediaPlayer?.seekTo(safePos.toInt())
            }
        } catch (_: Exception) {
        }
        _playbackState.value = _playbackState.value.copy(currentPositionMs = safePos)
        PlaybackMediaSessionService.syncForegroundNotification(appContext, _playbackState.value)
    }

    fun clearError() {
        _playbackState.value = _playbackState.value.copy(error = null)
    }

    /**
     * Test helper allowing deterministic JVM/Robolectric verification of prepared playback state,
     * audio focus transitions, and track completion progression without hardware audio decoders.
     */
    internal fun setPreparedStateForTest(song: Song, durationMs: Long, isPlaying: Boolean) {
        isTestPreparedMode = true
        if (isPlaying) {
            requestAudioFocus()
        }
        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = isPlaying,
            currentPositionMs = 0L,
            durationMs = durationMs,
            audioSessionId = 1,
            isPrepared = true,
            error = null
        )
    }

    internal fun triggerTrackCompletionForTest() {
        val currentState = _playbackState.value
        if (!currentState.isPrepared) return
        stopProgressTracker()
        _playbackState.value = currentState.copy(
            isPlaying = false,
            currentPositionMs = currentState.durationMs
        )
        onCompletionCallback?.invoke()
    }

    private fun durationMsSafe(): Long {
        return try {
            val d = mediaPlayer?.duration?.toLong() ?: _playbackState.value.durationMs
            if (d > 0L) d else _playbackState.value.durationMs
        } catch (_: Exception) {
            _playbackState.value.durationMs
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && _playbackState.value.isPlaying) {
                try {
                    val player = mediaPlayer
                    if (player != null && _playbackState.value.isPrepared && player.isPlaying) {
                        val currentPos = player.currentPosition.toLong().coerceAtLeast(0L)
                        _playbackState.value = _playbackState.value.copy(currentPositionMs = currentPos)
                    }
                } catch (_: Exception) {
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayerOnly() {
        isTestPreparedMode = false
        stopProgressTracker()
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        if (oldPlayer != null) {
            try {
                oldPlayer.setOnPreparedListener(null)
                oldPlayer.setOnCompletionListener(null)
                oldPlayer.setOnErrorListener(null)
                if (oldPlayer.isPlaying) {
                    oldPlayer.stop()
                }
            } catch (_: Exception) {
            }
            try {
                oldPlayer.reset()
            } catch (_: Exception) {
            }
            try {
                oldPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        activeLoadToken++
        abandonAudioFocus()
        releasePlayerOnly()
    }
}
