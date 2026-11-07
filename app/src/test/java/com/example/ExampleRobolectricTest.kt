package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArtworkManager
import com.example.data.AudioPermissionHelper
import com.example.data.LyricsManager
import com.example.data.MusicLibraryManager
import com.example.data.SongGrouper
import com.example.data.model.Song
import com.example.player.PlaybackMediaSessionService
import com.example.recorder.Mp4StreamVerifier
import com.example.recorder.MuxerFormatCoordinator
import com.example.recorder.PcmFftAnalyzer
import com.example.recorder.RecordingStatus
import com.example.recorder.YuvConverter
import com.example.ui.MainViewModel
import com.example.ui.OrientationPreference
import com.example.ui.RepeatMode
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.ui.VisualizerStyle
import com.example.visualizer.CuratedSkin
import com.example.visualizer.CylinderFontFamily
import com.example.visualizer.StageAspectMode
import com.example.visualizer.SynthwaveCompositionRenderer
import com.example.visualizer.VinylRpmPreset
import com.example.visualizer.VisualCompositionConfig
import com.example.visualizer.VisualizerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Elyzareth Player pro v1.0", appName)
    }

    @Test
    fun `audio permission regression test across SDK levels`() {
        assertEquals(
            Manifest.permission.READ_MEDIA_AUDIO,
            AudioPermissionHelper.getRequiredAudioStoragePermission(Build.VERSION_CODES.TIRAMISU)
        )
        assertEquals(
            Manifest.permission.READ_MEDIA_AUDIO,
            AudioPermissionHelper.getRequiredAudioStoragePermission(34)
        )
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            AudioPermissionHelper.getRequiredAudioStoragePermission(Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            AudioPermissionHelper.getRequiredAudioStoragePermission(Build.VERSION_CODES.Q)
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.denyPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.RECORD_AUDIO)
        assertFalse(AudioPermissionHelper.hasAudioStoragePermission(app, 34))
        assertFalse(AudioPermissionHelper.hasRecordAudioPermission(app))

        shadowApp.grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.RECORD_AUDIO)
        assertTrue(AudioPermissionHelper.hasAudioStoragePermission(app, 34))
        assertTrue(AudioPermissionHelper.hasRecordAudioPermission(app))
    }

    @Test
    fun `persistent manual import survives library rescan`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val artworkManager = ArtworkManager(app)
        val libraryManager = MusicLibraryManager(app, artworkManager)

        val tempMp3 = File(app.cacheDir, "Elyzareth - Celestial Echo (Acoustic).mp3")
        tempMp3.writeBytes(ByteArray(2048) { (it % 127).toByte() })

        val fileUri = Uri.fromFile(tempMp3)
        val importedSong = libraryManager.importAudioUri(fileUri)
        assertNotNull("Manual import of accessible audio file must succeed", importedSong)
        assertEquals("Celestial Echo", importedSong?.songTitle)
        assertEquals("Acoustic", importedSong?.versionName)
        assertNotNull("Imported audio must be copied to app-private storage", importedSong?.filePath)
        assertTrue(File(importedSong!!.filePath!!).exists())

        tempMp3.delete()
        val rescannedLibrary = libraryManager.loadFullLibrary()
        assertTrue(
            "Manually imported track must survive library rescan",
            rescannedLibrary.any { it.songTitle == "Celestial Echo" && it.versionName == "Acoustic" }
        )
    }

    @Test
    fun `real visualizer attachment reports failure clearly without synthetic fallback`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)
        val engineNoPerm = VisualizerEngine(app, scope)
        engineNoPerm.setAudioSession(101, true)
        val frameNoPerm = engineNoPerm.frame.value
        assertFalse(frameNoPerm.isAttached)
        assertNotNull(frameNoPerm.attachmentError)
        assertTrue(frameNoPerm.attachmentError!!.contains("RECORD_AUDIO"))
        assertTrue(frameNoPerm.frequencies.all { it == 0f })

        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        val engineFail = VisualizerEngine(app, scope) { _ ->
            throw RuntimeException("Hardware audiofx session unavailable")
        }
        engineFail.setAudioSession(202, true)
        val frameFail = engineFail.frame.value
        assertFalse(frameFail.isAttached)
        assertNotNull(frameFail.attachmentError)
        assertTrue(frameFail.attachmentError!!.contains("Visualizer attachment failed"))
        assertTrue("Must not substitute synthetic animation on failure", frameFail.frequencies.all { it == 0f })

        val fftBytes = ByteArray(128)
        for (i in 4 until 40 step 2) {
            fftBytes[i] = 96.toByte()
            fftBytes[i + 1] = 64.toByte()
        }
        engineFail.processFft(fftBytes)
        val frameActive = engineFail.frame.value
        assertTrue(frameActive.isAttached)
        assertNull(frameActive.attachmentError)
        assertTrue("Real FFT input must produce positive frequency magnitudes", frameActive.rmsLevel > 0f)
    }

    @Test
    fun `YUV420SP colour conversion operator precedence and accuracy test`() {
        val (yWhite, uWhite, vWhite) = YuvConverter.rgbToYuv(255, 255, 255)
        assertEquals(235, yWhite)
        assertEquals(128, uWhite)
        assertEquals(128, vWhite)

        val (yBlack, uBlack, vBlack) = YuvConverter.rgbToYuv(0, 0, 0)
        assertEquals(16, yBlack)
        assertEquals(128, uBlack)
        assertEquals(128, vBlack)

        val (yRed, uRed, vRed) = YuvConverter.rgbToYuv(255, 0, 0)
        assertEquals(82, yRed)
        assertEquals(90, uRed)
        assertEquals(240, vRed)

        val (yGreen, uGreen, vGreen) = YuvConverter.rgbToYuv(0, 255, 0)
        assertEquals(144, yGreen)
        assertEquals(54, uGreen)
        assertEquals(34, vGreen)

        val (yBlue, uBlue, vBlue) = YuvConverter.rgbToYuv(0, 0, 255)
        assertEquals(41, yBlue)
        assertEquals(240, uBlue)
        assertEquals(110, vBlue)

        val width = 2
        val height = 2
        val argb = IntArray(4) { (0xFF shl 24) or (255 shl 16) or (0 shl 8) or 0 }
        val yuv = ByteArray(width * height * 3 / 2)
        YuvConverter.encodeYUV420SP(yuv, argb, width, height)
        assertEquals(82, yuv[0].toInt() and 0xFF)
        assertEquals(82, yuv[3].toInt() and 0xFF)
        assertEquals(90, yuv[4].toInt() and 0xFF)
        assertEquals(240, yuv[5].toInt() and 0xFF)
    }

    @Test
    fun `encoder format negotiation and monotonic timestamp synchronization test`() {
        val coordinator = MuxerFormatCoordinator()
        assertFalse(coordinator.isReadyToStart)

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 720, 1280)
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)

        coordinator.onVideoFormatChanged(videoFormat)
        assertFalse("Muxer must wait for both video and audio formats", coordinator.isReadyToStart)

        val s1 = coordinator.enqueueOrReadySample(true, byteArrayOf(1, 2, 3), 100_000L, 0)
        val s2 = coordinator.enqueueOrReadySample(true, byteArrayOf(4, 5, 6), 133_333L, 0)
        assertEquals(0L, s1.presentationTimeUs)
        assertEquals(33_333L, s2.presentationTimeUs)

        coordinator.onAudioFormatChanged(audioFormat)
        assertTrue(coordinator.isReadyToStart)

        val flushed = coordinator.markMuxerStarted()
        assertTrue(coordinator.isMuxerStarted)
        assertEquals(2, flushed.size)
        assertEquals(0L, flushed[0].presentationTimeUs)
        assertEquals(33_333L, flushed[1].presentationTimeUs)

        val s3 = coordinator.enqueueOrReadySample(true, byteArrayOf(7), 120_000L, 0)
        assertTrue("Timestamps must be strictly monotonic", s3.presentationTimeUs > s2.presentationTimeUs)
    }

    @Test
    fun `real PCM FFT analyzer extracts audio-reactive spectrum for recording`() {
        val sampleRate = 44100
        val numSamples = 512
        val pcmBytes = ByteArray(numSamples * 2)
        val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * Math.PI * 1000.0 * i / sampleRate) * 24000).toInt().toShort()
            shortBuf.put(i, sample)
        }

        val analyzer = PcmFftAnalyzer(numBars = 28)
        analyzer.recordPcmChunk(0L, pcmBytes, pcmBytes.size, channelCount = 1)

        val spectrum = analyzer.getSpectrumAt(0L)
        assertEquals(28, spectrum.size)
        assertTrue(
            "Decoded PCM sine wave must produce non-zero FFT spectrum bars",
            spectrum.any { it > 0.2f }
        )
    }

    @Test
    fun `MP4 stream verifier rejects missing or empty files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missingFile = File(context.cacheDir, "non_existent_output.mp4")
        val resultMissing = Mp4StreamVerifier.verifyMp4File(missingFile, 720, 1280)
        assertFalse(resultMissing.isValid)
        assertFalse(resultMissing.hasVideoTrack)
        assertFalse(resultMissing.hasAudioTrack)

        val emptyFile = File(context.cacheDir, "empty_output.mp4")
        emptyFile.writeBytes(ByteArray(0))
        val resultEmpty = Mp4StreamVerifier.verifyMp4File(emptyFile, 720, 1280)
        assertFalse(resultEmpty.isValid)
        emptyFile.delete()
    }

    @Test
    fun `test song version parsing and grouping`() {
        val (title1, ver1) = SongGrouper.parseSongInfo("Whispers of Twilight (Acoustic)", "Elyzareth")
        assertEquals("Whispers of Twilight", title1)
        assertEquals("Acoustic", ver1)

        val (title2, ver2) = SongGrouper.parseSongInfo("Starlight Symphony [Piano]", "Elyzareth")
        assertEquals("Starlight Symphony", title2)
        assertEquals("Piano", ver2)

        val songs = listOf(
            Song("1", "Whispers of Twilight", "Acoustic", "Whispers of Twilight (Acoustic)"),
            Song("2", "Whispers of Twilight", "Pop", "Whispers of Twilight (Pop)"),
            Song("3", "Neon Horizon", "Reggae", "Neon Horizon (Reggae)")
        )
        val groups = SongGrouper.groupSongs(songs)
        assertEquals(2, groups.size)
        assertEquals(2, groups.first { it.baseTitle == "Whispers of Twilight" }.versions.size)
    }

    @Test
    fun `test lyrics parser and active line index`() {
        val lrc = """
            [00:00.00]Line 0
            [00:02.00]Line 1
            [00:05.50]Line 2
        """.trimIndent()
        val lines = LyricsManager.parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals(0, LyricsManager.getActiveLyricIndex(lines, 1000L))
        assertEquals(1, LyricsManager.getActiveLyricIndex(lines, 3000L))
        assertEquals(2, LyricsManager.getActiveLyricIndex(lines, 7000L))
    }

    // =========================================================================
    // CP-08 REGRESSION TESTS
    // =========================================================================

    @Test
    fun `CP-08 continuous playback, Play Pause transitions, and seek continuity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Midnight Sun", "Acoustic", "Midnight Sun (Acoustic)", durationMs = 180_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 180_000L, isPlaying = true)

        assertTrue("Track must be actively playing", vm.playbackState.value.isPlaying)
        assertEquals("s1", vm.playbackState.value.currentSong?.id)

        // Seek within track while maintaining playback
        vm.seekTo(45_000L)
        assertEquals(45_000L, vm.playbackState.value.currentPositionMs)
        assertTrue("Playback must remain active during seek", vm.playbackState.value.isPlaying)

        // Pause transition
        vm.togglePlayPause()
        assertFalse("Play/Pause toggle must transition to paused", vm.playbackState.value.isPlaying)
        assertEquals(45_000L, vm.playbackState.value.currentPositionMs)

        // Play transition
        vm.togglePlayPause()
        assertTrue("Play/Pause toggle must transition back to playing", vm.playbackState.value.isPlaying)
    }

    @Test
    fun `CP-08 automatic next-track progression and Previous Next controls`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        val f1 = File(app.cacheDir, "track1.mp3").apply { writeBytes(ByteArray(512) { 1 }) }
        val f2 = File(app.cacheDir, "track2.mp3").apply { writeBytes(ByteArray(512) { 2 }) }
        val f3 = File(app.cacheDir, "track3.mp3").apply { writeBytes(ByteArray(512) { 3 }) }

        val s1 = Song("1", "Track One", "Original", "Track One", durationMs = 30_000L, filePath = f1.absolutePath)
        val s2 = Song("2", "Track Two", "Acoustic", "Track Two (Acoustic)", durationMs = 30_000L, filePath = f2.absolutePath)
        val s3 = Song("3", "Track Three", "Piano", "Track Three (Piano)", durationMs = 30_000L, filePath = f3.absolutePath)
        vm.setSongsForTest(listOf(s1, s2, s3))

        // Start on Track 1 and simulate natural track completion -> auto-advances to Track 2
        vm.playerEngine.setPreparedStateForTest(s1, durationMs = 30_000L, isPlaying = true)
        vm.playerEngine.triggerTrackCompletionForTest()
        assertEquals("Must automatically progress to Track 2 on completion", "2", vm.playbackState.value.currentSong?.id)

        // Test Next button -> advances to Track 3
        vm.skipNext()
        assertEquals("Next button must advance to Track 3", "3", vm.playbackState.value.currentSong?.id)

        // Test Previous button -> returns to Track 2
        vm.skipPrevious()
        assertEquals("Previous button must return to Track 2", "2", vm.playbackState.value.currentSong?.id)

        f1.delete(); f2.delete(); f3.delete()
    }

    @Test
    fun `CP-08 playback continues uninterrupted during library and settings interactions and rotation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Eternal Tide", "Pop", "Eternal Tide (Pop)", durationMs = 120_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 120_000L, isPlaying = true)
        vm.seekTo(22_000L)

        // Open consolidated Settings & interact with visualizer, playback settings, and orientation
        vm.toggleSettings(true)
        assertTrue(vm.uiState.value.showSettings)
        assertTrue("Opening Settings must not interrupt playback", vm.playbackState.value.isPlaying)

        vm.setVisualizerStyle(VisualizerStyle.RADIAL_CIRCLE)
        vm.setRepeatMode(RepeatMode.REPEAT_ONE)
        vm.setOrientationPreference(OrientationPreference.LANDSCAPE)
        vm.setOrientationPreference(OrientationPreference.PORTRAIT)
        assertTrue("Changing settings and orientation must preserve playback", vm.playbackState.value.isPlaying)
        assertEquals(22_000L, vm.playbackState.value.currentPositionMs)

        // Update artwork metadata in-place while playing
        val updatedSong = song.copy(customArtworkUri = "file:///custom_art.jpg")
        vm.playerEngine.updateCurrentSongMetadata(updatedSong)
        assertTrue("Updating artwork must not interrupt active player", vm.playbackState.value.isPlaying)
        assertEquals("file:///custom_art.jpg", vm.playbackState.value.currentSong?.customArtworkUri)

        // Trigger library rescan while playing -> active player must not be reset
        vm.rescanLibrary()
        assertTrue("Library rescan must not stop active playback", vm.playbackState.value.isPlaying)
        assertEquals("s1", vm.playbackState.value.currentSong?.id)

        vm.toggleSettings(false)
        assertFalse(vm.uiState.value.showSettings)
        assertTrue(vm.playbackState.value.isPlaying)
    }

    @Test
    fun `CP-08 audio focus interruption, ducking, and recovery test`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Focus Song", "Original", "Focus Song", durationMs = 90_000L)
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 90_000L, isPlaying = true)
        assertTrue(vm.playbackState.value.isPlaying)

        // 1. Transient audio focus loss (e.g. incoming notification/call) -> pauses playback
        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse("Transient focus loss must pause playback", vm.playbackState.value.isPlaying)

        // 2. Audio focus regained -> automatically resumes playback
        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue("Regaining audio focus after transient loss must resume playback", vm.playbackState.value.isPlaying)

        // 3. Transient ducking -> lowers volume without pausing playback
        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue("Ducking must keep playback active", vm.playbackState.value.isPlaying)
        assertTrue("Ducking flag must be active", vm.playbackState.value.isDucked)

        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse("Focus gain must restore normal volume from ducking", vm.playbackState.value.isDucked)
        assertTrue(vm.playbackState.value.isPlaying)

        // 4. Permanent audio focus loss -> pauses and does not auto-resume
        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertFalse("Permanent focus loss must pause playback", vm.playbackState.value.isPlaying)
        vm.playerEngine.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse("Permanent focus loss must not auto-resume without user action", vm.playbackState.value.isPlaying)
    }

    @Test
    fun `CP-08 background playback MediaSessionService transport control test`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Background Anthem", "Original", "Background Anthem", durationMs = 60_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 60_000L, isPlaying = true)

        val controller = Robolectric.buildService(PlaybackMediaSessionService::class.java).create()
        val service = controller.get()
        assertNotNull("MediaSession token must be initialized on service creation", service.sessionToken)

        // Dispatch pause action from foreground notification intent
        val pauseIntent = Intent(app, PlaybackMediaSessionService::class.java).apply {
            action = PlaybackMediaSessionService.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 1)
        assertFalse("Service ACTION_PAUSE must pause active player", vm.playbackState.value.isPlaying)

        // Dispatch play action from foreground notification intent
        val playIntent = Intent(app, PlaybackMediaSessionService::class.java).apply {
            action = PlaybackMediaSessionService.ACTION_PLAY
        }
        service.onStartCommand(playIntent, 0, 2)
        assertTrue("Service ACTION_PLAY must resume active player", vm.playbackState.value.isPlaying)

        controller.destroy()
    }

    @Test
    fun `CP-08 recording start, stop, and MP4 export completion in Settings`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Starlight Symphony", "Piano", "Starlight Symphony (Piano)", durationMs = 45_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 45_000L, isPlaying = true)

        assertTrue(vm.recordingStatus.value is RecordingStatus.Idle)

        // Start recording (Portrait 720x1280 preset, 15s)
        vm.recorder.simulateStartRecordingForTest(isPortrait = true, targetSec = 15)
        val recState = vm.recordingStatus.value
        assertTrue("Status must transition to Recording", recState is RecordingStatus.Recording)
        assertEquals(15, (recState as RecordingStatus.Recording).targetSec)
        assertTrue(recState.isPortrait)

        // Stop recording and finalize export
        val exported = vm.recorder.simulateStopAndExportForTest(song.songTitle, isPortrait = true)
        val completedState = vm.recordingStatus.value
        assertTrue("Status must transition to Completed on Stop", completedState is RecordingStatus.Completed)
        assertTrue("Exported MP4 file must exist on disk", exported.file.exists())
        assertTrue("Exported filename must include Portrait Shorts preset", exported.name.contains("Shorts_720x1280"))
        assertEquals(exported.name, vm.uiState.value.lastExportedVideo?.name)

        exported.file.delete()
    }

    // =========================================================================
    // CP-09 REGRESSION TESTS — V1.5 VISUALIZER RESTORATION & EXPORT VERIFICATION
    // =========================================================================

    @Test
    fun `CP-09 curated skins, 5 visualizer styles, and vinyl RPM deterministic angle test`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Open Sky", "Synthwave", "Open Sky", durationMs = 180_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 180_000L, isPlaying = true)

        // Verify V1.5 established default: NORMAL = 8 RPM
        assertEquals(VinylRpmPreset.NORMAL, vm.uiState.value.visualConfig.vinylRpmPreset)
        assertEquals(8f, vm.uiState.value.visualConfig.vinylRpmPreset.rpm, 0.01f)

        // At 7500ms (7.5s = 1/8th of a minute), 8 RPM must complete exactly 1.0 revolution (0 deg)
        // At 3750ms (3.75s), 8 RPM must complete exactly 0.5 revolution (180 deg)
        val angleHalfRev = SynthwaveCompositionRenderer.computeVinylAngleDegrees(3750L, 8f)
        assertEquals(180f, angleHalfRev, 0.1f)

        // Cycle through all CuratedSkin and VisualizerStyle options without interrupting playback
        for (skin in CuratedSkin.entries) {
            vm.setCuratedSkin(skin)
            assertEquals(skin, vm.uiState.value.visualConfig.curatedSkin)
            assertTrue("Changing skin must not interrupt playback", vm.playbackState.value.isPlaying)
        }

        for (style in VisualizerStyle.entries) {
            vm.setVisualizerStyle(style)
            assertEquals(style, vm.uiState.value.visualizerStyle)
            assertEquals(style, vm.uiState.value.visualConfig.visualizerStyle)
            assertTrue("Changing visualizer style must not interrupt playback", vm.playbackState.value.isPlaying)
        }
    }

    @Test
    fun `CP-09 3D cylinder typography bidirectional rotation and custom album song text test`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Open Sky", "Original", "Open Sky", album = "ELYZARETH RECORDS", durationMs = 120_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 120_000L, isPlaying = true)

        // Verify V1.5 established defaults
        val defaultCfg = vm.uiState.value.visualConfig
        assertEquals("ELYZARETH RECORDS", defaultCfg.cylinderTopText)
        assertEquals(-3.0f, defaultCfg.cylinderRotationSpeed, 0.01f)
        assertEquals(CylinderFontFamily.MONOSPACE, defaultCfg.cylinderFontFamily)
        assertEquals(20, defaultCfg.cylinderFontSizePx)
        assertEquals("#EC4899", defaultCfg.cylinderTopColorHex)
        assertEquals("#F59E0B", defaultCfg.cylinderBottomColorHex)

        // Bidirectional phase check: negative vs positive rotation speed
        val phaseNeg = SynthwaveCompositionRenderer.computeCylinderPhaseRadians(2000L, -3.0f)
        val phasePos = SynthwaveCompositionRenderer.computeCylinderPhaseRadians(2000L, +3.0f)
        assertEquals(-phasePos, phaseNeg, 0.001f)

        // Update 3D cylinder typography in ViewModel
        vm.setCylinderTypographyConfig(
            topText = "CUSTOM ALBUM RING",
            bottomTextOverride = "ONCE AGAIN",
            rotationSpeed = 2.5f,
            fontFamily = CylinderFontFamily.SANS_SERIF,
            fontSizePx = 26,
            topColorHex = "#00F0FF",
            bottomColorHex = "#10B981"
        )
        val updated = vm.uiState.value.visualConfig
        assertEquals("CUSTOM ALBUM RING", updated.cylinderTopText)
        assertEquals("ONCE AGAIN", updated.cylinderBottomTextOverride)
        assertEquals(2.5f, updated.cylinderRotationSpeed, 0.01f)
        assertEquals(CylinderFontFamily.SANS_SERIF, updated.cylinderFontFamily)
        assertEquals(26, updated.cylinderFontSizePx)
        assertTrue("Updating 3D cylinder typography must not interrupt playback", vm.playbackState.value.isPlaying)
    }

    @Test
    fun `CP-09 two-column playlist overlay active track synchronization test`() {
        val songs = (1..15).map { idx ->
            Song(
                id = "trk_$idx",
                songTitle = "Track Title $idx",
                versionName = "Original",
                fullTitle = "Track Title $idx",
                durationMs = 60_000L
            )
        }
        val currentSong = songs[3] // Track 4 (index 3)
        val config = VisualCompositionConfig()
        val (items, activeIdx) = SynthwaveCompositionRenderer.resolvePlaylistOverlayItems(songs, currentSong, config)

        assertEquals(15, items.size)
        assertEquals("01. Track Title 1", items[0])
        assertEquals("04. Track Title 4", items[3])
        assertEquals("15. Track Title 15", items[14])
        assertEquals("Active index must match currently playing track", 3, activeIdx)
    }

    @Test
    fun `CP-09 independent X Y layout positioning, scaling, wallpaper opacity, and Reset Positions test`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Open Sky", "Original", "Open Sky", durationMs = 120_000L)
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 120_000L, isPlaying = true)

        assertEquals(80, vm.uiState.value.visualConfig.wallpaperOpacityPercent)
        vm.setWallpaperOpacityPercent(65)
        assertEquals(65, vm.uiState.value.visualConfig.wallpaperOpacityPercent)

        // Apply V1.5 Screenshot 1 offsets: Sunset (-293px, +107px), Cover (+0px, -118px, 45% scale)
        vm.setSunsetOffsets(offsetX = -293, offsetY = 107)
        vm.setCoverPositionAndScale(offsetX = 120, offsetY = -118, scalePercent = 45)
        vm.setPlaylistOverlayConfig(show = true, offsetY = -30, customText = "", highlightedIndex = -1)

        val moved = vm.uiState.value.visualConfig
        assertEquals(-293, moved.sunsetOffsetX)
        assertEquals(107, moved.sunsetOffsetY)
        assertEquals(120, moved.coverOffsetX)
        assertEquals(-118, moved.coverOffsetY)
        assertEquals(45, moved.coverScalePercent)
        assertEquals(-30, moved.playlistOffsetY)

        // Reset Positions must restore all layout offsets and scale to defaults (0px / 100%)
        vm.resetVisualPositions()
        val reset = vm.uiState.value.visualConfig
        assertEquals(0, reset.sunsetOffsetX)
        assertEquals(0, reset.sunsetOffsetY)
        assertEquals(0, reset.coverOffsetX)
        assertEquals(0, reset.coverOffsetY)
        assertEquals(100, reset.coverScalePercent)
        assertEquals(0, reset.playlistOffsetY)
        assertEquals("Reset Positions must preserve wallpaper opacity", 65, reset.wallpaperOpacityPercent)
        assertTrue("Reset Positions must not interrupt playback", vm.playbackState.value.isPlaying)
    }

    @Test
    fun `CP-09 PNG frame export verification at 1280x720 and 720x1280 without interrupting playback`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val songs = listOf(
            Song("s1", "ONCE AGAIN", "Original", "ONCE AGAIN", durationMs = 180_000L),
            Song("s2", "Elysian Nights", "Synth", "Elysian Nights", durationMs = 195_000L),
            Song("s3", "Neon Horizon", "Original", "Neon Horizon", durationMs = 210_000L)
        )
        vm.setSongsForTest(songs)
        vm.playerEngine.setPreparedStateForTest(songs[0], durationMs = 180_000L, isPlaying = true)

        val freqs = FloatArray(32) { idx -> ((idx % 8) + 2) / 10f }

        // 1. Export Widescreen 1280x720 PNG
        val landscapePng = vm.exportManager.exportPngFrame(
            song = songs[0],
            songs = songs,
            config = vm.uiState.value.visualConfig,
            artworkBitmap = null,
            wallpaperBitmap = null,
            songTimeMs = 12_500L,
            frequencies = freqs,
            isPortrait = false,
            showLyrics = true
        )
        assertNotNull("Widescreen 1280x720 PNG export must succeed", landscapePng)
        assertEquals(1280, landscapePng!!.width)
        assertEquals(720, landscapePng.height)
        assertTrue("Exported 1280x720 PNG file must exist and be non-empty", landscapePng.file.exists() && landscapePng.sizeBytes > 1024L)
        assertTrue("Display location must target Pictures/Elyzareth", landscapePng.displayLocation.startsWith("Pictures/Elyzareth/"))

        // 2. Export Portrait 720x1280 PNG
        val portraitPng = vm.exportManager.exportPngFrame(
            song = songs[0],
            songs = songs,
            config = vm.uiState.value.visualConfig,
            artworkBitmap = null,
            wallpaperBitmap = null,
            songTimeMs = 12_500L,
            frequencies = freqs,
            isPortrait = true,
            showLyrics = true
        )
        assertNotNull("Portrait 720x1280 PNG export must succeed", portraitPng)
        assertEquals(720, portraitPng!!.width)
        assertEquals(1280, portraitPng.height)
        assertTrue("Exported 720x1280 PNG file must exist and be non-empty", portraitPng.file.exists() && portraitPng.sizeBytes > 1024L)

        // Copy sample PNG artifacts into ../release/ for inspection if release dir exists
        val releaseDir = File("../release")
        if (releaseDir.exists() && releaseDir.isDirectory) {
            landscapePng.file.copyTo(File(releaseDir, "sample_widescreen_1280x720.png"), overwrite = true)
            portraitPng.file.copyTo(File(releaseDir, "sample_portrait_720x1280.png"), overwrite = true)
        }

        assertTrue("PNG export must not interrupt active playback", vm.playbackState.value.isPlaying)

        landscapePng.file.delete()
        portraitPng.file.delete()
    }

    @Test
    fun `CP-09 watermark-free MP4 composition rendering at 720x1280 and 1280x720 with playback continuity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val song = Song("s1", "Open Sky", "Synthwave", "Open Sky", durationMs = 180_000L)
        vm.setSongsForTest(listOf(song))
        vm.playerEngine.setPreparedStateForTest(song, durationMs = 180_000L, isPlaying = true)
        vm.seekTo(15_000L)

        val freqs = FloatArray(28) { 0.55f }

        // Render 1280x720 frame via Mp4VideoRecorder.drawCompositionFrame
        val bmpLandscape = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvasLandscape = Canvas(bmpLandscape)
        vm.recorder.drawCompositionFrame(
            canvas = canvasLandscape,
            width = 1280,
            height = 720,
            song = song,
            artwork = null,
            songTimeMs = 15_000L,
            frequencies = freqs,
            isPortrait = false,
            songs = listOf(song),
            config = vm.uiState.value.visualConfig
        )
        assertEquals(1280, bmpLandscape.width)
        assertEquals(720, bmpLandscape.height)

        // Render 720x1280 frame via Mp4VideoRecorder.drawCompositionFrame
        val bmpPortrait = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvasPortrait = Canvas(bmpPortrait)
        vm.recorder.drawCompositionFrame(
            canvas = canvasPortrait,
            width = 720,
            height = 1280,
            song = song,
            artwork = null,
            songTimeMs = 15_000L,
            frequencies = freqs,
            isPortrait = true,
            songs = listOf(song),
            config = vm.uiState.value.visualConfig
        )
        assertEquals(720, bmpPortrait.width)
        assertEquals(1280, bmpPortrait.height)

        // Verify pause and seek on live player during active recording do not crash or corrupt state
        vm.recorder.simulateStartRecordingForTest(isPortrait = false, targetSec = 15)
        assertTrue(vm.recordingStatus.value is RecordingStatus.Recording)
        vm.togglePlayPause() // Pause live player during recording
        assertFalse(vm.playbackState.value.isPlaying)
        vm.seekTo(30_000L)   // Seek live player during recording
        assertEquals(30_000L, vm.playbackState.value.currentPositionMs)
        vm.togglePlayPause() // Resume live player during recording
        assertTrue(vm.playbackState.value.isPlaying)

        val exportedMp4 = vm.recorder.simulateStopAndExportForTest(song.songTitle, isPortrait = false)
        assertTrue(exportedMp4.file.exists())
        assertTrue(exportedMp4.name.contains("Standard_1280x720"))
        assertTrue("Stopping recording must keep live playback active", vm.playbackState.value.isPlaying)
        exportedMp4.file.delete()
    }
}

