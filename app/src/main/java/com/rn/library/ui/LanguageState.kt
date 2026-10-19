package com.rn.library.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LanguageState(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var currentLanguage: Language by mutableStateOf(loadLanguage(prefs))
        private set

    fun setLanguage(language: Language) {
        if (currentLanguage == language) return
        currentLanguage = language
        persistAndApply(appContext, language)
    }

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY = "language"

        fun applyStoredLanguage(context: Context) {
            // The Compose tree applies the stored locale without recreating the Activity.
            storedLanguage(context)
        }

        fun storedLanguage(context: Context): Language =
            loadLanguage(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

        private fun loadLanguage(prefs: android.content.SharedPreferences): Language {
            if (!prefs.contains(KEY)) {
                val deviceLang = java.util.Locale.getDefault().language.lowercase()
                val detected = Language.values().firstOrNull {
                    it.languageTag.equals(deviceLang, ignoreCase = true) ||
                    it.name.equals(deviceLang, ignoreCase = true)
                } ?: Language.ENGLISH
                prefs.edit().putString(KEY, detected.name).apply()
                return detected
            }
            return runCatching {
                val storedName = prefs.getString(KEY, Language.ENGLISH.name) ?: Language.ENGLISH.name
                Language.valueOf(storedName)
            }.getOrDefault(Language.ENGLISH)
        }

        private fun persistAndApply(context: Context, language: Language, persist: Boolean = true) {
            if (persist) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, language.name)
                    .apply()
            }
        }
    }
}
