package com.miaohui.app.data

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {

    private const val PREFS_NAME = "miaohui_settings"
    private const val KEY_API_URL = "api_base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private const val KEY_CACHED_MODELS = "cached_models"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiBaseUrl(context: Context): String =
        getPrefs(context).getString(KEY_API_URL, "") ?: ""

    fun setApiBaseUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_API_URL, url.trimEnd('/')).apply()
    }

    fun getApiKey(context: Context): String =
        getPrefs(context).getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getModelName(context: Context): String =
        getPrefs(context).getString(KEY_MODEL, "gpt-image-2") ?: "gpt-image-2"

    fun setModelName(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun getCachedModels(context: Context): String =
        getPrefs(context).getString(KEY_CACHED_MODELS, "") ?: ""

    fun setCachedModels(context: Context, models: String) {
        getPrefs(context).edit().putString(KEY_CACHED_MODELS, models).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getApiBaseUrl(context).isNotEmpty() && getApiKey(context).isNotEmpty()
    }
}
