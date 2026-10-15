package dev.twiview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    System("", R.string.app_language_system),
    Japanese("ja", R.string.lang_name_ja),
    German("de", R.string.lang_name_de),
    English("en", R.string.lang_name_en),
    Spanish("es", R.string.lang_name_es),
    French("fr", R.string.lang_name_fr),
    Italian("it", R.string.lang_name_it),
    Portuguese("pt", R.string.lang_name_pt),
    Turkish("tr", R.string.lang_name_tr),
    Russian("ru", R.string.lang_name_ru),
    Korean("ko", R.string.lang_name_ko);

    companion object {
        private val languages get() = entries.filter { it != System }

        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: System
        }

        fun streamDefault(locale: Locale = Locale.getDefault()): String? =
            languages.firstOrNull { it.tag == locale.language }?.tag

        fun set(lang: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (lang == System) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang.tag),
            )
        }
    }
}

@SuppressLint("StaticFieldLeak")
object Strings {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val res = resources() ?: return ""
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    private fun resources(): Resources? {
        val ctx = app ?: return null
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ctx.resources
        val conf = Configuration(ctx.resources.configuration)
        conf.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return ctx.createConfigurationContext(conf).resources
    }
}
