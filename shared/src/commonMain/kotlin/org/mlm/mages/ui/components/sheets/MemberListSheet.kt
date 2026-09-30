package org.mlm.mages.ui.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mlm.mages.matrix.MemberSummary
import org.mlm.mages.ui.components.core.Avatar
import org.mlm.mages.ui.theme.Sizes
import org.mlm.mages.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.Res

@Composable
fun MemberListSheet(
    members: List<MemberSummary>,
    bannedMembers: List<MemberSummary>,
    isLoading: Boolean,
    myUserId: String?,
    onDismiss: () -> Unit,
    onMemberClick: (MemberSummary) -> Unit,
    onInvite: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.members_count, members.size),
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = onInvite) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(Res.string.invite))
                }
            }
            
            HorizontalDivider()
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.sm)
                ) {
                    items(members, key = { it.userId }) { member ->
                        MemberListItem(
                            member = member,
                            isMe = member.userId == myUserId,
                            onClick = { onMemberClick(member) }
                        )
                    }
                    if (bannedMembers.isNotEmpty()) {
                        item(key = "banned_header") {
                            Text(
                                stringResource(Res.string.banned_count, bannedMembers.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.lg,
                                    vertical = Spacing.sm
                                )
                            )
                        }
                        items(bannedMembers, key = { "banned_${it.userId}" }) { member ->
                            MemberListItem(
                                member = member,
                                isMe = member.userId == myUserId,
                                onClick = { onMemberClick(member) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberListItem(
    member: MemberSummary,
    isMe: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.displayName ?: member.userId,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                )
                if (isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            stringResource(Res.string.you),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                member.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Avatar(
                name = member.displayName ?: member.userId,
                avatarPath = member.avatarUrl,
                size = Sizes.avatarSmall
            )
        },
        trailingContent = {
            MembershipBadge(member.membership)
        },
        modifier = Modifier.clickable(enabled = !isMe) { onClick() }
    )
}

@Composable
private fun MembershipBadge(membership: String) {
    val (color, text) = when (membership.lowercase()) {
        "join" -> MaterialTheme.colorScheme.primary to stringResource(Res.string.member)
        "invite" -> MaterialTheme.colorScheme.tertiary to stringResource(Res.string.invited)
        "ban" -> MaterialTheme.colorScheme.error to stringResource(Res.string.banned)
        "leave" -> MaterialTheme.colorScheme.outline to "Left"
        else -> MaterialTheme.colorScheme.outline to membership
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}