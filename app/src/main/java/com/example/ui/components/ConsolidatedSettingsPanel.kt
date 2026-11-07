package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.data.model.SongGroup
import com.example.recorder.ExportedVideo
import com.example.recorder.RecordingStatus
import com.example.ui.OrientationPreference
import com.example.ui.RepeatMode
import com.example.ui.VisualizerStyle

@Composable
fun ConsolidatedSettingsPanel(
    songGroups: List<SongGroup>,
    currentSong: Song?,
    isScanning: Boolean,
    visualizerStyle: VisualizerStyle,
    visualizerAttached: Boolean,
    visualizerError: String?,
    recordingStatus: RecordingStatus,
    lastExportedVideo: ExportedVideo?,
    autoAdvanceEnabled: Boolean,
    repeatMode: RepeatMode,
    orientationPreference: OrientationPreference,
    showLyricsOnStage: Boolean,
    onSongSelected: (Song) -> Unit,
    onRescanLibrary: () -> Unit,
    onImportAudioFiles: (List<Uri>) -> Unit,
    onArtworkSelected: (Uri) -> Unit,
    onVisualizerStyleSelected: (VisualizerStyle) -> Unit,
    onStartRecording: (isPortrait: Boolean, durationSec: Int) -> Unit,
    onStopRecording: () -> Unit,
    onResetRecordingStatus: () -> Unit,
    onPlayExportedVideo: (ExportedVideo) -> Unit,
    onShareExportedVideo: (ExportedVideo) -> Unit,
    onAutoAdvanceChanged: (Boolean) -> Unit,
    onRepeatModeChanged: (RepeatMode) -> Unit,
    onOrientationPreferenceChanged: (OrientationPreference) -> Unit,
    onShowLyricsChanged: (Boolean) -> Unit,
    onCloseSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isPortraitRecordingPreset by remember { mutableStateOf(true) }
    var selectedDurationSec by remember { mutableIntStateOf(15) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportAudioFiles(uris)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onArtworkSelected(uri)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0B1A))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("consolidated_settings_panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Settings Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings & Studio Controls",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Library • Artwork • Visualizer • MP4 Recorder • Playback • Display",
                    color = Color(0xFFB57CFF),
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = onCloseSettings,
                modifier = Modifier.testTag("close_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Return to Player Stage",
                    tint = Color(0xFFD4C8EB)
                )
            }
        }

        // =========================================================================
        // 1. MUSIC LIBRARY & MANUAL IMPORT
        // =========================================================================
        SettingsSectionCard(
            title = "1. Music Library & Manual Import",
            icon = Icons.Default.LibraryMusic,
            testTag = "settings_section_library"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songGroups.size} song group(s) available",
                    color = Color(0xFF9E92B8),
                    fontSize = 12.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRescanLibrary,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD4C8EB)),
                        modifier = Modifier.testTag("rescan_library_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFB57CFF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Library",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rescan", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            audioPickerLauncher.launch(arrayOf("audio/*", "audio/mpeg"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6E3EBF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("import_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import MP3", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF9E92B8)
                    )
                },
                placeholder = { Text("Filter songs or versions...", color = Color(0xFF6B6082)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFB57CFF),
                    unfocusedBorderColor = Color(0xFF2C2442),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            val filteredGroups = songGroups.filter { group ->
                searchQuery.isBlank() ||
                    group.baseTitle.contains(searchQuery, ignoreCase = true) ||
                    group.versions.any { it.versionName.contains(searchQuery, ignoreCase = true) }
            }

            if (filteredGroups.isEmpty()) {
                Text(
                    text = "No audio tracks found. Tap 'Import MP3' or 'Rescan' to add music.",
                    color = Color(0xFF8B7EAA),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredGroups.forEach { group ->
                        LibrarySongGroupItem(
                            group = group,
                            currentSong = currentSong,
                            onSongSelected = onSongSelected
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 2. ARTWORK SELECTION
        // =========================================================================
        SettingsSectionCard(
            title = "2. Artwork Selection",
            icon = Icons.Default.AddPhotoAlternate,
            testTag = "settings_section_artwork"
        ) {
            Text(
                text = "Active Song: ${currentSong?.songTitle ?: "None Selected"}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Rendering Mode: FIT (Aspect ratio preserved, centered, never cropped)",
                color = Color(0xFF9E92B8),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = currentSong != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6E3EBF),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("change_artwork_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Artwork from Photo Library")
            }
        }

        // =========================================================================
        // 3. VISUALIZER CONFIGURATION
        // =========================================================================
        SettingsSectionCard(
            title = "3. Visualizer Configuration",
            icon = Icons.Default.Equalizer,
            testTag = "settings_section_visualizer"
        ) {
            Text(
                text = if (visualizerAttached) {
                    "Status: Connected to active audio session FFT"
                } else {
                    visualizerError ?: "Status: Standby (attaches on active audio playback)"
                },
                color = if (visualizerError != null) Color(0xFFFF8A80) else Color(0xFF9E92B8),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisualizerStyle.entries.forEach { style ->
                    FilterChip(
                        selected = visualizerStyle == style,
                        onClick = { onVisualizerStyleSelected(style) },
                        label = { Text(style.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6E3EBF),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF221A38),
                            labelColor = Color(0xFFB0A2CE)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("visualizer_style_${style.name.lowercase()}")
                    )
                }
            }
        }

        // =========================================================================
        // 4. MP4 RECORDING & EXPORT (Consolidated per CP-08 Section C)
        // =========================================================================
        SettingsSectionCard(
            title = "4. MP4 Video Recording & Export",
            icon = Icons.Default.Videocam,
            testTag = "settings_section_recording"
        ) {
            Text(
                text = "Target Format / Aspect Ratio Preset (30 FPS):",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isPortraitRecordingPreset,
                    onClick = { isPortraitRecordingPreset = true },
                    enabled = recordingStatus !is RecordingStatus.Recording,
                    label = { Text("Portrait (720×1280)\nYouTube Shorts", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF6E3EBF),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF221A38),
                        labelColor = Color(0xFFB0A2CE)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_portrait_shorts")
                )

                FilterChip(
                    selected = !isPortraitRecordingPreset,
                    onClick = { isPortraitRecordingPreset = false },
                    enabled = recordingStatus !is RecordingStatus.Recording,
                    label = { Text("Landscape (1280×720)\nYouTube Standard", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF6E3EBF),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF221A38),
                        labelColor = Color(0xFFB0A2CE)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_landscape_standard")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recording Duration:",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 25).forEach { sec ->
                    FilterChip(
                        selected = selectedDurationSec == sec,
                        onClick = { selectedDurationSec = sec },
                        enabled = recordingStatus !is RecordingStatus.Recording,
                        label = { Text("${sec}s", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6E3EBF),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF221A38),
                            labelColor = Color(0xFFB0A2CE)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("duration_${sec}s")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start Recording and Stop Recording controls (always visible/accessible in Settings)
            val isRecordingActive = recordingStatus is RecordingStatus.Recording
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onStartRecording(isPortraitRecordingPreset, selectedDurationSec)
                    },
                    enabled = currentSong != null && !isRecordingActive && recordingStatus !is RecordingStatus.Finalizing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB57CFF),
                        contentColor = Color(0xFF0D0B18)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Recording", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onStopRecording,
                    enabled = isRecordingActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stop_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Recording", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recording Status & Export Completion Display
            when (recordingStatus) {
                is RecordingStatus.Idle -> {
                    Text(
                        text = if (currentSong != null) {
                            "Status: Ready to record '${currentSong.songTitle}' (${currentSong.versionName})"
                        } else {
                            "Status: Select or import a song in the library above to record."
                        },
                        color = Color(0xFF9E92B8),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("recording_status_text")
                    )
                }
                is RecordingStatus.Recording -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recording_status_active")
                    ) {
                        Text(
                            text = "Recording in progress: ${recordingStatus.elapsedSec}s / ${recordingStatus.targetSec}s (30 FPS)",
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("recording_status_text")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = (recordingStatus.elapsedSec.toFloat() / recordingStatus.targetSec.coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Color(0xFF00F0FF),
                            trackColor = Color(0xFF2C2442),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }
                }
                is RecordingStatus.Finalizing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("recording_status_text")
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFB57CFF),
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Finalizing MP4 container & verifying audio/video streams...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                is RecordingStatus.Completed -> {
                    ExportCompletedCard(
                        video = recordingStatus.exportedVideo,
                        onPlay = { onPlayExportedVideo(recordingStatus.exportedVideo) },
                        onShare = { onShareExportedVideo(recordingStatus.exportedVideo) },
                        onReset = onResetRecordingStatus
                    )
                }
                is RecordingStatus.Failed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3B1826), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("recording_status_text"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recording Failed",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = recordingStatus.reason,
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(onClick = onResetRecordingStatus) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
            }

            // If idle but a previous export was completed in this session, keep file access available
            if (recordingStatus is RecordingStatus.Idle && lastExportedVideo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ExportCompletedCard(
                    video = lastExportedVideo,
                    onPlay = { onPlayExportedVideo(lastExportedVideo) },
                    onShare = { onShareExportedVideo(lastExportedVideo) },
                    onReset = null
                )
            }
        }

        // =========================================================================
        // 5. PLAYBACK SETTINGS
        // =========================================================================
        SettingsSectionCard(
            title = "5. Playback Settings",
            icon = Icons.Default.Tune,
            testTag = "settings_section_playback"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Advance to Next Track",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically play the next song when a track finishes",
                        color = Color(0xFF9E92B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoAdvanceEnabled,
                    onCheckedChange = onAutoAdvanceChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6E3EBF)
                    ),
                    modifier = Modifier.testTag("auto_advance_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Repeat Mode:",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatMode.entries.forEach { mode ->
                    FilterChip(
                        selected = repeatMode == mode,
                        onClick = { onRepeatModeChanged(mode) },
                        label = { Text(mode.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6E3EBF),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF221A38),
                            labelColor = Color(0xFFB0A2CE)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("repeat_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // =========================================================================
        // 6. DISPLAY & ORIENTATION SETTINGS
        // =========================================================================
        SettingsSectionCard(
            title = "6. Display & Orientation Settings",
            icon = Icons.Default.ScreenRotation,
            testTag = "settings_section_display"
        ) {
            Text(
                text = "Screen Orientation:",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrientationPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = orientationPreference == pref,
                        onClick = { onOrientationPreferenceChanged(pref) },
                        label = { Text(pref.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6E3EBF),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF221A38),
                            labelColor = Color(0xFFB0A2CE)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("orientation_${pref.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Lyrics Panel on Player Stage",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Display synchronized or plain lyrics over the stage",
                        color = Color(0xFF9E92B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = showLyricsOnStage,
                    onCheckedChange = onShowLyricsChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6E3EBF)
                    ),
                    modifier = Modifier.testTag("show_lyrics_switch")
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171228)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2B2244), RoundedCornerShape(16.dp))
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFB57CFF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun LibrarySongGroupItem(
    group: SongGroup,
    currentSong: Song?,
    onSongSelected: (Song) -> Unit
) {
    val isCurrentSongInGroup = group.versions.any { it.id == currentSong?.id }
    Surface(
        color = if (isCurrentSongInGroup) Color(0xFF261C3D) else Color(0xFF1E1733),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("song_group_${group.baseTitle.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3B295C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = group.baseTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${group.artist} • ${group.versions.size} version(s)",
                            color = Color(0xFF9E92B8),
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val toPlay = group.versions.find { it.id == currentSong?.id } ?: group.versions.first()
                        onSongSelected(toPlay)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play ${group.baseTitle}",
                        tint = Color(0xFFB57CFF),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (version in group.versions) {
                    val isPlayingThis = currentSong?.id == version.id
                    Surface(
                        color = if (isPlayingThis) Color(0xFF6E3EBF) else Color(0xFF2B2244),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onSongSelected(version) }
                    ) {
                        Text(
                            text = version.versionName,
                            color = if (isPlayingThis) Color.White else Color(0xFFC7B6E8),
                            fontSize = 11.sp,
                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCompletedCard(
    video: ExportedVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onReset: (() -> Unit)?
) {
    Surface(
        color = Color(0xFF1E2B2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_completed_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4EFA8A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MP4 Export Ready",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onReset != null) {
                    Text(
                        text = "Dismiss",
                        color = Color(0xFF9E92B8),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onReset() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.name,
                color = Color(0xFFD4C8EB),
                fontSize = 12.sp,
                modifier = Modifier.testTag("exported_filename_text")
            )
            Text(
                text = "Size: ${video.sizeBytes / 1024} KB",
                color = Color(0xFF9E92B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6E3EBF),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_exported_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open / Play", fontSize = 12.sp)
                }

                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00ADB5),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_exported_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share MP4", fontSize = 12.sp)
                }
            }
        }
    }
}
