package com.scrollmate.app

import android.app.Application
import com.scrollmate.app.notifications.Notifier
import com.scrollmate.app.winddown.WindDownController
import com.scrollmate.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScrollMateApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        WorkScheduler.ensurePeriodicCheck(this)
        // Resume the evening wind-down if it is configured (no-op otherwise).
        CoroutineScope(Dispatchers.IO).launch {
            WindDownController.sync(this@ScrollMateApplication, container.settings.current())
        }
    }
}
