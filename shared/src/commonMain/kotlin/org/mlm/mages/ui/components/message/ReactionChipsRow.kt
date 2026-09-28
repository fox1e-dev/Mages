package org.mlm.mages.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mlm.mages.matrix.ReactionSummary
import org.mlm.mages.ui.components.core.Avatar
import org.mlm.mages.ui.theme.Sizes

enum class ReactionChipStyle {
    Timeline,
    ThreadRoot,
}

@Composable
fun ReactionChipsRow(
    chips: List<ReactionSummary>,
    modifier: Modifier = Modifier,
    style: ReactionChipStyle = ReactionChipStyle.Timeline,
    maxVisible: Int? = null,
    avatarPathsByUserId: Map<String, String> = emptyMap(),
    imagePaths: Map<String, String> = emptyMap(),
    shortcodes: Map<String, String> = emptyMap(),
    showAvatars: Boolean = false,
    onClick: ((String) -> Unit)? = null,
    onLongClick: ((String) -> Unit)? = null,
) {
    if (chips.isEmpty()) return

    val visibleChips = maxVisible?.let { chips.take(it) } ?: chips

    FlowRow(
        modifier = modifier.padding(top = 0.dp),
        horizontalArrangement = Arrangement.spacedBy((-2).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visibleChips.forEach { chip ->
            ReactionChip(
                chip = chip,
                avatarPathsByUserId = avatarPathsByUserId,
                imagePaths = imagePaths,
                shortcodes = shortcodes,
                showAvatars = showAvatars,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    }
}

/**
 * Renders a reaction key, which is either an emoji or, per MSC4027, an mxc URI
 * standing in for an image.
 *
 * The shortcode is optional in the MSC and matrix-sdk-ui's reaction aggregation
 * discards annotation content, so it cannot be read back off the wire. When the
 * key is not a known pack image either, the mxc URI's own last path segment is
 * shown rather than the whole URI, which is what the MSC calls out as the main
 * downside of the design.
 */
@Composable
private fun ReactionKeyLabel(
    key: String,
    imagePath: String?,
    shortcode: String?
) {
    if (!key.startsWith("mxc://")) {
        Text(text = key, fontSize = 16.sp, lineHeight = 16.sp)
        return
    }

    if (imagePath != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imagePath)
                .crossfade(true)
                .build(),
            contentDescription = shortcode,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp)
        )
        return
    }

    Text(
        text = shortcode ?: key.substringAfterLast('/').ifEmpty { "🖼" },
        fontSize = 16.sp,
        lineHeight = 16.sp,
        maxLines = 1
    )
}

@Composable
private fun ReactionChip(
    chip: ReactionSummary,
    avatarPathsByUserId: Map<String, String>,
    imagePaths: Map<String, String>,
    shortcodes: Map<String, String>,
    showAvatars: Boolean = true,
    onClick: ((String) -> Unit)?,
    onLongClick: ((String) -> Unit)?
) {
    val isSelected = chip.mine

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val outlineColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Surface(
        modifier = Modifier.combinedClickable(
            onClick = { onClick?.invoke(chip.key) },
            onLongClick = { onLongClick?.invoke(chip.key) }
        ),
        shape = RoundedCornerShape(percent = 50),
        color = backgroundColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReactionKeyLabel(
                key = chip.key,
                imagePath = imagePaths[chip.key],
                shortcode = shortcodes[chip.key]
            )

            if (showAvatars && chip.userIds.isNotEmpty()) {
                val maxAvatars = 5
                val userIdsToShow = chip.userIds.take(maxAvatars)

                Row(
                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    userIdsToShow.forEach { userId ->
                        Avatar(
                            name = userId,
                            avatarPath = avatarPathsByUserId[userId],
                            size = 20.dp,
//                            modifier = Modifier.border(1.5.dp, outlineColor.copy(alpha = 0.9f), RoundedCornerShape(percent = 50))
                        )
                    }
                }

                if (chip.count > userIdsToShow.size) {
                    Text(
                        text = "+${chip.count - userIdsToShow.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            else {
                Text("${chip.count}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 13.sp)
            }
        }
    }
}
