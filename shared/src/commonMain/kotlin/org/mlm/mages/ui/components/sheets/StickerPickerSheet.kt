package org.mlm.mages.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import mages.shared.generated.resources.Res
import mages.shared.generated.resources.picker_no_sticker_packs
import mages.shared.generated.resources.sticker_pack_enable_globally
import mages.shared.generated.resources.sticker_pack_disable_globally
import mages.shared.generated.resources.sticker_pack_unencrypted_notice
import org.jetbrains.compose.resources.stringResource
import org.mlm.mages.matrix.ImagePackImageEntry
import org.mlm.mages.matrix.ImagePackSummary
import org.mlm.mages.ui.theme.Spacing

/**
 * Picker for image packs (spec v1.19). Only packs that declare sticker usage, or
 * no usage at all, are shown; packs arrive in source-priority order.
 *
 * The grid is flat with full-span pack headers rather than a column of per-pack
 * rows, so only the images actually on screen are composed and therefore only
 * those are fetched.
 */
@Composable
fun StickerPickerSheet(
    packs: List<ImagePackSummary>,
    isLoading: Boolean,
    isEncryptedRoom: Boolean,
    packIdsBeingUpdated: Set<String>,
    requestPreview: suspend (thumbnailMxcUri: String?, mxcUrl: String) -> String?,
    onSelect: (ImagePackImageEntry) -> Unit,
    onSetPackEnabled: (ImagePackSummary, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val stickerPacks = remember(packs) { packs.filter { it.servesStickers() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                stickerPacks.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.picker_no_sticker_packs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.sm,
                        bottom = Spacing.xl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (isEncryptedRoom) {
                        item(key = "notice", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(Res.string.sticker_pack_unencrypted_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    stickerPacks.forEach { pack ->
                        packHeader(
                            pack = pack,
                            isUpdating = pack.packId in packIdsBeingUpdated,
                            onSetPackEnabled = onSetPackEnabled
                        )
                        items(
                            items = pack.images,
                            key = { "${pack.packId}:${it.shortcode}" }
                        ) { image ->
                            PackImageCell(
                                image = image,
                                requestPreview = requestPreview,
                                onSelect = onSelect
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.packHeader(
    pack: ImagePackSummary,
    isUpdating: Boolean,
    onSetPackEnabled: (ImagePackSummary, Boolean) -> Unit,
) {
    item(key = "header:${pack.packId}", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = pack.displayName ?: pack.sourceRoom,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            pack.attribution?.let { attribution ->
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onSetPackEnabled(pack, !pack.isGlobal) },
                enabled = !isUpdating,
                modifier = Modifier.size(28.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (pack.isGlobal) Icons.Filled.Public else Icons.Outlined.Public,
                        contentDescription = stringResource(
                            if (pack.isGlobal) Res.string.sticker_pack_disable_globally
                            else Res.string.sticker_pack_enable_globally
                        ),
                        tint = if (pack.isGlobal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PackImageCell(
    image: ImagePackImageEntry,
    requestPreview: suspend (String?, String) -> String?,
    onSelect: (ImagePackImageEntry) -> Unit,
) {
    var previewPath by remember(image.mxcUrl) { mutableStateOf<String?>(null) }
    val context = LocalPlatformContext.current

    LaunchedEffect(image.mxcUrl) {
        previewPath = requestPreview(image.thumbnailMxcUri, image.mxcUrl)
    }

    val modifier = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { onSelect(image) }

    val path = previewPath
    if (path != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(path)
                .crossfade(true)
                .build(),
            contentDescription = image.body ?: image.shortcode,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier)
    }
}
