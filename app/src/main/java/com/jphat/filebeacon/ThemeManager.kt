package com.jphat.filebeacon

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"
    
    enum class Theme(val displayName: String, val value: String) {
        LIGHT("Light", "light"),
        DARK("Dark", "dark"),
        AMOLED("AMOLED Black", "amoled"),
        BLUE("Ocean Blue", "blue"),
        GREEN("Forest Green", "green"),
        PURPLE("Royal Purple", "purple"),
        SUNSET("Sunset Orange", "sunset"),
        SYSTEM("System Default", "system")
    }
    
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getCurrentTheme(context: Context): Theme {
        val prefs = getPreferences(context)
        val themeValue = prefs.getString(KEY_THEME, Theme.SYSTEM.value)
        return Theme.values().find { it.value == themeValue } ?: Theme.SYSTEM
    }
    
    fun setTheme(context: Context, theme: Theme) {
        val prefs = getPreferences(context)
        prefs.edit().putString(KEY_THEME, theme.value).apply()
        applyTheme(theme)
    }
    
    fun applyTheme(theme: Theme) {
        when (theme) {
            Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            Theme.DARK, Theme.AMOLED, Theme.BLUE, Theme.GREEN, Theme.PURPLE, Theme.SUNSET -> 
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Theme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    fun initializeTheme(context: Context) {
        val currentTheme = getCurrentTheme(context)
        applyTheme(currentTheme)
    }
    
    fun getThemeResourceId(theme: Theme): Int {
        return when (theme) {
            Theme.LIGHT -> R.style.Base_Theme_FileBeacon
            Theme.DARK -> R.style.Base_Theme_FileBeacon // Uses night mode
            Theme.AMOLED -> R.style.Theme_FileBeacon_AMOLED
            Theme.BLUE -> R.style.Theme_FileBeacon_Blue
            Theme.GREEN -> R.style.Theme_FileBeacon_Green
            Theme.PURPLE -> R.style.Theme_FileBeacon_Purple
            Theme.SUNSET -> R.style.Theme_FileBeacon_Sunset
            Theme.SYSTEM -> R.style.Base_Theme_FileBeacon
        }
    }
    
    data class ThemeColors(
        val primary: Int,
        val primaryVariant: Int,
        val secondary: Int,
        val background: Int,
        val surface: Int,
        val onPrimary: Int,
        val onSecondary: Int,
        val onBackground: Int,
        val onSurface: Int
    )
    
    fun getThemeColors(theme: Theme): ThemeColors {
        return when (theme) {
            Theme.LIGHT -> ThemeColors(
                primary = 0xFF2196F3.toInt(),
                primaryVariant = 0xFF1976D2.toInt(),
                secondary = 0xFF299B80.toInt(),
                background = 0xFFF5F5F5.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFF000000.toInt(),
                onSurface = 0xFF000000.toInt()
            )
            Theme.DARK -> ThemeColors(
                primary = 0xFF2196F3.toInt(),
                primaryVariant = 0xFF1976D2.toInt(),
                secondary = 0xFF299B80.toInt(),
                background = 0xFF121212.toInt(),
                surface = 0xFF1E1E1E.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFFFFFFF.toInt(),
                onSurface = 0xFFFFFFFF.toInt()
            )
            Theme.AMOLED -> ThemeColors(
                primary = 0xFF2196F3.toInt(),
                primaryVariant = 0xFF1976D2.toInt(),
                secondary = 0xFF299B80.toInt(),
                background = 0xFF000000.toInt(),
                surface = 0xFF000000.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFFFFFFF.toInt(),
                onSurface = 0xFFFFFFFF.toInt()
            )
            Theme.BLUE -> ThemeColors(
                primary = 0xFF0D47A1.toInt(),
                primaryVariant = 0xFF002171.toInt(),
                secondary = 0xFF1976D2.toInt(),
                background = 0xFF0A1929.toInt(),
                surface = 0xFF132F4C.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFE3F2FD.toInt(),
                onSurface = 0xFFBBDEFB.toInt()
            )
            Theme.GREEN -> ThemeColors(
                primary = 0xFF2E7D32.toInt(),
                primaryVariant = 0xFF005005.toInt(),
                secondary = 0xFF66BB6A.toInt(),
                background = 0xFF1B5E20.toInt(),
                surface = 0xFF2E7D32.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFE8F5E9.toInt(),
                onSurface = 0xFFC8E6C9.toInt()
            )
            Theme.PURPLE -> ThemeColors(
                primary = 0xFF6A1B9A.toInt(),
                primaryVariant = 0xFF38006B.toInt(),
                secondary = 0xFF9C27B0.toInt(),
                background = 0xFF4A148C.toInt(),
                surface = 0xFF6A1B9A.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFF3E5F5.toInt(),
                onSurface = 0xFFE1BEE7.toInt()
            )
            Theme.SUNSET -> ThemeColors(
                primary = 0xFFE65100.toInt(),
                primaryVariant = 0xFFAC1900.toInt(),
                secondary = 0xFFFF6F00.toInt(),
                background = 0xFFBF360C.toInt(),
                surface = 0xFFD84315.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                onSecondary = 0xFFFFFFFF.toInt(),
                onBackground = 0xFFFFF3E0.toInt(),
                onSurface = 0xFFFFE0B2.toInt()
            )
            Theme.SYSTEM -> getThemeColors(Theme.LIGHT) // Default to light
        }
    }
}
