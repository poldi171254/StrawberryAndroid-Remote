package com.zudiewiener.strawberryremote.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.zudiewiener.strawberryremote.util.ColumnInfo
import com.zudiewiener.strawberryremote.util.ConnectionState
import com.zudiewiener.strawberryremote.util.PlaylistTab
import com.zudiewiener.strawberryremote.util.QueueRowData
import com.zudiewiener.strawberryremote.util.SharedViewModel
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestNextTrack
import nw.remote.RequestPause
import nw.remote.RequestPlay
import nw.remote.RequestPreviousTrack
import android.util.Log

// Fixed row/column sizing keeps the screen-space calculation pure arithmetic
// rather than needing a fragile multi-pass content measurement.
private val ROW_HEIGHT = 48.dp
private val HEADER_ROW_HEIGHT = 36.dp
private val COLUMN_WIDTH = 130.dp
private val MIN_COLUMN_WIDTH = 80.dp
private const val MAX_PREVIOUS_ROWS_SHOWN = 2

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val remainingTime by sharedViewModel.remainingTime.collectAsState()
    val playerStatus by sharedViewModel.playerStatus.collectAsState()
    val connectionState by sharedViewModel.connectionState.collectAsState()
    val serverShutdown by sharedViewModel.serverShutdown.collectAsState()

    val playlists by sharedViewModel.playlists.collectAsState()
    val activePlaylistIndex by sharedViewModel.activePlaylistIndex.collectAsState()
    val viewedPlaylistIndex by sharedViewModel.viewedPlaylistIndex.collectAsState()
    val columns by sharedViewModel.columns.collectAsState()
    val previousRows by sharedViewModel.previousRows.collectAsState()
    val currentRow by sharedViewModel.currentRow.collectAsState()
    val upcomingRows by sharedViewModel.upcomingRows.collectAsState()
    val actionError by sharedViewModel.actionError.collectAsState()

    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Message for the modal shutdown dialog; null means no dialog.
    var shutdownMessage by remember { mutableStateOf<String?>(null) }

    // React to connection loss.
    //  - Strawberry closed on the desktop: modal dialog, OK exits the app,
    //    since there is nothing left to reconnect to.
    //  - Anything else (rejection, network loss): brief message, then back to
    //    the connect screen so the user can try again.
    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Error -> {
                Log.d("SongInfoScreen", "Error: ${state.message}, serverShutdown=$serverShutdown")
                if (serverShutdown) {
                    shutdownMessage = state.message
                } else {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                    navController.navigate("connect") {
                        popUpTo("connect") { inclusive = true }
                    }
                }
            }
            is ConnectionState.Disconnected -> {
                Log.d("SongInfoScreen", "Disconnected state")
                navController.navigate("connect") {
                    popUpTo("connect") { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    // Failed add/remove actions surface as a one-shot snackbar.
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            sharedViewModel.consumeActionError()
        }
    }

    // Lifecycle observer — requests fresh song info on resume from sleep/background
    // Also handles disconnect when leaving the screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharedViewModel.requestSongInfo()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sharedViewModel.disconnect()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PlaylistTabsRow(
                playlists = playlists,
                selectedIndex = viewedPlaylistIndex,
                activeIndex = activePlaylistIndex,
                onSelect = { index -> sharedViewModel.selectPlaylistTab(index) }
            )

            // Queue table fills whatever vertical space remains between the
            // tabs and the status/controls below - this is what makes the
            // row count device-independent rather than a fixed guess.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val availableHeight = this.maxHeight
                val totalRows = ((availableHeight - HEADER_ROW_HEIGHT) / ROW_HEIGHT)
                    .toInt()
                    .coerceAtLeast(1)
                val previousToShow = minOf(
                    previousRows.size,
                    MAX_PREVIOUS_ROWS_SHOWN,
                    (totalRows - 1).coerceAtLeast(0)
                )
                val upcomingCountWanted = (totalRows - previousToShow - 1).coerceAtLeast(0)

                // Spreads columns evenly across the available width when few
                // enough to fit; falls back to a fixed minimum (triggering
                // horizontal scroll) once there are too many to fit evenly.
                val columnWidth = if (columns.isNotEmpty()) {
                    maxOf(this.maxWidth / columns.size, MIN_COLUMN_WIDTH)
                } else {
                    COLUMN_WIDTH
                }

                // Re-requests whenever the measured space changes (rotation,
                // split-screen resize, different device) or the viewed
                // playlist changes - this is the single place upcomingCount
                // is decided.
                LaunchedEffect(upcomingCountWanted, viewedPlaylistIndex) {
                    if (viewedPlaylistIndex >= 0) {
                        sharedViewModel.refreshVisibleQueue(upcomingCountWanted)
                    }
                }

                QueueTable(
                    columns = columns,
                    columnWidth = columnWidth,
                    previousRows = previousRows.takeLast(previousToShow),
                    currentRow = currentRow,
                    upcomingRows = upcomingRows,
                    playlists = playlists,
                    viewedPlaylistIndex = viewedPlaylistIndex,
                    onPlayRow = { rowIndex -> sharedViewModel.requestPlaySong(rowIndex) },
                    onAddCurrentToPlaylist = { targetId, newName ->
                        sharedViewModel.addCurrentSongToPlaylist(targetId, newName)
                    },
                    onRemoveRow = { rowIndex -> sharedViewModel.removeSongFromPlaylist(rowIndex) }
                )
            }

            // Status, remaining time, and playback controls combined into one
            // compact row - this used to be three stacked rows plus a
            // full-width Exit button, which left almost no vertical space for
            // the queue table in landscape (short screen height).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerStatus,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = remainingTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaybackIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PREVIOUS)
                                    .setRequestPreviousTrack(
                                        RequestPreviousTrack.newBuilder()
                                            .setPrevious(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                                    .setRequestPlay(
                                        RequestPlay.newBuilder().setPlay(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PAUSE)
                                    .setRequestPause(
                                        RequestPause.newBuilder().setPause(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_NEXT)
                                    .setRequestNextTrack(
                                        RequestNextTrack.newBuilder().setNext(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Exit",
                        containerColor = MaterialTheme.colorScheme.outline,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { activity?.finish() }
                    )
                }
            }
        }
    }

    // Modal dialog shown only when Strawberry itself has shut down.
    // Not dismissable by back-press or tapping outside: OK closes the app.
    shutdownMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Disconnected") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistTabsRow(
    playlists: List<PlaylistTab>,
    selectedIndex: Int,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    if (playlists.isEmpty()) return
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceAtLeast(0),
        edgePadding = 8.dp
    ) {
        playlists.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = tab.name,
                        fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueTable(
    columns: List<ColumnInfo>,
    columnWidth: Dp,
    previousRows: List<QueueRowData>,
    currentRow: QueueRowData?,
    upcomingRows: List<QueueRowData>,
    playlists: List<PlaylistTab>,
    viewedPlaylistIndex: Int,
    onPlayRow: (Int) -> Unit,
    onAddCurrentToPlaylist: (Int, String) -> Unit,
    onRemoveRow: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    // Which row (by rowIndex) currently has its context menu open, if any.
    // Also tracks whether that row is the current (bold) row, since only the
    // current row offers "Add to another playlist".
    var menuForRow by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // Per-column alignment comes straight from the server's own knowledge of
    // each Playlist::Column's nature (numeric/measurement-like vs. free
    // text) - no need to guess from formatted cell content.
    val columnCentered = remember(columns) { columns.map { it.isNumeric } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_ROW_HEIGHT)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEachIndexed { index, column ->
                    Text(
                        text = column.name,
                        modifier = Modifier
                            .width(columnWidth)
                            .padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (columnCentered.getOrElse(index) { false }) TextAlign.Center else TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            var zebraIndex = 0

            for (row in previousRows) {
                QueueRow(
                    row = row,
                    columnCount = columns.size,
                    columnWidth = columnWidth,
                    columnCentered = columnCentered,
                    isCurrent = false,
                    isPrevious = true,
                    isActionable = false,
                    zebraIndex = zebraIndex++,
                    scrollState = scrollState,
                    onDoubleTap = {},
                    onLongPress = {}
                )
            }

            currentRow?.let { row ->
                QueueRow(
                    row = row,
                    columnCount = columns.size,
                    columnWidth = columnWidth,
                    columnCentered = columnCentered,
                    isCurrent = true,
                    isPrevious = false,
                    isActionable = true,
                    zebraIndex = zebraIndex++,
                    scrollState = scrollState,
                    onDoubleTap = { onPlayRow(row.rowIndex) },
                    onLongPress = { menuForRow = row.rowIndex to true }
                )
            }

            for (row in upcomingRows) {
                QueueRow(
                    row = row,
                    columnCount = columns.size,
                    columnWidth = columnWidth,
                    columnCentered = columnCentered,
                    isCurrent = false,
                    isPrevious = false,
                    isActionable = true,
                    zebraIndex = zebraIndex++,
                    scrollState = scrollState,
                    onDoubleTap = { onPlayRow(row.rowIndex) },
                    onLongPress = { menuForRow = row.rowIndex to false }
                )
            }
        }

        HorizontalScrollIndicators(scrollState)
    }

    // Top-level menu: exactly two options on the current row, one on upcoming rows.
    menuForRow?.let { (rowIndex, isCurrentRow) ->
        Box {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuForRow = null }
            ) {
                if (isCurrentRow) {
                    DropdownMenuItem(
                        text = { Text("Add to another playlist") },
                        onClick = {
                            menuForRow = null
                            showAddToPlaylistDialog = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    onClick = {
                        onRemoveRow(rowIndex)
                        menuForRow = null
                    }
                )
            }
        }
    }

    // Second step: a separate dialog listing the available target playlists.
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            excludePlaylistId = playlists.getOrNull(viewedPlaylistIndex)?.id,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectExisting = { playlistId ->
                showAddToPlaylistDialog = false
                onAddCurrentToPlaylist(playlistId, "")
            },
            onSelectNew = {
                showAddToPlaylistDialog = false
                showNewPlaylistDialog = true
            }
        )
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onConfirm = { name ->
                showNewPlaylistDialog = false
                if (name.isNotBlank()) {
                    onAddCurrentToPlaylist(0, name)
                }
            }
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistTab>,
    excludePlaylistId: Int?,
    onDismiss: () -> Unit,
    onSelectExisting: (Int) -> Unit,
    onSelectNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to another playlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                for (playlist in playlists) {
                    if (playlist.id == excludePlaylistId) continue
                    TextButton(
                        onClick = { onSelectExisting(playlist.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = playlist.name,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
                TextButton(
                    onClick = onSelectNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "New playlist...",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    row: QueueRowData,
    columnCount: Int,
    columnWidth: Dp,
    columnCentered: List<Boolean>,
    isCurrent: Boolean,
    isPrevious: Boolean,
    isActionable: Boolean,
    zebraIndex: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        zebraIndex % 2 == 0 -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }
    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isPrevious -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(backgroundColor)
            .horizontalScroll(scrollState)
            .then(
                if (isActionable) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = onDoubleTap,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (col in 0 until columnCount) {
            val value = row.values.getOrElse(col) { "" }
            Text(
                text = value,
                modifier = Modifier
                    .width(columnWidth)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (columnCentered.getOrElse(col) { false }) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
            )
        }
    }
}

@Composable
private fun BoxScope.HorizontalScrollIndicators(scrollState: androidx.compose.foundation.ScrollState) {
    val showLeft by remember { derivedStateOf { scrollState.value > 0 } }
    val showRight by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

    if (showRight) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "More columns",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
    if (showLeft) {
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = "More columns",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PlaybackIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color = MaterialTheme.colorScheme.secondary,
    contentColor: Color = MaterialTheme.colorScheme.onSecondary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                color = containerColor,
                shape = CircleShape
            )
            .size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}