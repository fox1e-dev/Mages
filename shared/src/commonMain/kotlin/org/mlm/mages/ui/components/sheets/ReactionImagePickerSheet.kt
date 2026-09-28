package org.mlm.mages.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mlm.mages.matrix.ImagePackImageEntry
import org.mlm.mages.matrix.ImagePackSummary
import org.mlm.mages.ui.components.core.EmoteRef
import org.mlm.mages.ui.theme.Spacing

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
                        .heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No images available to react with",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
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

    val modifier = Modifier
        .size(64.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { onClick() }

    val path = previewPath
    if (path != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
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
