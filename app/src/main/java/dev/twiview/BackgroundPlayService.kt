package dev.twiview

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager

@OptIn(UnstableApi::class)
class BackgroundPlayService : Service() {
    private var notifications: PlayerNotificationManager? = null
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationUtil.createNotificationChannel(this, CHANNEL, R.string.notification_channel_playback, 0, NotificationUtil.IMPORTANCE_LOW)
        val placeholder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(subtitle).setSilent(true).build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        val player = current ?: run {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (notifications == null) {
            val s = MediaSession.Builder(this, player).build()
            session = s
            notifications = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL)
                .setChannelNameResourceId(R.string.notification_channel_playback)
                .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence = title
                    override fun getCurrentContentText(player: Player): CharSequence = subtitle
                    override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? = null
                    override fun createCurrentContentIntent(player: Player): PendingIntent =
                        PendingIntent.getActivity(this@BackgroundPlayService, 0, Intent(this@BackgroundPlayService, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                })
                .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                        if (ongoing) ServiceCompat.startForeground(this@BackgroundPlayService, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                        else ServiceCompat.stopForeground(this@BackgroundPlayService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    }

                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        if (dismissedByUser) player.pause()
                        stopSelf()
                    }
                })
                .setSmallIconResourceId(R.drawable.ic_notification)
                .build()
                .apply {
                    setMediaSessionToken(s.platformToken)
                    setUseNextAction(false)
                    setUsePreviousAction(false)
                    setUseNextActionInCompactView(false)
                    setUsePreviousActionInCompactView(false)
                    setUseFastForwardAction(seekable)
                    setUseRewindAction(seekable)
                    setUseStopAction(false)
                    setPlayer(player)
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notifications?.setPlayer(null)
        session?.release()
        notifications = null
        session = null
        current = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL = "playback"
        private var current: Player? = null
        private var title: CharSequence = ""
        private var subtitle: CharSequence = ""

        private var seekable = false

        fun start(context: Context, player: Player, title: CharSequence, subtitle: CharSequence, seekable: Boolean) {
            current = player
            this.seekable = seekable
            this.title = title
            this.subtitle = subtitle
            ContextCompat.startForegroundService(context, Intent(context, BackgroundPlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundPlayService::class.java))
        }
    }
}
