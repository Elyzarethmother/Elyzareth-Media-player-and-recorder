package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.recorder.ExportedImage
import com.example.recorder.ExportedVideo
import com.example.recorder.RecordingStatus
import com.example.ui.OrientationPreference
import com.example.ui.RepeatMode
import com.example.ui.VisualizerStyle
import com.example.visualizer.CuratedSkin
import com.example.visualizer.CylinderFontFamily
import com.example.visualizer.StageAspectMode
import com.example.visualizer.VinylRpmPreset
import com.example.visualizer.VisualCompositionConfig
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConsolidatedSettingsPanel(
    songGroups: List<SongGroup>,
    currentSong: Song?,
    isScanning: Boolean,
    visualizerStyle: VisualizerStyle,
    visualConfig: VisualCompositionConfig = VisualCompositionConfig(),
    visualizerAttached: Boolean,
    visualizerError: String?,
    recordingStatus: RecordingStatus,
    lastExportedVideo: ExportedVideo?,
    lastExportedImage: ExportedImage? = null,
    autoAdvanceEnabled: Boolean,
    repeatMode: RepeatMode,
    orientationPreference: OrientationPreference,
    showLyricsOnStage: Boolean,
    onSongSelected: (Song) -> Unit,
    onRescanLibrary: () -> Unit,
    onImportAudioFiles: (List<Uri>) -> Unit,
    onArtworkSelected: (Uri) -> Unit,
    onClearArtwork: () -> Unit = {},
    onWallpaperSelected: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onWallpaperOpacityChanged: (Int) -> Unit = {},
    onCuratedSkinSelected: (CuratedSkin) -> Unit = {},
    onStageAspectModeSelected: (StageAspectMode) -> Unit = {},
    onVinylRpmSelected: (VinylRpmPreset) -> Unit = {},
    onSunsetOffsetsChanged: (Int, Int) -> Unit = { _, _ -> },
    onCoverPositionAndScaleChanged: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onPlaylistOverlayConfigChanged: (Boolean, Int, String, Int) -> Unit = { _, _, _, _ -> },
    onResetPositions: () -> Unit = {},
    onTimeFlowSpeedChanged: (Float) -> Unit = {},
    onAudioVolumeChanged: (Int) -> Unit = {},
    onVisualizerStyleSelected: (VisualizerStyle) -> Unit,
    onCylinderTypographyChanged: (String, String, Float, CylinderFontFamily, Int, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onExportPngFrame: (Boolean) -> Unit = {},
    onViewExportedImage: (ExportedImage) -> Unit = {},
    onShareExportedImage: (ExportedImage) -> Unit = {},
    onDismissExportedImage: () -> Unit = {},
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
    // Default landscape (1280x720) or portrait (720x1280) export preset
    var isPortraitRecordingPreset by remember { mutableStateOf(false) }
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

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onWallpaperSelected(uri)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D18))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF0A2536),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF))
                    ) {
                        Text(
                            text = "ELYZARETH STUDIO OS",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Settings & Synthwave Visualizer",
                    color = Color(0xFF00F0FF),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Library • Vinyl & Cover • Move Layout • 3D Cylinder • PNG & MP4 Export",
                    color = Color(0xFF94A3B8),
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
                                color = Color(0xFF00F0FF),
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
                            containerColor = Color(0xFF00B8D4),
                            contentColor = Color(0xFF041019)
                        ),
                        modifier = Modifier.testTag("import_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import MP3", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    focusedBorderColor = Color(0xFF00F0FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
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
        // 2. CURATED OS SKINS, BACKGROUND WALLPAPER, LEFT COVER ART & VINYL RPM
        // =========================================================================
        SettingsSectionCard(
            title = "2. Curated Skins, Background & Cover Art",
            icon = Icons.Default.Wallpaper,
            testTag = "settings_section_artwork"
        ) {
            Text(
                text = "CURATED OS SKINS",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CuratedSkin.entries.forEach { skin ->
                    FilterChip(
                        selected = visualConfig.curatedSkin == skin,
                        onClick = { onCuratedSkinSelected(skin) },
                        label = { Text(skin.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("skin_selector_${skin.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Aspect Ratio Toggle (1:1 | 16:9 matching V1.5 Screenshot 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BACKGROUND & COVER ART",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StageAspectMode.entries.forEach { mode ->
                        FilterChip(
                            selected = visualConfig.stageAspectMode == mode,
                            onClick = { onStageAspectModeSelected(mode) },
                            label = { Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00B8D4),
                                selectedLabelColor = Color(0xFF041019),
                                containerColor = Color(0xFF111827),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("aspect_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bg Wallpaper buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        wallpaperPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF111827),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_wallpaper_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bg Wallpaper", fontSize = 12.sp)
                }

                if (visualConfig.wallpaperUri != null) {
                    OutlinedButton(
                        onClick = onClearWallpaper,
                        modifier = Modifier.testTag("clear_wallpaper_button")
                    ) {
                        Text("Clear Bg", fontSize = 12.sp, color = Color(0xFFFF8A80))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Wallpaper Opacity Slider (Default 80% per V1.5 Screenshot 1)
            SliderParameterRow(
                label = "Wallpaper Opacity",
                valueText = "${visualConfig.wallpaperOpacityPercent}%",
                value = visualConfig.wallpaperOpacityPercent.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { onWallpaperOpacityChanged(it.roundToInt()) },
                accentColor = Color(0xFF00F0FF),
                testTag = "wallpaper_opacity_slider"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Left Cover Art buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = currentSong != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF111827),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("change_artwork_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Left Cover Art", fontSize = 12.sp)
                }

                if (currentSong?.customArtworkUri != null) {
                    OutlinedButton(
                        onClick = onClearArtwork,
                        modifier = Modifier.testTag("clear_artwork_button")
                    ) {
                        Text("Reset Art", fontSize = 12.sp, color = Color(0xFFFF8A80))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Vinyl Rotation Speed (SLOW | NORMAL | FAST | TURBO, 8 RPM default per Screenshot 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VINYL ROTATION SPEED",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${visualConfig.vinylRpmPreset.rpm.toInt()} RPM",
                    color = Color(0xFFEC4899),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("vinyl_rpm_value_text")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VinylRpmPreset.entries.forEach { preset ->
                    val selected = visualConfig.vinylRpmPreset == preset
                    Surface(
                        color = if (selected) Color(0xFFEC4899) else Color(0xFF111827),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onVinylRpmSelected(preset) }
                            .testTag("vinyl_rpm_${preset.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (selected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. MOVE LAYOUT ELEMENTS & RESET POSITIONS (V1.5 Screenshot 1 & 3)
        // =========================================================================
        SettingsSectionCard(
            title = "3. Move Layout Elements",
            icon = Icons.Default.OpenWith,
            testTag = "settings_section_layout"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEON SUNSET & 3D CYLINDER POSITION",
                    color = Color(0xFF00F0FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onResetPositions,
                    modifier = Modifier.testTag("reset_positions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESET POSITIONS", fontSize = 10.sp, color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold)
                }
            }

            val sunsetXSign = if (visualConfig.sunsetOffsetX > 0) "+" else ""
            SliderParameterRow(
                label = "Move Horizontally (X)",
                valueText = "$sunsetXSign${visualConfig.sunsetOffsetX}px",
                value = visualConfig.sunsetOffsetX.toFloat(),
                valueRange = -350f..350f,
                onValueChange = { onSunsetOffsetsChanged(it.roundToInt(), visualConfig.sunsetOffsetY) },
                accentColor = Color(0xFF00F0FF),
                testTag = "sunset_offset_x_slider"
            )

            val sunsetYSign = if (visualConfig.sunsetOffsetY > 0) "+" else ""
            SliderParameterRow(
                label = "Move Vertically (Y)",
                valueText = "$sunsetYSign${visualConfig.sunsetOffsetY}px",
                value = visualConfig.sunsetOffsetY.toFloat(),
                valueRange = -250f..250f,
                onValueChange = { onSunsetOffsetsChanged(visualConfig.sunsetOffsetX, it.roundToInt()) },
                accentColor = Color(0xFF00F0FF),
                testTag = "sunset_offset_y_slider"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "LEFT COVER ART & VINYL POSITION & SIZE",
                color = Color(0xFFA855F7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            SliderParameterRow(
                label = "Move Rightward (X only)",
                valueText = "+${visualConfig.coverOffsetX}px",
                value = visualConfig.coverOffsetX.toFloat(),
                valueRange = 0f..350f,
                onValueChange = {
                    onCoverPositionAndScaleChanged(
                        it.roundToInt(),
                        visualConfig.coverOffsetY,
                        visualConfig.coverScalePercent
                    )
                },
                accentColor = Color(0xFFA855F7),
                testTag = "cover_offset_x_slider"
            )

            val coverYSign = if (visualConfig.coverOffsetY > 0) "+" else ""
            SliderParameterRow(
                label = "Move Vertically (Y)",
                valueText = "$coverYSign${visualConfig.coverOffsetY}px",
                value = visualConfig.coverOffsetY.toFloat(),
                valueRange = -250f..250f,
                onValueChange = {
                    onCoverPositionAndScaleChanged(
                        visualConfig.coverOffsetX,
                        it.roundToInt(),
                        visualConfig.coverScalePercent
                    )
                },
                accentColor = Color(0xFFA855F7),
                testTag = "cover_offset_y_slider"
            )

            SliderParameterRow(
                label = "Scale Size",
                valueText = "${visualConfig.coverScalePercent}%",
                value = visualConfig.coverScalePercent.toFloat(),
                valueRange = 35f..160f,
                onValueChange = {
                    onCoverPositionAndScaleChanged(
                        visualConfig.coverOffsetX,
                        visualConfig.coverOffsetY,
                        it.roundToInt()
                    )
                },
                accentColor = Color(0xFFA855F7),
                testTag = "cover_scale_slider"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Playlist Overlay Positioning & Custom Tracklist (Max 20)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PLAYLIST TRACKLIST OVERLAY (MAX 20)",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Two-column bottom tracklist with active cyan glow",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = visualConfig.showPlaylistOverlay,
                    onCheckedChange = {
                        onPlaylistOverlayConfigChanged(
                            it,
                            visualConfig.playlistOffsetY,
                            visualConfig.customTracklistText,
                            visualConfig.highlightedTrackIndex
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00B8D4)
                    ),
                    modifier = Modifier.testTag("playlist_overlay_switch")
                )
            }

            if (visualConfig.showPlaylistOverlay) {
                SliderParameterRow(
                    label = "Playlist Vertical Offset (Y)",
                    valueText = "${visualConfig.playlistOffsetY}px",
                    value = visualConfig.playlistOffsetY.toFloat(),
                    valueRange = -150f..100f,
                    onValueChange = {
                        onPlaylistOverlayConfigChanged(
                            visualConfig.showPlaylistOverlay,
                            it.roundToInt(),
                            visualConfig.customTracklistText,
                            visualConfig.highlightedTrackIndex
                        )
                    },
                    accentColor = Color(0xFF00F0FF),
                    testTag = "playlist_offset_y_slider"
                )

                OutlinedTextField(
                    value = visualConfig.customTracklistText,
                    onValueChange = {
                        onPlaylistOverlayConfigChanged(
                            visualConfig.showPlaylistOverlay,
                            visualConfig.playlistOffsetY,
                            it,
                            visualConfig.highlightedTrackIndex
                        )
                    },
                    placeholder = {
                        Text(
                            "Leave blank to auto-display your discovered library tracks, or enter up to 20 tracks (one per line)...",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_tracklist_input")
                )
            }
        }

        // =========================================================================
        // 4. LIVE PARAMETERS, VISUALIZER STYLE & 3D CYLINDER TYPOGRAPHY
        // =========================================================================
        SettingsSectionCard(
            title = "4. Visualizer & 3D Cylinder Typography",
            icon = Icons.Default.Equalizer,
            testTag = "settings_section_visualizer"
        ) {
            Text(
                text = if (visualizerAttached) {
                    "FFT Audio Capture: Connected to active session"
                } else {
                    visualizerError ?: "FFT Audio Capture: Standby (attaches on active playback)"
                },
                color = if (visualizerError != null) Color(0xFFFF8A80) else Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            SliderParameterRow(
                label = "Time Flow Speed",
                valueText = String.format(Locale.US, "%.1fx", visualConfig.timeFlowSpeed),
                value = visualConfig.timeFlowSpeed,
                valueRange = 0.5f..4.0f,
                onValueChange = onTimeFlowSpeedChanged,
                accentColor = Color(0xFF10B981),
                testTag = "time_flow_speed_slider"
            )

            SliderParameterRow(
                label = "Audio Volume",
                valueText = "${visualConfig.audioVolumePercent}%",
                value = visualConfig.audioVolumePercent.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { onAudioVolumeChanged(it.roundToInt()) },
                accentColor = Color(0xFF00F0FF),
                testTag = "audio_volume_slider"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "VISUALIZER STYLE (5 ROBUST OPTIONS)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VisualizerStyle.entries.forEach { style ->
                    FilterChip(
                        selected = visualizerStyle == style,
                        onClick = { onVisualizerStyleSelected(style) },
                        label = { Text(style.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("visualizer_style_${style.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "EDIT 3D CYLINDER TEXT",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = visualConfig.cylinderTopText,
                    onValueChange = {
                        onCylinderTypographyChanged(
                            it,
                            visualConfig.cylinderBottomTextOverride,
                            visualConfig.cylinderRotationSpeed,
                            visualConfig.cylinderFontFamily,
                            visualConfig.cylinderFontSizePx,
                            visualConfig.cylinderTopColorHex,
                            visualConfig.cylinderBottomColorHex
                        )
                    },
                    label = { Text("TOP RING (ALBUM)", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEC4899),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cylinder_top_text_input")
                )

                OutlinedTextField(
                    value = visualConfig.cylinderBottomTextOverride,
                    onValueChange = {
                        onCylinderTypographyChanged(
                            visualConfig.cylinderTopText,
                            it,
                            visualConfig.cylinderRotationSpeed,
                            visualConfig.cylinderFontFamily,
                            visualConfig.cylinderFontSizePx,
                            visualConfig.cylinderTopColorHex,
                            visualConfig.cylinderBottomColorHex
                        )
                    },
                    label = { Text("BOTTOM RING (SONG)", fontSize = 10.sp) },
                    placeholder = { Text(currentSong?.songTitle ?: "Open Sky", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cylinder_bottom_text_input")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            SliderParameterRow(
                label = "Rotation Speed (Bidirectional)",
                valueText = String.format(Locale.US, "%.1fx", visualConfig.cylinderRotationSpeed),
                value = visualConfig.cylinderRotationSpeed,
                valueRange = -5.0f..5.0f,
                onValueChange = {
                    onCylinderTypographyChanged(
                        visualConfig.cylinderTopText,
                        visualConfig.cylinderBottomTextOverride,
                        it,
                        visualConfig.cylinderFontFamily,
                        visualConfig.cylinderFontSizePx,
                        visualConfig.cylinderTopColorHex,
                        visualConfig.cylinderBottomColorHex
                    )
                },
                accentColor = Color(0xFF00F0FF),
                testTag = "cylinder_rotation_speed_slider"
            )

            SliderParameterRow(
                label = "3D Font Size",
                valueText = "${visualConfig.cylinderFontSizePx}PX",
                value = visualConfig.cylinderFontSizePx.toFloat(),
                valueRange = 12f..36f,
                onValueChange = {
                    onCylinderTypographyChanged(
                        visualConfig.cylinderTopText,
                        visualConfig.cylinderBottomTextOverride,
                        visualConfig.cylinderRotationSpeed,
                        visualConfig.cylinderFontFamily,
                        it.roundToInt(),
                        visualConfig.cylinderTopColorHex,
                        visualConfig.cylinderBottomColorHex
                    )
                },
                accentColor = Color(0xFF00F0FF),
                testTag = "cylinder_font_size_slider"
            )

            // Font family selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CylinderFontFamily.entries.forEach { font ->
                    FilterChip(
                        selected = visualConfig.cylinderFontFamily == font,
                        onClick = {
                            onCylinderTypographyChanged(
                                visualConfig.cylinderTopText,
                                visualConfig.cylinderBottomTextOverride,
                                visualConfig.cylinderRotationSpeed,
                                font,
                                visualConfig.cylinderFontSizePx,
                                visualConfig.cylinderTopColorHex,
                                visualConfig.cylinderBottomColorHex
                            )
                        },
                        label = { Text(font.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cylinder_font_${font.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Top and Bottom Ring Color Swatches (Default #EC4899 and #F59E0B per V1.5 Screenshot 1)
            val colorSwatches = listOf("#EC4899", "#F59E0B", "#00F0FF", "#10B981", "#A855F7", "#FFFFFF")
            Text(
                text = "ALBUM (TOP) COLOR: ${visualConfig.cylinderTopColorHex}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                colorSwatches.forEach { hex ->
                    val swatchColor = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = visualConfig.cylinderTopColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                shape = CircleShape
                            )
                            .clickable {
                                onCylinderTypographyChanged(
                                    visualConfig.cylinderTopText,
                                    visualConfig.cylinderBottomTextOverride,
                                    visualConfig.cylinderRotationSpeed,
                                    visualConfig.cylinderFontFamily,
                                    visualConfig.cylinderFontSizePx,
                                    hex,
                                    visualConfig.cylinderBottomColorHex
                                )
                            }
                            .testTag("top_color_${hex.removePrefix("#").lowercase()}")
                    )
                }
            }

            Text(
                text = "SONG (BOTTOM) COLOR: ${visualConfig.cylinderBottomColorHex}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                colorSwatches.forEach { hex ->
                    val swatchColor = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = visualConfig.cylinderBottomColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                shape = CircleShape
                            )
                            .clickable {
                                onCylinderTypographyChanged(
                                    visualConfig.cylinderTopText,
                                    visualConfig.cylinderBottomTextOverride,
                                    visualConfig.cylinderRotationSpeed,
                                    visualConfig.cylinderFontFamily,
                                    visualConfig.cylinderFontSizePx,
                                    visualConfig.cylinderTopColorHex,
                                    hex
                                )
                            }
                            .testTag("bottom_color_${hex.removePrefix("#").lowercase()}")
                    )
                }
            }
        }

        // =========================================================================
        // 5. PNG FRAME & WATERMARK-FREE MP4 RECORDING & EXPORT
        // =========================================================================
        SettingsSectionCard(
            title = "5. PNG Frame & Watermark-Free MP4 Export",
            icon = Icons.Default.Videocam,
            testTag = "settings_section_recording"
        ) {
            Text(
                text = "Target Format / Resolution Preset:",
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
                    selected = !isPortraitRecordingPreset,
                    onClick = { isPortraitRecordingPreset = false },
                    enabled = recordingStatus !is RecordingStatus.Recording,
                    label = { Text("Widescreen (1280×720)\nYouTube Standard", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0F293A),
                        selectedLabelColor = Color(0xFF00F0FF),
                        containerColor = Color(0xFF111827),
                        labelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_landscape_standard")
                )

                FilterChip(
                    selected = isPortraitRecordingPreset,
                    onClick = { isPortraitRecordingPreset = true },
                    enabled = recordingStatus !is RecordingStatus.Recording,
                    label = { Text("Portrait (720×1280)\nYouTube Shorts", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0F293A),
                        selectedLabelColor = Color(0xFF00F0FF),
                        containerColor = Color(0xFF111827),
                        labelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_portrait_shorts")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // EXPORT WIDESCREEN / PORTRAIT FRAME (PNG) button (matching V1.5 Screenshot 1 & 3)
            OutlinedButton(
                onClick = { onExportPngFrame(isPortraitRecordingPreset) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_png_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF00F0FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPortraitRecordingPreset) {
                        "EXPORT PORTRAIT FRAME (PNG • 720×1280)"
                    } else {
                        "EXPORT WIDESCREEN FRAME (PNG • 1280×720)"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            if (lastExportedImage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PngExportCompletedCard(
                    image = lastExportedImage,
                    onView = { onViewExportedImage(lastExportedImage) },
                    onShare = { onShareExportedImage(lastExportedImage) },
                    onDismiss = onDismissExportedImage
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Video Recording Duration (30 FPS):",
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
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("duration_${sec}s")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Start Recording and Stop Recording controls
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
                        containerColor = Color(0xFF10B981),
                        contentColor = Color(0xFF041412)
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
                            "Status: Ready to record watermark-free HD video for '${currentSong.songTitle}' (${currentSong.versionName})"
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
                            color = Color(0xFF00F0FF),
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
        // 6. PLAYBACK SETTINGS
        // =========================================================================
        SettingsSectionCard(
            title = "6. Playback Settings",
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
                        checkedTrackColor = Color(0xFF00B8D4)
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
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("repeat_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // =========================================================================
        // 7. DISPLAY & ORIENTATION SETTINGS
        // =========================================================================
        SettingsSectionCard(
            title = "7. Display & Orientation Settings",
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
                            selectedContainerColor = Color(0xFF0F293A),
                            selectedLabelColor = Color(0xFF00F0FF),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
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
                        text = "Show Synchronized Lyrics on Stage",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Display synchronized or plain lyrics over the stage & exports",
                        color = Color(0xFF9E92B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = showLyricsOnStage,
                    onCheckedChange = onShowLyricsChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00B8D4)
                    ),
                    modifier = Modifier.testTag("show_lyrics_switch")
                )
            }
        }
    }
}

@Composable
private fun SliderParameterRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Text(
                text = valueText,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor.copy(alpha = 0.7f),
                inactiveTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF00F0FF),
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
        color = if (isCurrentSongInGroup) Color(0xFF17253B) else Color(0xFF111827),
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
                            .background(Color(0xFF1E293B)),
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
                            color = Color(0xFF94A3B8),
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
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (version in group.versions) {
                    val isPlayingThis = currentSong?.id == version.id
                    Surface(
                        color = if (isPlayingThis) Color(0xFF00B8D4) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onSongSelected(version) }
                    ) {
                        Text(
                            text = version.versionName,
                            color = if (isPlayingThis) Color(0xFF041019) else Color(0xFFCBD5E1),
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
private fun PngExportCompletedCard(
    image: ExportedImage,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF122636),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("png_export_completed_card")
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
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PNG Frame Saved (${image.width}×${image.height})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Dismiss",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = image.displayLocation,
                color = Color(0xFF00F0FF),
                fontSize = 11.sp,
                modifier = Modifier.testTag("exported_png_location_text")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onView,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("view_exported_png_button")
                ) {
                    Text("Open PNG", fontSize = 12.sp)
                }
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B8D4),
                        contentColor = Color(0xFF041019)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_exported_png_button")
                ) {
                    Text("Share PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        text = "MP4 Export Ready (Movies/Elyzareth)",
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
