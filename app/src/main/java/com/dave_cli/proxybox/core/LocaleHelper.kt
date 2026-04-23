package com.dave_cli.proxybox.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    private const val PREF_NAME = "proxybox_prefs"
    private const val KEY_LANGUAGE = "app_language"

    // "", "en", "ru"
    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
    }

    fun saveLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    fun applyLocale(context: Context): Context {
        val lang = getSavedLanguage(context)
        if (lang.isEmpty()) return context
        return updateResources(context, lang)
    }

    fun applyLocaleToActivity(activity: android.app.Activity) {
        val lang = getSavedLanguage(activity)
        if (lang.isEmpty()) {
            val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0]
            Locale.setDefault(systemLocale)
            val config = Configuration(activity.resources.configuration)
            config.setLocale(systemLocale)
            config.setLocales(LocaleList(systemLocale))
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        } else {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(activity.resources.configuration)
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        }
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    fun getDisplayName(langCode: String): String = when (langCode) {
        "en" -> "English"
        "ru" -> "Русский"
        else -> "System"
    }
}
