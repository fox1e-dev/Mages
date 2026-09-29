package org.mlm.mages.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.LocaleListCompat
import java.util.Locale

actual object LocalAppLocale {
    private val appLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = appLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> =
        appLocale.provides(value ?: Locale.getDefault().toLanguageTag())
}

fun applyAppLocale(languageTag: String?) {
    val target = languageTag?.let(LocaleListCompat::forLanguageTags)
        ?: LocaleListCompat.getEmptyLocaleList()
    val applied = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (applied != target.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(target)
    }
}
