package org.mlm.mages.matrix

import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.remote.RemoteBinding
import io.github.mlmgames.settings.core.remote.RemotePullPolicy
import io.github.mlmgames.settings.core.remote.RemoteSettingsStore
import io.github.mlmgames.settings.core.remote.RemoteUnsupportedException
import io.github.mlmgames.settings.core.remote.SettingsRemoteSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.mlm.mages.settings.AppSettings
import org.mlm.mages.settings.AppSettingsSchema

const val MEDIA_PREVIEWS_FIELD = "mediaPreviews"

/** MSC4278 account data, so the media preview choice follows the account. */
class MatrixRemoteSettingsStore(
    private val port: () -> MatrixPort,
) : RemoteSettingsStore {
    /**
     * There is no account data change notification, so nothing arrives here after the initial
     * read. A change made on another client lands the next time the account is opened.
     */
    override val changes: Flow<Map<String, String>> = emptyFlow()

    /**
     * A null [MatrixPort.mediaPreviewConfig] means the account never set one, which is absent
     * rather than "On", and must not overwrite the local choice. A failure is left to
     * propagate so the sync reports it instead of mirroring a value it never read.
     */
    override suspend fun read(fields: List<String>): Map<String, String> {
        if (fields.isEmpty()) return emptyMap()
        fields.forEach { require(it == MEDIA_PREVIEWS_FIELD) { "Unsupported remote field: $it" } }

        val config = port().mediaPreviewConfig()
        return config?.let { mapOf(MEDIA_PREVIEWS_FIELD to it.name) } ?: emptyMap()
    }

    override suspend fun write(field: String, value: String) {
        require(field == MEDIA_PREVIEWS_FIELD) { "Unsupported remote field: $field" }
        // The sync round-trips the settings enum's entry name, so [MediaPreviewMode] has to
        // name its entries the same way or this throws rather than writing a wrong value.
        val mode = runCatching { MediaPreviewMode.valueOf(value) }
            .getOrElse { throw RemoteUnsupportedException("Unknown media preview mode: $value") }
        port().setMediaPreviewConfig(mode).getOrThrow()
    }
}

fun mediaPreviewSettingsSync(
    repository: SettingsRepository<AppSettings>,
    port: () -> MatrixPort,
): SettingsRemoteSync<AppSettings> = SettingsRemoteSync(
    repository = repository,
    schema = AppSettingsSchema,
    store = MatrixRemoteSettingsStore(port),
    bindings = listOf(RemoteBinding(field = MEDIA_PREVIEWS_FIELD)),
    pullPolicy = RemotePullPolicy.ONCE_PER_ATTACH,
)
