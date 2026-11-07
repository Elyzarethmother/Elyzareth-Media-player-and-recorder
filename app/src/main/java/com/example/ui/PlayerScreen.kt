package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AudioPermissionHelper
import com.example.ui.components.ConsolidatedSettingsPanel
import com.example.ui.components.PlaybackControls
import com.example.ui.components.SynthwaveStageCanvas
import com.example.ui.components.VersionSelector
import com.example.visualizer.StageAspectMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val visualizerFrame by viewModel.visualizerFrame.collectAsState()
    val recordingStatus by viewModel.recorder.status.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle system Back button when inside consolidated Settings or Fullscreen Stage
    BackHandler(enabled = uiState.showSettings || uiState.isFullscreenStage) {
        if (uiState.showSettings) {
            viewModel.toggleSettings(false)
        } else if (uiState.isFullscreenStage) {
            viewModel.toggleFullscreenStage(false)
        }
    }

    // Apply orientation preference without interrupting playback
    LaunchedEffect(uiState.orientationPreference) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = when (uiState.orientationPreference) {
            OrientationPreference.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    val requiredStoragePerm = remember {
        AudioPermissionHelper.getRequiredAudioStoragePermission()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val storageGranted = grants[requiredStoragePerm] == true
        val recordAudioGranted = grants[Manifest.permission.RECORD_AUDIO] == true

        if (storageGranted) {
            viewModel.rescanLibrary()
        }
        val latestPlayback = viewModel.playbackState.value
        if (recordAudioGranted || latestPlayback.audioSessionId > 0) {
            viewModel.visualizerEngine.setAudioSession(
                latestPlayback.audioSessionId,
                latestPlayback.isPlaying
            )
        }
    }

    LaunchedEffect(Unit) {
        val missingPerms = mutableListOf<String>()
        if (!AudioPermissionHelper.hasAudioStoragePermission(context)) {
            missingPerms.add(requiredStoragePerm)
        }
        if (!AudioPermissionHelper.hasRecordAudioPermission(context)) {
            missingPerms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                missingPerms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingPerms.isNotEmpty()) {
            permissionsLauncher.launch(missingPerms.toTypedArray())
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentSong = playbackState.currentSong

    // Cached bitmaps for live stage composition
    val currentArtworkBitmap = remember(currentSong?.id, currentSong?.customArtworkUri) {
        viewModel.loadCoverArtworkBitmap(currentSong)
    }
    val currentWallpaperBitmap = remember(uiState.visualConfig.wallpaperUri) {
        viewModel.loadWallpaperBitmap(uiState.visualConfig)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.isFullscreenStage || uiState.showSettings) {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Elyzareth Player",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF0F293A),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF))
                                ) {
                                    Text(
                                        text = "PRO v1.0",
                                        color = Color(0xFF00F0FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Elyzareth Studio • Synthwave Visualizer",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    actions = {
                        // Single unified Settings entry point (CP-08 Section D & CP-09 Phase 2)
                        IconButton(
                            onClick = { viewModel.toggleSettings(!uiState.showSettings) },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (uiState.showSettings) Color(0xFF00F0FF) else Color(0xFFD4C8EB)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF090C15)
                    )
                )
            }
        },
        bottomBar = {
            // Persistent playback controls bar accessible across Portrait, Landscape, Fullscreen Stage, and Settings
            PlaybackControls(
                isPlaying = playbackState.isPlaying,
                currentPositionMs = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs,
                currentTrackTitle = currentSong?.let { "${it.songTitle} (${it.versionName})" },
                playbackError = playbackState.error,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() }
            )
        },
        containerColor = Color(0xFF070A12),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090D18),
                            Color(0xFF070A12),
                            Color(0xFF04060B)
                        )
                    )
                )
        ) {
            if (uiState.showSettings) {
                ConsolidatedSettingsPanel(
                    songGroups = uiState.songGroups,
                    currentSong = currentSong,
                    isScanning = uiState.isScanning,
                    visualizerStyle = uiState.visualizerStyle,
                    visualConfig = uiState.visualConfig,
                    visualizerAttached = visualizerFrame.isAttached,
                    visualizerError = visualizerFrame.attachmentError,
                    recordingStatus = recordingStatus,
                    lastExportedVideo = uiState.lastExportedVideo,
                    lastExportedImage = uiState.lastExportedImage,
                    autoAdvanceEnabled = uiState.autoAdvanceEnabled,
                    repeatMode = uiState.repeatMode,
                    orientationPreference = uiState.orientationPreference,
                    showLyricsOnStage = uiState.showLyricsOnStage,
                    onSongSelected = { viewModel.playSong(it) },
                    onRescanLibrary = {
                        permissionsLauncher.launch(
                            arrayOf(
                                requiredStoragePerm,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                        viewModel.rescanLibrary()
                    },
                    onImportAudioFiles = { viewModel.importAudioFiles(it) },
                    onArtworkSelected = { viewModel.updateArtworkForCurrentSong(it) },
                    onClearArtwork = { viewModel.clearArtworkForCurrentSong() },
                    onWallpaperSelected = { viewModel.updateWallpaper(it) },
                    onClearWallpaper = { viewModel.clearWallpaper() },
                    onWallpaperOpacityChanged = { viewModel.setWallpaperOpacityPercent(it) },
                    onCuratedSkinSelected = { viewModel.setCuratedSkin(it) },
                    onStageAspectModeSelected = { viewModel.setStageAspectMode(it) },
                    onVinylRpmSelected = { viewModel.setVinylRpmPreset(it) },
                    onSunsetOffsetsChanged = { x, y -> viewModel.setSunsetOffsets(x, y) },
                    onCoverPositionAndScaleChanged = { x, y, scale ->
                        viewModel.setCoverPositionAndScale(x, y, scale)
                    },
                    onPlaylistOverlayConfigChanged = { show, offsetY, customText, highlightIdx ->
                        viewModel.setPlaylistOverlayConfig(show, offsetY, customText, highlightIdx)
                    },
                    onResetPositions = { viewModel.resetVisualPositions() },
                    onTimeFlowSpeedChanged = { viewModel.setTimeFlowSpeed(it) },
                    onAudioVolumeChanged = { viewModel.setAudioVolumePercent(it) },
                    onVisualizerStyleSelected = { viewModel.setVisualizerStyle(it) },
                    onCylinderTypographyChanged = { top, bottom, speed, font, size, topCol, botCol ->
                        viewModel.setCylinderTypographyConfig(top, bottom, speed, font, size, topCol, botCol)
                    },
                    onExportPngFrame = { isPortrait -> viewModel.exportFramePng(isPortrait) },
                    onViewExportedImage = { viewModel.viewExportedImage(it) },
                    onShareExportedImage = { viewModel.shareExportedImage(it) },
                    onDismissExportedImage = { viewModel.clearLastExportedImage() },
                    onStartRecording = { isPortrait, durationSec ->
                        viewModel.startRecording(isPortrait, durationSec)
                    },
                    onStopRecording = { viewModel.stopRecording() },
                    onResetRecordingStatus = { viewModel.resetRecordingStatus() },
                    onPlayExportedVideo = { viewModel.playExportedVideo(it) },
                    onShareExportedVideo = { viewModel.shareExportedVideo(it) },
                    onAutoAdvanceChanged = { viewModel.setAutoAdvanceEnabled(it) },
                    onRepeatModeChanged = { viewModel.setRepeatMode(it) },
                    onOrientationPreferenceChanged = { viewModel.setOrientationPreference(it) },
                    onShowLyricsChanged = { viewModel.setShowLyricsOnStage(it) },
                    onCloseSettings = { viewModel.toggleSettings(false) }
                )
            } else if (uiState.isFullscreenStage || isLandscape) {
                // FULLSCREEN OR LANDSCAPE SYNTHWAVE MONITOR STAGE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SynthwaveStageCanvas(
                        song = currentSong,
                        songs = uiState.songs,
                        config = uiState.visualConfig,
                        frame = visualizerFrame,
                        artworkBitmap = currentArtworkBitmap,
                        wallpaperBitmap = currentWallpaperBitmap,
                        songTimeMs = playbackState.currentPositionMs,
                        showLyrics = uiState.showLyricsOnStage,
                        isFullscreenStage = uiState.isFullscreenStage,
                        onToggleFullscreenStage = { viewModel.toggleFullscreenStage() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    if (!uiState.isFullscreenStage) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSong?.songTitle ?: "No Track Selected",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stage_song_title")
                            )
                            VersionSelector(
                                songGroup = uiState.currentSongGroup,
                                currentSong = currentSong,
                                onVersionSelected = { viewModel.selectVersion(it) }
                            )
                        }
                    }
                }
            } else {
                // PORTRAIT SYNTHWAVE MONITOR STAGE (Matches V1.5 Monitor Preview + Song Info & Version Selector)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Song Title & Alternate Version Bar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentSong?.songTitle ?: "Open Sky",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("stage_song_title")
                        )
                        Text(
                            text = if (currentSong != null) {
                                "${currentSong.artist} • Version: ${currentSong.versionName}"
                            } else {
                                "Tap Settings icon to scan or import MP3 files"
                            },
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        VersionSelector(
                            songGroup = uiState.currentSongGroup,
                            currentSong = currentSong,
                            onVersionSelected = { viewModel.selectVersion(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // V1.5 Synthwave Monitor Composition Stage
                    val stageModifier = if (uiState.visualConfig.stageAspectMode == StageAspectMode.SQUARE_1_1) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    }

                    SynthwaveStageCanvas(
                        song = currentSong,
                        songs = uiState.songs,
                        config = uiState.visualConfig,
                        frame = visualizerFrame,
                        artworkBitmap = currentArtworkBitmap,
                        wallpaperBitmap = currentWallpaperBitmap,
                        songTimeMs = playbackState.currentPositionMs,
                        showLyrics = uiState.showLyricsOnStage,
                        isFullscreenStage = false,
                        onToggleFullscreenStage = { viewModel.toggleFullscreenStage(true) },
                        modifier = stageModifier
                    )
                }
            }
        }
    }
}
