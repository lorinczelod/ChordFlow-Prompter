package com.chordflow.prompter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.data.Song
import com.chordflow.prompter.ui.theme.Phosphor
import com.chordflow.prompter.vm.LibraryViewModel

@Composable
fun SongListScreen(
    vm: LibraryViewModel,
    onOpenTeleprompter: (Long) -> Unit,
    onEditSong: (Long) -> Unit,
    onOpenDownloader: () -> Unit
) {
    val songs by vm.songs.collectAsState()
    val setlists by vm.setlists.collectAsState()
    val activeSetlist by vm.activeSetlist.collectAsState()
    val message by vm.message.collectAsState()

    var showNewSetlist by remember { mutableStateOf(false) }
    var newSetlistName by remember { mutableStateOf("") }
    var menuForSong by remember { mutableStateOf<Song?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFile(uri) }

    val inSetlist = remember(songs, activeSetlist) {
        songs.filter { it.setlist == activeSetlist }.sortedBy { it.position }
    }
    val allSetlists = remember(setlists, activeSetlist) {
        (setlists + activeSetlist + Song.DEFAULT_SETLIST).distinct().sorted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Phosphor.Black)
    ) {
        GreenHeader(
            title = "ChordFlow Prompter",
            subtitle = "${songs.size} songs offline  |  ${allSetlists.size} setlists"
        )

        // --- setlist chips -------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allSetlists.forEach { name ->
                val selected = name == activeSetlist
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) Phosphor.GreenBright else Phosphor.GreenFaint,
                            RoundedCornerShape(3.dp)
                        )
                        .background(if (selected) Phosphor.GreenGhost else Phosphor.Black)
                        .pointerInput(name) {
                            detectTapGesturesCompat { vm.selectSetlist(name) }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = name.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Phosphor.GreenBright else Phosphor.GreenDim
                    )
                }
            }
            GreenMiniButton(text = "+ SETLIST", onClick = { showNewSetlist = true })
        }

        // --- actions -------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GreenButton(
                text = "Download Free Songs",
                onClick = onOpenDownloader,
                modifier = Modifier.weight(1f)
            )
            GreenButton(
                text = "Import File",
                onClick = {
                    // ChordPro and OnSong files are frequently served as text/plain or
                    // application/octet-stream, so accept broadly and filter on parse.
                    importLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            GreenButton(
                text = "+ New Blank Song",
                onClick = { vm.createBlankSong { id -> onEditSong(id) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (message != null) {
            Text(
                text = "> $message",
                style = MaterialTheme.typography.bodySmall,
                color = Phosphor.GreenBright,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .pointerInput(Unit) { detectTapGesturesCompat { vm.clearMessage() } }
            )
        }

        GreenDivider(Modifier.padding(top = 6.dp))

        if (inSetlist.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO SONGS IN \"${activeSetlist.uppercase()}\"\n\nDownload, import, or create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Phosphor.GreenDim
                )
            }
        } else {
            ReorderableSongList(
                songs = inSetlist,
                onOpen = onOpenTeleprompter,
                onMenu = { menuForSong = it },
                onOrderChanged = { vm.applyOrder(it) },
                onTranspose = { song, delta -> vm.nudgeTranspose(song, delta) }
            )
        }
    }

    // --- new setlist dialog -----------------------------------------------
    if (showNewSetlist) {
        AlertDialog(
            onDismissRequest = { showNewSetlist = false },
            containerColor = Phosphor.Black,
            titleContentColor = Phosphor.Green,
            textContentColor = Phosphor.Green,
            title = { Text("NEW SETLIST", style = MaterialTheme.typography.titleMedium) },
            text = {
                GreenTextField(
                    value = newSetlistName,
                    onValueChange = { newSetlistName = it },
                    label = "Setlist name",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                GreenButton(text = "Create", filled = true, onClick = {
                    vm.createSetlist(newSetlistName)
                    newSetlistName = ""
                    showNewSetlist = false
                })
            },
            dismissButton = {
                GreenButton(text = "Cancel", onClick = { showNewSetlist = false })
            }
        )
    }

    // --- per-song menu -----------------------------------------------------
    val menuSong = menuForSong
    if (menuSong != null) {
        AlertDialog(
            onDismissRequest = { menuForSong = null },
            containerColor = Phosphor.Black,
            titleContentColor = Phosphor.Green,
            textContentColor = Phosphor.Green,
            title = {
                Text(
                    menuSong.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusLine(
                        "Original key ${menuSong.originalKey} | " +
                            "sounding ${ChordTransposer.transposedKey(menuSong.originalKey, menuSong.transposeSemitones)}"
                    )
                    if (menuSong.source.isNotBlank()) StatusLine(menuSong.source)
                    Spacer(Modifier.height(4.dp))
                    GreenButton(
                        text = "Open Teleprompter",
                        filled = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            menuForSong = null
                            onOpenTeleprompter(menuSong.id)
                        }
                    )
                    GreenButton(
                        text = "Edit / Change Key",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            menuForSong = null
                            onEditSong(menuSong.id)
                        }
                    )
                    Text(
                        "MOVE TO SETLIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = Phosphor.GreenDim
                    )
                    allSetlists.filter { it != menuSong.setlist }.forEach { target ->
                        GreenButton(
                            text = target,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                vm.moveToSetlist(menuSong, target)
                                menuForSong = null
                            }
                        )
                    }
                    GreenButton(
                        text = "Delete Song",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            vm.delete(menuSong)
                            menuForSong = null
                        }
                    )
                }
            },
            confirmButton = {
                GreenButton(text = "Close", onClick = { menuForSong = null })
            }
        )
    }
}

/**
 * Setlist with long-press drag-and-drop reordering.
 * Positions are written back to Room when the drag ends.
 */
@Composable
private fun ReorderableSongList(
    songs: List<Song>,
    onOpen: (Long) -> Unit,
    onMenu: (Song) -> Unit,
    onOrderChanged: (List<Song>) -> Unit,
    onTranspose: (Song, Int) -> Unit
) {
    val listState: LazyListState = rememberLazyListState()
    var working by remember(songs) { mutableStateOf(songs) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(songs) { working = songs }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(working, key = { it.id }) { song ->
            val isDragging = draggingId == song.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        alpha = if (isDragging) 0.85f else 1f
                    }
                    .background(if (isDragging) Phosphor.GreenGhost else Phosphor.Black)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle — long-press here to reorder.
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(36.dp)
                            .pointerInput(song.id, working) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = song.id
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val info = listState.layoutInfo.visibleItemsInfo
                                        val fromIndex = working.indexOfFirst { it.id == song.id }
                                        val dragged = info.firstOrNull { it.index == fromIndex }
                                        if (dragged != null) {
                                            val center = dragged.offset + dragged.size / 2f + dragOffset
                                            val target = info.firstOrNull { item ->
                                                center >= item.offset && center <= item.offset + item.size
                                            }
                                            if (target != null && target.index != fromIndex &&
                                                target.index in working.indices
                                            ) {
                                                val updated = working.toMutableList()
                                                val moved = updated.removeAt(fromIndex)
                                                updated.add(target.index, moved)
                                                working = updated
                                                dragOffset -= (target.offset - dragged.offset)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        onOrderChanged(working)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                        working = songs
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "≡",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isDragging) Phosphor.GreenBright else Phosphor.GreenDim
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(song.id) {
                                detectTapGesturesCompat(
                                    onTap = { onOpen(song.id) },
                                    onLongPress = { onMenu(song) }
                                )
                            }
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Phosphor.Green,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sounding = ChordTransposer.transposedKey(song.originalKey, song.transposeSemitones)
                        val tail = buildString {
                            append("KEY ").append(sounding)
                            if (song.transposeSemitones != 0) {
                                append(" (")
                                append(song.originalKey)
                                append(if (song.transposeSemitones > 0) " +" else " ")
                                append(song.transposeSemitones)
                                append(")")
                            }
                            if (song.tempo > 0) append("  |  ").append(song.tempo).append(" BPM")
                            if (song.artist.isNotBlank()) append("  |  ").append(song.artist)
                        }
                        Text(
                            text = tail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Phosphor.GreenDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    KeyBadge(
                        text = ChordTransposer.transposedKey(song.originalKey, song.transposeSemitones),
                        bright = song.transposeSemitones != 0
                    )
                    Spacer(Modifier.width(6.dp))
                    GreenMiniButton(text = "-", onClick = { onTranspose(song, -1) })
                    Spacer(Modifier.width(4.dp))
                    GreenMiniButton(text = "+", onClick = { onTranspose(song, 1) })
                    Spacer(Modifier.width(6.dp))
                    GreenMiniButton(text = "⋮", onClick = { onMenu(song) })
                }
                GreenDivider()
            }
        }
    }
}
