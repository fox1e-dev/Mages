package org.mlm.mages.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mages.shared.generated.resources.*
import org.mlm.mages.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.Res

@Composable
fun InviteUserDialog(
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null
) {
    var userId by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.PersonAdd, null)
        },
        title = {
            Text(stringResource(Res.string.invite_user))
        },
        text = {
            Column {
                Text(
                    stringResource(Res.string.enter_the_matrix_id_of_the_user_you_want_to_invite),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text(stringResource(Res.string.user_id)) },
                    placeholder = { Text(stringResource(Res.string.user_id_placeholder)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onInvite(userId.trim()) },
                enabled = userId.isNotBlank() && userId.contains("@") && userId.contains(":") && !isLoading
            ) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp)
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