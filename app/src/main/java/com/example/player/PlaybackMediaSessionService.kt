package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.example.MainActivity
import com.example.R

class PlaybackMediaSessionService : MediaBrowserService() {

    companion object {
        const val CHANNEL_ID = "elyzareth_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.player.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.player.ACTION_STOP"
        const val ACTION_SYNC_STATE = "com.example.player.ACTION_SYNC_STATE"

        @Volatile
        var transportCallback: TransportCallback? = null

        fun syncForegroundNotification(context: Context, state: PlaybackState) {
            try {
                val appContext = context.applicationContext
                if (state.currentSong == null) return
                val intent = Intent(appContext, PlaybackMediaSessionService::class.java).apply {
                    action = ACTION_SYNC_STATE
                    putExtra("title", state.currentSong.songTitle)
                    putExtra("artist", "${state.currentSong.artist} • ${state.currentSong.versionName}")
                    putExtra("isPlaying", state.isPlaying)
                    putExtra("positionMs", state.currentPositionMs)
                    putExtra("durationMs", state.durationMs)
                }
                if (state.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore in unit test environments where background service start may be restricted
            }
        }
    }

    interface TransportCallback {
        fun onPlay()
        fun onPause()
        fun onSkipNext()
        fun onSkipPrevious()
        fun onSeekTo(posMs: Long)
    }

    private var mediaSession: MediaSession? = null
    private var currentTitle: String = "Elyzareth Player pro v1.0"
    private var currentArtist: String = "Elyzareth Studio"
    private var isPlayingState: Boolean = false
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val session = MediaSession(this, "ElyzarethMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    transportCallback?.onPlay()
                }

                override fun onPause() {
                    transportCallback?.onPause()
                }

                override fun onSkipToNext() {
                    transportCallback?.onSkipNext()
                }

                override fun onSkipToPrevious() {
                    transportCallback?.onSkipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    transportCallback?.onSeekTo(pos)
                }
            })
            isActive = true
        }
        mediaSession = session
        sessionToken = session.sessionToken
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> transportCallback?.onPlay()
            ACTION_PAUSE -> transportCallback?.onPause()
            ACTION_NEXT -> transportCallback?.onSkipNext()
            ACTION_PREVIOUS -> transportCallback?.onSkipPrevious()
            ACTION_STOP -> {
                transportCallback?.onPause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SYNC_STATE -> {
                currentTitle = intent.getStringExtra("title") ?: currentTitle
                currentArtist = intent.getStringExtra("artist") ?: currentArtist
                isPlayingState = intent.getBooleanExtra("isPlaying", false)
                positionMs = intent.getLongExtra("positionMs", 0L)
                durationMs = intent.getLongExtra("durationMs", 0L)
                updateMediaSessionAndNotification()
            }
        }
        return START_STICKY
    }

    private fun updateMediaSessionAndNotification() {
        val session = mediaSession ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()
        session.setMetadata(metadata)

        val actions = AndroidPlaybackState.ACTION_PLAY or
            AndroidPlaybackState.ACTION_PAUSE or
            AndroidPlaybackState.ACTION_PLAY_PAUSE or
            AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
            AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
            AndroidPlaybackState.ACTION_SEEK_TO

        val playbackState = AndroidPlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (isPlayingState) AndroidPlaybackState.STATE_PLAYING else AndroidPlaybackState.STATE_PAUSED,
                positionMs,
                if (isPlayingState) 1.0f else 0.0f
            )
            .build()
        session.setPlaybackState(playbackState)

        val notification = buildNotification(session)
        try {
            if (isPlayingState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(session: MediaSession): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = serviceActionPendingIntent(ACTION_PREVIOUS, 1)
        val playPausePending = serviceActionPendingIntent(
            if (isPlayingState) ACTION_PAUSE else ACTION_PLAY,
            2
        )
        val nextPending = serviceActionPendingIntent(ACTION_NEXT, 3)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val playPauseIcon = if (isPlayingState) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            prevPending
        ).build()

        val playPauseAction = Notification.Action.Builder(
            playPauseIcon,
            if (isPlayingState) "Pause" else "Play",
            playPausePending
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            nextPending
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlayingState)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .build()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackMediaSessionService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Elyzareth Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio playback controls for Elyzareth Player pro v1.0"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("elyzareth_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        super.onDestroy()
    }
}
