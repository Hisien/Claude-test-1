package com.scrollmate.app.winddown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.scrollmate.app.ScrollMateApplication
import com.scrollmate.app.data.UsageAccess
import com.scrollmate.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts or stops the wind-down service so that it only runs when it can actually work. */
object WindDownController {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when everything the wind-down overlay needs is in place. */
    fun isReady(context: Context, prefs: UserPreferences): Boolean =
        prefs.windDown.enabled &&
            prefs.windDown.blockedApps.isNotEmpty() &&
            canDrawOverlays(context) &&
            UsageAccess(context).isGranted()

    fun sync(context: Context, prefs: UserPreferences) {
        val intent = Intent(context, WindDownService::class.java)
        if (isReady(context, prefs)) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Starting a foreground service from the background can be refused by the system;
                // it is started again the next time ScrollMate is opened.
            }
        } else {
            context.stopService(intent)
        }
    }
}

/** Re-starts the wind-down service after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as ScrollMateApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WindDownController.sync(app, app.container.settings.current())
            } finally {
                pending.finish()
            }
        }
    }
}
