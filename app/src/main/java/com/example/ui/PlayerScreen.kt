package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.data.AudioPermissionHelper
import com.example.ui.components.ArtworkView
import com.example.ui.components.ConsolidatedSettingsPanel
import com.example.ui.components.LyricsView
import com.example.ui.components.PlaybackControls
import com.example.ui.components.VersionSelector
import com.example.ui.components.VisualizerView

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

    // Handle system Back button when inside consolidated Settings
    BackHandler(enabled = uiState.showSettings) {
        viewModel.toggleSettings(false)
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
        if (recordAudioGranted || playbackState.audioSessionId > 0) {
            viewModel.visualizerEngine.setAudioSession(
                playbackState.audioSessionId,
                playbackState.isPlaying
            )
        }
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            requiredStoragePerm,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
                                color = Color(0xFF6E3EBF),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "PRO v1.0",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Elyzareth Studio",
                            color = Color(0xFFB57CFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    // Single unified Settings entry point (CP-08 Section D)
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
                    containerColor = Color(0xFF0F0B1C)
                )
            )
        },
        bottomBar = {
            // Persistent playback controls bar accessible across Player Stage, Library, and Settings (CP-08 Section B)
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
        containerColor = Color(0xFF0B0816),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0B1C),
                            Color(0xFF0B0816),
                            Color(0xFF05040A)
                        )
                    )
                )
        ) {
            if (uiState.showSettings) {
                // Consolidated Settings Panel (Library, Artwork, Visualizer, Recording/Export, Playback, Display)
                ConsolidatedSettingsPanel(
                    songGroups = uiState.songGroups,
                    currentSong = currentSong,
                    isScanning = uiState.isScanning,
                    visualizerStyle = uiState.visualizerStyle,
                    visualizerAttached = visualizerFrame.isAttached,
                    visualizerError = visualizerFrame.attachmentError,
                    recordingStatus = recordingStatus,
                    lastExportedVideo = uiState.lastExportedVideo,
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
                    onVisualizerStyleSelected = { viewModel.setVisualizerStyle(it) },
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
            } else if (isLandscape) {
                // LANDSCAPE PLAYER STAGE
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtworkView(
                            currentSong = currentSong,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentSong?.songTitle ?: "No Track Selected",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("stage_song_title")
                        )
                        Text(
                            text = "${currentSong?.artist ?: "Elyzareth"} • ${currentSong?.versionName ?: "Open Settings to Import MP3"}",
                            color = Color(0xFFB57CFF),
                            fontSize = 13.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        VersionSelector(
                            songGroup = uiState.currentSongGroup,
                            currentSong = currentSong,
                            onVersionSelected = { viewModel.selectVersion(it) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        VisualizerView(
                            frame = visualizerFrame,
                            style = uiState.visualizerStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )

                        if (uiState.showLyricsOnStage) {
                            LyricsView(
                                currentSong = currentSong,
                                currentPositionMs = playbackState.currentPositionMs,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // PORTRAIT PLAYER STAGE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    ArtworkView(
                        currentSong = currentSong,
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .aspectRatio(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentSong?.songTitle ?: "No Track Selected",
                            color = Color.White,
                            fontSize = 20.sp,
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
                            color = Color(0xFFB57CFF),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    VersionSelector(
                        songGroup = uiState.currentSongGroup,
                        currentSong = currentSong,
                        onVersionSelected = { viewModel.selectVersion(it) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.showLyricsOnStage) {
                        LyricsView(
                            currentSong = currentSong,
                            currentPositionMs = playbackState.currentPositionMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .height(105.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    VisualizerView(
                        frame = visualizerFrame,
                        style = uiState.visualizerStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                    )
                }
            }
        }
    }
}
