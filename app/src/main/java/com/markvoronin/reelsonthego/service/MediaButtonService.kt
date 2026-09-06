package com.markvoronin.reelsonthego.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.markvoronin.reelsonthego.MainActivity
import com.markvoronin.reelsonthego.R
import com.markvoronin.reelsonthego.data.PreferencesRepository
import com.markvoronin.reelsonthego.util.Logger

class MediaButtonService : Service() {

    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var silentAudioTrack: AudioTrack? = null
    private var isReceiverRegistered = false
    private lateinit var prefsRepository: PreferencesRepository

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Logger.log("Bluetooth device connected (Car Head Unit)")
                    activateMediaSession()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Logger.log("Bluetooth device disconnected")
                    deactivateMediaSession()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.log("MediaButtonService created")
        prefsRepository = PreferencesRepository(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initMediaSession()
        registerBluetoothReceiver()
        activateMediaSession()
    }

    private fun registerBluetoothReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(bluetoothReceiver, filter)
            }
            isReceiverRegistered = true
            Logger.log("Registered Bluetooth connection receiver")
        }
    }

    private fun unregisterBluetoothReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                Logger.log("Error unregistering Bluetooth receiver: ${e.message}", isError = true)
            }
            isReceiverRegistered = false
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "ReelsMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Reels Steering Control")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Active")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Reels While Driving")
                .build()

            setMetadata(metadata)

            setCallback(object : MediaSession.Callback() {
                override fun onSkipToNext() {
                    Logger.log("MediaSession: onSkipToNext() received -> Swiping Up")
                    ReelsAccessibilityService.getInstance()?.swipeUp(force = true)
                }

                override fun onSkipToPrevious() {
                    if (prefsRepository.isPrevButtonDoubleTap) {
                        Logger.log("MediaSession: onSkipToPrevious() received -> Double Tapping (Like)")
                        ReelsAccessibilityService.getInstance()?.doubleTap(force = true)
                    } else {
                        Logger.log("MediaSession: onSkipToPrevious() received -> Swiping Down")
                        ReelsAccessibilityService.getInstance()?.swipeDown(force = true)
                    }
                }

                override fun onFastForward() {
                    Logger.log("MediaSession: onFastForward() received -> Swiping Up")
                    ReelsAccessibilityService.getInstance()?.swipeUp(force = true)
                }

                override fun onRewind() {
                    if (prefsRepository.isPrevButtonDoubleTap) {
                        Logger.log("MediaSession: onRewind() received -> Double Tapping (Like)")
                        ReelsAccessibilityService.getInstance()?.doubleTap(force = true)
                    } else {
                        Logger.log("MediaSession: onRewind() received -> Swiping Down")
                        ReelsAccessibilityService.getInstance()?.swipeDown(force = true)
                    }
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                    }

                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        Logger.log("MediaSession MediaButtonEvent KeyCode: ${keyEvent.keyCode}")
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                            KeyEvent.KEYCODE_NAVIGATE_NEXT,
                            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                            KeyEvent.KEYCODE_CHANNEL_UP -> {
                                ReelsAccessibilityService.getInstance()?.swipeUp(force = true)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                            KeyEvent.KEYCODE_MEDIA_REWIND,
                            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                if (prefsRepository.isPrevButtonDoubleTap) {
                                    ReelsAccessibilityService.getInstance()?.doubleTap(force = true)
                                } else {
                                    ReelsAccessibilityService.getInstance()?.swipeDown(force = true)
                                }
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
        }
    }

    private fun activateMediaSession() {
        requestAudioFocus()
        startSilentAudio()

        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_FAST_FORWARD or
                        PlaybackState.ACTION_REWIND
            )
            .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession?.apply {
            setPlaybackState(state)
            isActive = true
        }
        Logger.log("Activated MediaSession (Playing State + AudioFocus)")
    }

    private fun deactivateMediaSession() {
        stopSilentAudio()
        abandonAudioFocus()
        val state = PlaybackState.Builder()
            .setActions(0)
            .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
            .build()

        mediaSession?.apply {
            setPlaybackState(state)
            isActive = false
        }
        Logger.log("Deactivated MediaSession (Paused/Idle State)")
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Logger.log("AudioFocus change: $focusChange")
                }
                .build()

            audioFocusRequest = focusRequest
            val res = am.requestAudioFocus(focusRequest)
            Logger.log("Requested Audio Focus (AUDIOFOCUS_GAIN): result=$res")
        } else {
            @Suppress("DEPRECATION")
            val res = am.requestAudioFocus(
                { focus -> Logger.log("AudioFocus change: $focus") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            Logger.log("Requested Audio Focus: result=$res")
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun startSilentAudio() {
        if (silentAudioTrack != null) return
        try {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            silentAudioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                val silentBuffer = ByteArray(bufferSize)
                write(silentBuffer, 0, silentBuffer.size)
                setLoopPoints(0, silentBuffer.size / 4, -1)
                play()
            }
            Logger.log("Started silent AudioTrack for car Bluetooth focus")
        } catch (e: Exception) {
            Logger.log("Error starting silent audio track: ${e.message}", isError = true)
        }
    }

    private fun stopSilentAudio() {
        try {
            silentAudioTrack?.apply {
                stop()
                release()
            }
            silentAudioTrack = null
        } catch (e: Exception) {
            Logger.log("Error stopping silent audio track: ${e.message}", isError = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            deactivateMediaSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        activateMediaSession()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBluetoothReceiver()
        deactivateMediaSession()
        mediaSession?.release()
        mediaSession = null
        isRunning = false
        Logger.log("MediaButtonService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaButtonService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running_notification_title))
            .setContentText(getString(R.string.service_running_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reels Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Bluetooth media session active for steering wheel controls"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "reels_control_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.markvoronin.reelsonthego.action.START"
        const val ACTION_STOP = "com.markvoronin.reelsonthego.action.STOP"

        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, MediaButtonService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MediaButtonService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
