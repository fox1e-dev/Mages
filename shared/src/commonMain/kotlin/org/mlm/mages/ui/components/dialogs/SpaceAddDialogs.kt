package org.mlm.mages.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mlm.mages.RoomSummary
import org.mlm.mages.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.*

@Composable
fun AddRoomToSpaceDialog(
    availableRooms: List<RoomSummary>,
    isSaving: Boolean,
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
                enabled = selectedRoom != null && !isSaving
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
fun InviteUserToSpaceDialog(
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

@Composable
fun CreateRoomInSpaceDialog(
    name: String,
    topic: String,
    isPublic: Boolean,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_room_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = onTopicChange,
                    label = { Text(stringResource(Res.string.topic)) },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isPublic,
                        onCheckedChange = onPublicChange,
                        enabled = !isSaving
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(Res.string.make_this_room_public))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate, enabled = !isSaving) {
                Text(stringResource(Res.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
