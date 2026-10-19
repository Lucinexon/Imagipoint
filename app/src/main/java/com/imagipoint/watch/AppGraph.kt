package com.imagipoint.watch

import android.app.Application
import android.content.Context
import com.imagipoint.watch.hid.HidManager
import com.imagipoint.watch.session.SessionRuntime
import com.imagipoint.watch.settings.SettingsRepository

/** Small manual dependency graph; no service locator framework is needed for this app. */
class AppGraph(context: Context) {
    val settings = SettingsRepository(context)
    val hid = HidManager(context)
    val session = SessionRuntime()
}

class ImagiPointApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
