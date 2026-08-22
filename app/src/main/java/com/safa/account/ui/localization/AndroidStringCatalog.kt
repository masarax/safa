package com.safa.account.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Compatibility layer while the persisted language preference remains EN/BN.
 * Translation values live only in Android locale resources; callers use stable
 * logical keys and Android resource resolution performs the lookup.
 */
object AndroidStringCatalog {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun get(language: String, key: String): String {
        val id = AndroidStringResources.ids[key] ?: return key
        return get(language, id)
    }

    fun get(language: String, @StringRes id: Int, vararg formatArgs: Any): String {
        val context = applicationContext ?: return ""
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(if (language.equals("BN", true)) Locale("bn") else Locale.ENGLISH)
        val localized = context.createConfigurationContext(configuration)
        return if (formatArgs.isEmpty()) localized.getString(id) else localized.getString(id, *formatArgs)
    }
}
