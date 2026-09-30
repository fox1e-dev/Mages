package org.mlm.mages.ui.components.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.*
import org.koin.compose.koinInject
import org.mlm.mages.matrix.MatrixPort
import org.mlm.mages.nav.matrixToUserLink
import org.mlm.mages.platform.ShareContent
import org.mlm.mages.platform.ShareOutcome
import org.mlm.mages.platform.rememberShareHandler
import org.mlm.mages.ui.components.snackbar.SnackbarManager
import org.mlm.mages.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChatSheet(
    matrixPort: MatrixPort,
    onDismiss: () -> Unit,
    onCreateRoom: () -> Unit,
    onOpenDirectory: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()
    val shareHandler = rememberShareHandler()
    val myUserId = remember { matrixPort.whoami() }
    val inviteLink = remember(myUserId) { myUserId?.let(::matrixToUserLink) }
    val copiedLabel = stringResource(Res.string.copied_to_clipboard)
    val shareFailedLabel = stringResource(Res.string.share_failed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                stringResource(Res.string.start_chat),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.sm)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item {
                    ActionListItem(
                        icon = Icons.Default.Add,
                        label = stringResource(Res.string.new_room),
                        description = stringResource(Res.string.create_room_desc),
                        onClick = {
                            onDismiss()
                            onCreateRoom()
                        }
                    )
                }
                item {
                    ActionListItem(
                        icon = Icons.Default.Search,
                        label = stringResource(Res.string.room_directory),
                        description = stringResource(Res.string.browse_directory_desc),
                        onClick = {
                            onDismiss()
                            onOpenDirectory()
                        }
                    )
                }
                if (inviteLink != null) {
                    item {
                        ActionListItem(
                            icon = Icons.Default.Share,
                            label = stringResource(Res.string.invite_to_chat_on_matrix),
                            description = myUserId,
                            onClick = {
                                scope.launch {
                                    when (shareHandler(ShareContent(text = inviteLink))) {
                                        ShareOutcome.Shared -> Unit
                                        ShareOutcome.Copied -> snackbarManager.show(copiedLabel)
                                        ShareOutcome.Failed -> snackbarManager.showError(shareFailedLabel)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
