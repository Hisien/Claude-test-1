package com.scrollmate.app.winddown

import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.scrollmate.app.R
import com.scrollmate.app.ScrollMateApplication
import com.scrollmate.app.data.UsageAccess
import com.scrollmate.app.notifications.Notifier
import com.scrollmate.app.ui.MainActivity
import com.scrollmate.app.ui.Tab
import com.scrollmate.core.winddown.WindDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Evening wind-down without an accessibility service: while the wind-down window is active it
 * checks the foreground app about once per second via UsageStatsManager and covers selected apps
 * with a calming overlay. Outside the window it only wakes up about once a minute.
 */
class WindDownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var overlay: WindDownOverlay? = null
    private val tracker by lazy { ForegroundTracker(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay?.hide()
        super.onDestroy()
    }

    private suspend fun run() {
        val container = (application as ScrollMateApplication).container
        while (scope.isActive) {
            val prefs = container.settings.current()
            if (!WindDownController.isReady(this, prefs)) {
                withContext(Dispatchers.Main) { overlay?.hide() }
                stopSelf()
                return
            }
            val wd = prefs.windDown
            val now = ZonedDateTime.now(container.clock.withZone(ZoneId.systemDefault()))
            val local = now.toLocalDateTime()

            if (!WindDown.isActive(local, wd)) {
                withContext(Dispatchers.Main) { overlay?.hide() }
                tracker.reset()
                val wait = WindDown.untilNextStart(now, wd)
                delay(
                    when {
                        wait.isZero -> 30_000L // inside the window but paused for tonight
                        wait > Duration.ofMinutes(1) -> 60_000L
                        else -> wait.toMillis().coerceAtLeast(1_000L)
                    },
                )
                continue
            }

            val foreground = tracker.current()
            when {
                WindDown.isBlocked(foreground, local, wd) -> {
                    val label = foreground?.let { container.installedApps.label(it) } ?: ""
                    val bedtime = wd.bedtime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    withContext(Dispatchers.Main) { overlayFor().show(label, bedtime) }
                }
                foreground != null -> withContext(Dispatchers.Main) { overlay?.hide() }
            }
            delay(1_000L)
        }
    }

    private fun overlayFor(): WindDownOverlay = overlay ?: WindDownOverlay(
        context = this,
        onGoHome = { goHome() },
        onPauseTonight = { pauseTonight() },
    ).also { overlay = it }

    private fun goHome() {
        overlay?.hide()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: Exception) {
            // Nothing else to do; the overlay is gone and the next check will cover the app again.
        }
    }

    private fun pauseTonight() {
        overlay?.hide()
        val container = (application as ScrollMateApplication).container
        scope.launch {
            container.settings.update { p ->
                val evening = WindDown.eveningOf(java.time.LocalDateTime.now(container.clock.withZone(ZoneId.systemDefault())), p.windDown)
                p.copy(windDown = p.windDown.copy(pausedForDate = evening))
            }
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Notifier.CHANNEL_WIND_DOWN)
        .setSmallIcon(R.drawable.ic_stat_scrollmate)
        .setContentTitle(getString(R.string.wind_down_service_title))
        .setContentText(getString(R.string.wind_down_service_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.SETTINGS.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private const val NOTIFICATION_ID = 4712
    }
}

/**
 * Tracks the current foreground app from usage events. Package names are kept in memory only
 * and never logged.
 */
private class ForegroundTracker(private val context: Context) {
    private var lastQueryMs = 0L
    private var current: String? = null

    fun reset() {
        lastQueryMs = 0L
        current = null
    }

    fun current(): String? {
        if (!UsageAccess(context).isGranted()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val from = if (lastQueryMs == 0L) now - 60_000L else lastQueryMs - 2_000L
        val events = try {
            usm.queryEvents(from, now)
        } catch (e: SecurityException) {
            return null
        } ?: return current
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.packageName
        }
        lastQueryMs = now
        return current
    }
}
