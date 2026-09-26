package dev.metro.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

import android.graphics.Bitmap

/** Должен быть разрешен пользователем (доступ к уведомлениям). */
class MetroListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: MetroListener? = null
            private set
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }
}

data class TrackInfo(
    val title: String,
    val artist: String,
    val appLabel: String,
    val appPackage: String,
    val playing: Boolean,
    val albumArt: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
)

/**
 * Системный playback (как Media.qml в локскрине): текущий трек любого
 * приложения + play/pause/next/prev. Без активного плеера — пусто.
 */
class PlayerRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, MetroListener::class.java)

    private val _track = MutableStateFlow<TrackInfo?>(null)
    val track: StateFlow<TrackInfo?> = _track.asStateFlow()

    private var lastPackage: String? = null
    private var controller: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pull()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pull()
        }

        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            _track.value = null
        }
    }

    // Отдельная работа под SupervisorJob: uncaught-исключение из poll
    // глушил бы весь Scope и тикание, а вместе с ним — next/pull (они на нём).
    private val pollJob = SupervisorJob()

    init {
        scope.launch(pollJob) {
            while (isActive) {
                pull()
                delay(POLL_MS)
            }
        }
    }

    /** Остановить опрос и отпустить MediaController/Handler. */
    fun close() {
        pollJob.cancel()
        controller?.let { c ->
            try { c.unregisterCallback(controllerCallback) } catch (_: Exception) {}
        }
        controller = null
        _track.value = null
    }

    fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    fun openListenerSettings() {
        // На кастомных прошивках/сборках без экрана настроек — ActivityNotFound,
        // раньше это роняло лаунчер из UI-клика.
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    fun toggle() {
        val c = controller ?: return
        try {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) c.transportControls.pause() else c.transportControls.play()
        } catch (_: Exception) {
        }
        pull()
    }

    fun next() {
        try {
            controller?.transportControls?.skipToNext()
        } catch (_: Exception) {
        }
    }

    fun prev() {
        try {
            controller?.transportControls?.skipToPrevious()
        } catch (_: Exception) {
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            controller?.transportControls?.seekTo(positionMs)
        } catch (_: Exception) {
        }
    }

    fun openPlayer() {
        val pkg = _track.value?.appPackage ?: controller?.packageName ?: lastPackage
        if (pkg != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val musicIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(musicIntent)
        } catch (_: Exception) {}
    }

    private fun pull() {
        try {
            val sessions = sessionManager.getActiveSessions(listenerComponent)
            val session = sessions.firstOrNull { it.playbackState != null }
            if (session == null) {
                controller?.unregisterCallback(controllerCallback)
                controller = null
                _track.value = null
                return
            }
            lastPackage = session.packageName
            if (controller?.sessionToken != session.sessionToken) {
                controller?.unregisterCallback(controllerCallback)
                controller = MediaController(context, session.sessionToken).also {
                    it.registerCallback(controllerCallback, Handler(Looper.getMainLooper()))
                }
            }
            val c = controller ?: return
            val md = c.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: ""
            val state = c.playbackState?.state
            val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = c.playbackState?.position ?: 0L
            val appLabel = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(session.packageName, 0)
                ).toString()
            } catch (_: Exception) {
                session.packageName.substringAfterLast('.')
            }
            _track.value = TrackInfo(
                title = title?.takeIf { it.isNotBlank() } ?: "Без названия",
                artist = artist,
                appLabel = appLabel,
                appPackage = session.packageName,
                playing = state == PlaybackState.STATE_PLAYING,
                albumArt = art,
                durationMs = duration.coerceAtLeast(0L),
                positionMs = position.coerceAtLeast(0L),
            )
        } catch (_: SecurityException) {
            // NotificationListener не выдан — тихо показываем «ничего не играет».
            _track.value = null
        } catch (_: Exception) {
            // От RemoteException/IllegalStateException до RuntimeException от
            // упавшего плеера: раньше любой из них ронял корутину опроса
            // и плеер «замирал» навсегда до перезапуска процесса.
            _track.value = null
        }
    }

    companion object {
        private const val POLL_MS = 1500L
    }
}
