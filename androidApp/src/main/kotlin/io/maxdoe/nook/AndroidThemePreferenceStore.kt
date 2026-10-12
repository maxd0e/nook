package io.maxdoe.nook

import android.content.Context

class AndroidThemePreferenceStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun darkThemeOrDefault(default: Boolean): Boolean =
        if (preferences.contains(DARK_THEME)) {
            preferences.getBoolean(DARK_THEME, default)
        } else {
            default
        }

    fun saveDarkTheme(darkTheme: Boolean) {
        preferences.edit().putBoolean(DARK_THEME, darkTheme).commit()
    }

    private companion object {
        const val DARK_THEME = "dark_theme"
        const val PREFERENCES_NAME = "nook_preferences"
    }
}
