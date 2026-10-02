package org.mlm.mages.ui.components.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mlm.mages.ui.theme.Sizes

private const val InitialsFontSizeRatio = 0.45f
private const val InitialsLineHeightRatio = 0.55f


@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarPath: String?,
    size: Dp = Sizes.avatarSmall,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: (() -> Unit)? = null
) {
    val initials = rememberSaveable(name) { extractInitials(name) }
    val ctx = LocalPlatformContext.current
    var showImage by remember(avatarPath) { mutableStateOf(!avatarPath.isNullOrBlank()) }

    Surface(
        color = containerColor,
        shape = shape,
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = if (onClick == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .clickable { onClick?.invoke() }
            },
            contentAlignment = Alignment.Center
        ) {
            if (showImage && !avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(avatarPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.fillMaxSize(),
                    onError = { showImage = false },
                )
            } else {
                val style: TextStyle = when {
                    size >= Sizes.avatarLarge -> MaterialTheme.typography.titleLarge
                    size >= Sizes.avatarMedium -> MaterialTheme.typography.titleMedium
                    size >= 24.dp -> MaterialTheme.typography.labelLarge
                    else -> with(LocalDensity.current) {
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = (size * InitialsFontSizeRatio).toSp(),
                            lineHeight = (size * InitialsLineHeightRatio).toSp(),
                            letterSpacing = 0.sp
                        )
                    }
                }
                Text(
                    text = initials,
                    style = style,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Extracts initials from a name or Matrix ID.
 * @user:server.com -> U
 * Display Name -> DN
 * single -> S
 */
fun extractInitials(name: String): String {
    val clean = name.trim()

    // Handle Matrix IDs
    if (clean.startsWith("@")) {
        val localpart = clean.substringAfter("@").substringBefore(":")
        return localpart.take(2).uppercase()
    }

    // Handle display names
    val words = clean.split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

/**
 * Formats a Matrix ID to a display name.
 * @user:server.com -> user
 */
fun formatDisplayName(mxid: String): String {
    return mxid.substringAfter("@").substringBefore(":")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
