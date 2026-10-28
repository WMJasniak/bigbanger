package com.wiktor.bigbanger

import android.content.Context
import android.content.SharedPreferences

/** Persists the user's light/dark theme choice across app restarts (house style -- see
 * update/Updater.kt's UpdateSettings). Defaults to dark, matching the app's original look. */
class ThemeSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()
}
