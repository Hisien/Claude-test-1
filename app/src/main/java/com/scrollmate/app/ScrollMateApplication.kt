package com.scrollmate.app

import android.app.Application
import com.scrollmate.app.notifications.Notifier
import com.scrollmate.app.work.WorkScheduler

class ScrollMateApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        WorkScheduler.ensurePeriodicCheck(this)
    }
}
