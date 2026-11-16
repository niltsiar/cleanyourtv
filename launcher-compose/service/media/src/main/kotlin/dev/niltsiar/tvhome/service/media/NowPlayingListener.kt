package dev.niltsiar.tvhome.service.media

import android.service.notification.NotificationListenerService

/**
 * The notification listener whose access lets Home read other apps' media sessions: `MediaSessionManager.getActiveSessions`
 * serves an app that names its own enabled listener. No notification is read. From API 31 the manifest's empty filter
 * delivers none; before that the system still calls [onNotificationPosted], which does nothing. Like any allowed listener,
 * the system binds it again after Home's process is killed. Enable with [NowPlayingAccess.setEnabled] and
 * `cmd notification allow_listener dev.niltsiar.tvhome/.service.media.NowPlayingListener`.
 */
public class NowPlayingListener : NotificationListenerService()
