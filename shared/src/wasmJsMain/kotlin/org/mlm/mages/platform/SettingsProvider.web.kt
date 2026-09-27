package org.mlm.mages.platform

import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import io.github.mlmgames.settings.core.managers.Migration
import io.github.mlmgames.settings.core.managers.MigrationManager
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mlm.mages.settings.AppSettings
import org.mlm.mages.settings.AppSettingsSchema

object SettingsProvider {
    private var repository: SettingsRepository<AppSettings>? = null

    fun get(): SettingsRepository<AppSettings> {
        repository?.let { return it }
        val dataStore = createSettingsDataStore("mages_settings")

        // get() is not suspend, so this runs in the background; the flow is reactive.
        CoroutineScope(Dispatchers.Default).launch {
            MigrationManager(dataStore, currentVersion = 3)
                .addMigration(MediaPreviewsModeMigration())
                .migrate()
        }

        val repo = SettingsRepository(dataStore, AppSettingsSchema)
        repository = repo
        return repo
    }
}

private class MediaPreviewsModeMigration : Migration {
    override val fromVersion: Int = 2
    override val toVersion: Int = 3

    override suspend fun migrate(prefs: MutablePreferences) {
        // Settings are stored under both a namespaced and a plain legacy key.
        val old = prefs[booleanPreferencesKey("__kmp_settings_v2__:boolean:20:block_media_previews")]
            ?: prefs[booleanPreferencesKey("block_media_previews")]
            ?: return
        val mode = if (old) "Off" else "On"
        prefs[stringPreferencesKey("__kmp_settings_v2__:enum:14:media_previews")] = mode
        prefs[stringPreferencesKey("media_previews")] = mode
        prefs.remove(booleanPreferencesKey("__kmp_settings_v2__:boolean:20:block_media_previews"))
        prefs.remove(booleanPreferencesKey("block_media_previews"))
    }
}
