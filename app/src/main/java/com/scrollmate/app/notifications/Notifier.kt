package com.scrollmate.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scrollmate.app.R
import com.scrollmate.app.data.UsageAccess
import com.scrollmate.app.ui.CoachTexts
import com.scrollmate.app.ui.MainActivity
import com.scrollmate.app.ui.Tab
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.model.CoachVoice
import com.scrollmate.core.rules.CoachTrigger
import java.time.LocalTime

/**
 * Posts calm, low-urgency notifications. Without notification permission nothing is posted;
 * the same messages are then shown in the Coach tab only.
 */
class Notifier(private val context: Context) {

    fun canPost(): Boolean = UsageAccess(context).notificationsAllowed()

    fun postCoach(trigger: CoachTrigger, labels: Map<String, String>, isDemo: Boolean, voice: CoachVoice, bedtime: LocalTime) {
        val text = CoachTexts.render(context.resources, trigger.message, labels, voice, bedtime)
        val title = if (isDemo) context.getString(R.string.notification_demo_prefix, text.title) else text.title
        post(trigger.key.hashCode(), title, text.body, Tab.COACH)
    }

    fun postFocusEnded(message: CoachMessage, voice: CoachVoice) {
        val text = CoachTexts.render(context.resources, message, voice = voice)
        post(FOCUS_NOTIFICATION_ID, text.title, text.body, Tab.FOCUS)
    }

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(id: Int, title: String, body: String, tab: Tab) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab.name)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_COACH)
            .setSmallIcon(R.drawable.ic_stat_scrollmate)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_COACH = "coach"
        const val CHANNEL_WIND_DOWN = "wind_down"
        private const val FOCUS_NOTIFICATION_ID = 4711

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_COACH,
                context.getString(R.string.notification_channel_coach),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_coach_desc) }
            manager.createNotificationChannel(channel)
            val windDown = NotificationChannel(
                CHANNEL_WIND_DOWN,
                context.getString(R.string.notification_channel_wind_down),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_wind_down_desc) }
            manager.createNotificationChannel(windDown)
        }
    }
}
