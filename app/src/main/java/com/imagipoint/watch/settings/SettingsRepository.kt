package com.imagipoint.watch.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences-backed local settings store. No network, account, or cloud copy. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // Write a complete, sanitized snapshot once so missing keys get stable defaults.
        persist(_settings.value)
    }

    val current: AppSettings get() = _settings.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(_settings.value).sanitized()
        persist(updated)
        _settings.value = updated
        return updated
    }

    private fun load(): AppSettings {
        val values = prefs.all.entries
            .filter { it.value is String }
            .associate { it.key to (it.value as String) }
        return SettingsCodec.decode(values)
    }

    private fun persist(settings: AppSettings) {
        val editor = prefs.edit()
        for ((key, value) in SettingsCodec.encode(settings)) editor.putString(key, value)
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "imagipoint_settings"
    }
}
