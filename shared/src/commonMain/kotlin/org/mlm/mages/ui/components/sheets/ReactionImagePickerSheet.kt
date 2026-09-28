package org.mlm.mages.ui.components.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mages.shared.generated.resources.Res
import mages.shared.generated.resources.picker_no_reaction_images
import org.jetbrains.compose.resources.stringResource
import org.mlm.mages.matrix.ImagePackImageEntry
import org.mlm.mages.matrix.ImagePackSummary
import org.mlm.mages.ui.components.core.EmptyState
import org.mlm.mages.ui.components.core.EmoteRef
import org.mlm.mages.ui.components.core.PackImageTile
import org.mlm.mages.ui.theme.Spacing

private val EMPTY_STATE_HEIGHT = 200.dp

/**
 * Picks an image to react with, per MSC4027, where the reaction key is the mxc
 * URI. The shortcode is sent alongside so other clients have a textual name.
 */
@Composable
fun ReactionImagePickerSheet(
    packs: List<ImagePackSummary>,
    resolvePreview: suspend (thumbnailMxcUri: String?, mxcUrl: String) -> String?,
    onImageSelected: (EmoteRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val images = remember(packs) {
        packs.flatMap { pack -> pack.images.map { it to (pack.displayName ?: pack.sourceRoom) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EMPTY_STATE_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.AddReaction,
                        title = stringResource(Res.string.picker_no_reaction_images)
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 64.dp),
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(images, key = { it.first.mxcUrl }) { (image, packName) ->
                    ReactionImageCell(
                        image = image,
                        packName = packName,
                        resolvePreview = resolvePreview,
                        onClick = {
                            onImageSelected(
                                EmoteRef(
                                    mxcUri = image.mxcUrl,
                                    alt = image.body,
                                    title = image.shortcode
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionImageCell(
    image: ImagePackImageEntry,
    packName: String,
    resolvePreview: suspend (String?, String) -> String?,
    onClick: () -> Unit
) {
    var previewPath by remember(image.mxcUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(image.mxcUrl) {
        previewPath = resolvePreview(image.thumbnailMxcUri, image.mxcUrl)
    }

    PackImageTile(
        path = previewPath,
        contentDescription = image.body ?: image.shortcode,
        modifier = Modifier.size(64.dp),
        onClick = onClick
    )
}
