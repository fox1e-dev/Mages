package org.mlm.mages.platform

import io.github.mlmgames.settings.core.PreferenceKind
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import io.github.mlmgames.settings.core.managers.MigrationManager
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
            MigrationManager(
                dataStore = dataStore,
                currentVersion = 3,
                schema = AppSettingsSchema,
            )
                .addValueTransform(
                    fromVersion = 2,
                    toVersion = 3,
                    oldKey = "block_media_previews",
                    oldKind = PreferenceKind.BOOLEAN,
                    newField = "mediaPreviews",
                ) { blocked -> if (blocked == true) "Off" else "On" }
                .migrate()
        }

        val repo = SettingsRepository(dataStore, AppSettingsSchema)
        repository = repo
        return repo
    }
}
