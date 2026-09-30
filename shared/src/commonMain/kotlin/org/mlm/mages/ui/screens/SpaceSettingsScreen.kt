package org.mlm.mages.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mlm.mages.RoomSummary
import org.mlm.mages.matrix.RoomJoinRule
import org.mlm.mages.matrix.SpaceChildInfo
import org.mlm.mages.matrix.SpaceInfo
import org.koin.compose.koinInject
import org.mlm.mages.ui.components.sheets.JoinRuleSpacePickerSheet
import org.mlm.mages.ui.components.sheets.MemberActionsSheet
import org.mlm.mages.ui.components.sheets.MemberListSheet
import org.mlm.mages.ui.components.sheets.PowerLevelsSheet
import org.mlm.mages.ui.ActionAvailabilityUi
import org.mlm.mages.ui.components.snackbar.SnackbarManager
import org.mlm.mages.ui.components.core.Avatar
import org.mlm.mages.ui.components.snackbar.snackbarHost
import org.mlm.mages.ui.components.snackbar.rememberErrorPoster
import org.mlm.mages.ui.theme.Spacing
import org.mlm.mages.ui.viewmodel.SpaceSettingsViewModel
import mages.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.Res
import androidx.compose.runtime.Composable

@Composable
fun SpaceSettingsScreen(
    viewModel: SpaceSettingsViewModel,
    onBack: () -> Unit,
    onLeaveSuccess: () -> Unit = onBack
) {
    val state by viewModel.state.collectAsState()
    val snackbarManager: SnackbarManager = koinInject()
    val postError = rememberErrorPoster(snackbarManager)

    LaunchedEffect(state.error) {
        state.error?.let {
            postError(it)
            viewModel.clearError()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.space_settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh))
                    }
                }
            )
        },
        snackbarHost = { snackbarManager.snackbarHost() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Loading indicator
            AnimatedVisibility(visible = state.isLoading || state.isSaving) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Spacing.md)
            ) {
                // Space info header
                state.space?.let { space ->
                    item(key = "header") {
                        SpaceInfoHeader(space = space, avatarPath = state.spaceAvatarPath)
                    }
                }

                // Actions
                item(key = "actions_title") {
                    SectionTitle(stringResource(Res.string.actions))
                }

                if (state.canEditDetails) {
                    item(key = "action_edit_details") {
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.edit_details)) },
                            supportingContent = { Text(stringResource(Res.string.name_topic_and_address)) },
                            leadingContent = {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable(enabled = !state.isSaving) {
                                viewModel.showEditDetailsDialog()
                            }
                        )
                    }
                }

                item(key = "action_people") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.people)) },
                        supportingContent = { Text(stringResource(Res.string.n_members, state.members.size)) },
                        leadingContent = {
                            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable { viewModel.showPeople() }
                    )
                }

                if (state.canManageSettings) {
                    item(key = "action_roles") {
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.roles_and_permissions)) },
                            supportingContent = { Text(stringResource(Res.string.who_can_change_what)) },
                            leadingContent = {
                                Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable(enabled = !state.isSaving) {
                                viewModel.showRoles()
                            }
                        )
                    }
                }

                item(key = "action_new_room") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.new_room_in_this_space)) },
                        supportingContent = { Text(stringResource(Res.string.create_a_room_inside_this_space)) },
                        leadingContent = {
                            Icon(Icons.Default.AddComment, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable(enabled = !state.isSaving) {
                            viewModel.showCreateRoom()
                        }
                    )
                }

                item(key = "action_add_room") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.add_rooms)) },
                        supportingContent = { Text(stringResource(Res.string.add_existing_rooms_to_this_space)) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Add,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable(enabled = !state.isSaving) {
                            viewModel.showAddRoomDialog()
                        }
                    )
                }

                item(key = "action_invite") {
                    if (state.canInvite || !state.canManageSettings) {
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.invite_users)) },
                            supportingContent = { Text(stringResource(Res.string.invite_users_to_this_space)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable(enabled = !state.isSaving) {
                                viewModel.showInviteDialog()
                            }
                        )
                    }
                }

                item(key = "action_leave") {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.leave_space), color = MaterialTheme.colorScheme.error)
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable(enabled = !state.isSaving) {
                            viewModel.showLeaveWithChildren()
                        }
                    )
                }

                item(key = "divider") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                }

                if (state.canManageSettings) {
                    item(key = "security_title") {
                        SectionTitle(stringResource(Res.string.who_can_join))
                    }
                    item(key = "security_join_rule") {
                        var expanded by remember { mutableStateOf(false) }
                        val rule = state.joinRule ?: RoomJoinRule.Invite
                        val allowCount = state.joinRuleAllowedSpaceIds.size
                        val subtitle = when (rule) {
                            RoomJoinRule.Public -> stringResource(Res.string.anyone_can_join)
                            RoomJoinRule.Invite -> stringResource(Res.string.invite_only)
                            RoomJoinRule.Knock -> stringResource(Res.string.ask_to_join)
                            RoomJoinRule.Restricted -> stringResource(Res.string.space_members_can_join)
                            RoomJoinRule.KnockRestricted -> stringResource(Res.string.ask_to_join_with_space_members)
                        }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.padding(horizontal = Spacing.lg)
                        ) {
                            OutlinedTextField(
                                value = if (allowCount > 0 &&
                                    (rule == RoomJoinRule.Restricted || rule == RoomJoinRule.KnockRestricted)
                                ) stringResource(Res.string.rule_n_spaces_allowed, rule, allowCount) else "$rule ($subtitle)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.access)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                RoomJoinRule.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayNameForSpace()) },
                                        onClick = {
                                            expanded = false
                                            viewModel.requestJoinRule(option)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item(key = "divider2") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                    }
                }

                // Children
                item(key = "children_title") {
                    SectionTitle(stringResource(Res.string.rooms_in_space_count, state.children.size))
                }

                if (state.children.isEmpty() && !state.isLoading) {
                    item(key = "empty") {
                        Text(
                            stringResource(Res.string.no_rooms_in_this_space_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.lg)
                        )
                    }
                }

                items(state.children, key = { it.roomId }) { child ->
                    val resolvedAvatar = state.avatarPathByRoomId[child.roomId] ?: child.avatarUrl
                    ChildRoomItem(
                        child = child.copy(avatarUrl = resolvedAvatar),
                        onRemove = { viewModel.removeChild(child.roomId) },
                        isRemoving = state.isSaving
                    )
                }
            }
        }
    }

    // Add room dialog
    if (state.showAddRoom) {
        AddRoomDialog(
            availableRooms = state.addableRooms,
            onAdd = { roomId, suggested -> viewModel.addChild(roomId, suggested) },
            onDismiss = viewModel::hideAddRoomDialog
        )
    }

    // Invite user dialog
    if (state.showInviteUser) {
        InviteUserToSpaceDialog(
            userId = state.inviteUserId,
            onUserIdChange = viewModel::setInviteUserId,
            onInvite = viewModel::inviteUser,
            onDismiss = viewModel::hideInviteDialog,
            isSaving = state.isSaving
        )
    }

    if (state.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::hideLeaveConfirm,
            title = { Text(stringResource(Res.string.leave_space)) },
            text = { Text(stringResource(Res.string.are_you_sure_you_want_to_leave_this_space)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::leaveSpace,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.leave)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideLeaveConfirm) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    // Edit details dialog
    if (state.showEditDetails) {
        AlertDialog(
            onDismissRequest = viewModel::hideEditDetailsDialog,
            title = { Text(stringResource(Res.string.edit_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.editName,
                        onValueChange = viewModel::setEditName,
                        label = { Text(stringResource(Res.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.editTopic,
                        onValueChange = viewModel::setEditTopic,
                        label = { Text(stringResource(Res.string.topic)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.editAlias,
                        onValueChange = viewModel::setEditAlias,
                        label = { Text(stringResource(Res.string.address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveEditDetails, enabled = !state.isSaving) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideEditDetailsDialog) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    // Create room in space dialog
    if (state.showCreateRoom) {
        AlertDialog(
            onDismissRequest = viewModel::hideCreateRoom,
            title = { Text(stringResource(Res.string.new_room_action)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.newRoomName,
                        onValueChange = viewModel::setNewRoomName,
                        label = { Text(stringResource(Res.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.newRoomTopic,
                        onValueChange = viewModel::setNewRoomTopic,
                        label = { Text(stringResource(Res.string.topic)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.newRoomIsPublic,
                            onCheckedChange = viewModel::setNewRoomIsPublic
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(Res.string.make_this_room_public))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::createRoomInSpace, enabled = !state.isSaving) { Text(stringResource(Res.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideCreateRoom) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    // Leave space with children dialog
    if (state.showLeaveWithChildren) {
        AlertDialog(
            onDismissRequest = viewModel::hideLeaveWithChildren,
            title = { Text(stringResource(Res.string.leave_space)) },
            text = {
                Column {
                    Text(stringResource(Res.string.select_any_rooms_to_leave_as_well))
                    Spacer(Modifier.height(Spacing.sm))
                    state.joinedChildren.forEach { child ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = child.roomId in state.selectedChildIds,
                                onCheckedChange = { viewModel.toggleChildSelection(child.roomId) }
                            )
                            Text(
                                child.name ?: child.roomId,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::leaveSpaceWithChildren,
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.leave)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideLeaveWithChildren) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    // People sheet
    if (state.showPeople) {
        MemberListSheet(
            members = state.members,
            bannedMembers = state.bannedMembers,
            isLoading = state.isLoading,
            myUserId = state.myUserId,
            onDismiss = viewModel::hidePeople,
            onMemberClick = { viewModel.selectMember(it) },
            onInvite = { viewModel.hidePeople(); viewModel.showInviteDialog() }
        )
    }

    // Per-member actions (kick / ban / unban / ignore) for a tapped member
    state.selectedMember?.let { member ->
        val moderation = if (state.canManageSettings) ActionAvailabilityUi.Enabled else ActionAvailabilityUi()
        MemberActionsSheet(
            member = member,
            onDismiss = viewModel::clearSelectedMember,
            dmAction = ActionAvailabilityUi(),
            kickAction = moderation,
            banAction = moderation,
            unbanAction = moderation,
            onStartDm = { viewModel.clearSelectedMember() },
            onKick = { reason -> viewModel.kickMember(member.userId, reason) },
            onBan = { reason -> viewModel.banMember(member.userId, reason) },
            onUnban = { reason -> viewModel.unbanMember(member.userId, reason) },
            onIgnore = { viewModel.ignoreMember(member.userId) },
            isBanned = member.membership == "ban"
        )
    }

    // Roles sheet
    if (state.showRoles) {
        PowerLevelsSheet(
            members = state.members,
            powerLevels = state.powerLevels,
            myPowerLevel = state.myPowerLevel,
            onUpdatePowerLevel = viewModel::updateMemberRole,
            onDismiss = viewModel::hideRoles
        )
    }

    // Join rule space picker
    state.pendingJoinRule?.let { pending ->
        if (state.showJoinRulePicker) {
            JoinRuleSpacePickerSheet(
                rule = pending,
                spaces = state.selectableSpaces,
                initiallyAllowedSpaceIds = state.joinRuleAllowedSpaceIds,
                onSave = { rule, ids -> viewModel.setJoinRule(rule, ids) },
                onDismiss = viewModel::hideJoinRuleSpacePicker
            )
        }
    }
}

@Composable
private fun SpaceInfoHeader(space: SpaceInfo, avatarPath: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = space.name,
                avatarPath = avatarPath,
                size = 48.dp
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    space.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(Res.string.n_members, space.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
}

@Composable
private fun ChildRoomItem(
    child: SpaceChildInfo,
    onRemove: () -> Unit,
    isRemoving: Boolean
) {
    val displayName = child.name ?: child.alias ?: child.roomId
    ListItem(
        headlineContent = {
            Text(
                displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = child.topic?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Avatar(
                name = displayName,
                avatarPath = child.avatarUrl,
                size = 40.dp,
                shape = MaterialTheme.shapes.small
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = !isRemoving) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    stringResource(Res.string.remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun RoomJoinRule.displayNameForSpace(): String = when (this) {
    RoomJoinRule.Public -> stringResource(Res.string.public_anyone_can_join)
    RoomJoinRule.Invite -> stringResource(Res.string.invite_only)
    RoomJoinRule.Knock -> stringResource(Res.string.knock_ask_to_join)
    RoomJoinRule.Restricted -> stringResource(Res.string.space_members_can_join)
    RoomJoinRule.KnockRestricted -> stringResource(Res.string.ask_to_join_with_space_members)
}

@Composable
private fun AddRoomDialog(
    availableRooms: List<RoomSummary>,
    onAdd: (roomId: String, suggested: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRoom by remember { mutableStateOf<RoomSummary?>(null) }
    var suggested by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_room_to_space)) },
        text = {
            Column {
                if (availableRooms.isEmpty()) {
                    Text(
                        stringResource(Res.string.all_your_rooms_are_already_in_this_space),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(availableRooms, key = { it.id }) { room ->
                            ListItem(
                                headlineContent = { Text(room.name) },
                                supportingContent = {
                                    Text(
                                        room.id,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    RadioButton(
                                        selected = selectedRoom?.id == room.id,
                                        onClick = { selectedRoom = room }
                                    )
                                },
                                modifier = Modifier.clickable { selectedRoom = room }
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = suggested,
                            onCheckedChange = { suggested = it }
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(Res.string.mark_as_suggested))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedRoom?.let { onAdd(it.id, suggested) } },
                enabled = selectedRoom != null
            ) {
                Text(stringResource(Res.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun InviteUserToSpaceDialog(
    userId: String,
    onUserIdChange: (String) -> Unit,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean
) {
    val isValid = userId.startsWith("@") && ":" in userId && userId.length > 3

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, null) },
        title = { Text(stringResource(Res.string.invite_user_to_space)) },
        text = {
            OutlinedTextField(
                value = userId,
                onValueChange = onUserIdChange,
                label = { Text(stringResource(Res.string.user_id)) },
                placeholder = { Text(stringResource(Res.string.user_id_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                isError = userId.isNotBlank() && !isValid
            )
        },
        confirmButton = {
            Button(
                onClick = onInvite,
                enabled = isValid && !isSaving
            ) {
                if (isSaving) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(stringResource(Res.string.invite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
