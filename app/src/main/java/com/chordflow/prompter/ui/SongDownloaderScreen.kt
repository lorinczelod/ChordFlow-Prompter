package com.chordflow.prompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.net.OnlineSongRepository
import com.chordflow.prompter.ui.theme.Phosphor
import com.chordflow.prompter.vm.DownloaderViewModel

/**
 * The one screen that uses the internet. Search an open ChordPro catalog, preview the
 * chart in monochrome green, then save it permanently into the offline Room library.
 */
@Composable
fun SongDownloaderScreen(
    vm: DownloaderViewModel,
    targetSetlist: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state.songs.isEmpty() && !state.loading) vm.loadCatalog()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Phosphor.Black)
    ) {
        GreenHeader(
            title = "Download Free Songs",
            subtitle = "Saves into setlist: $targetSetlist",
            trailing = { GreenMiniButton(text = "BACK", onClick = onBack) }
        )

        // --- catalog picker ------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            vm.catalogs.forEach { catalog ->
                val selected = catalog.label == state.catalog.label
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) Phosphor.GreenBright else Phosphor.GreenFaint,
                            RoundedCornerShape(3.dp)
                        )
                        .background(if (selected) Phosphor.GreenGhost else Phosphor.Black)
                        .pointerInput(catalog.label) {
                            detectTapGesturesCompat { vm.selectCatalog(catalog) }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = catalog.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Phosphor.GreenBright else Phosphor.GreenDim
                    )
                }
            }
        }

        // --- search ---------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GreenTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = "Search by title",
                modifier = Modifier.weight(1f)
            )
            GreenMiniButton(text = "RELOAD", onClick = { vm.loadCatalog() })
        }

        // --- paste a raw URL -------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GreenTextField(
                value = state.pastedUrl,
                onValueChange = vm::onPastedUrlChange,
                label = "Or paste a raw ChordPro / GitHub URL",
                modifier = Modifier.weight(1f)
            )
            GreenMiniButton(text = "FETCH", onClick = { vm.previewPastedUrl() })
        }

        if (state.status.isNotBlank()) {
            Text(
                text = "> ${state.status}",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.offline) Phosphor.GreenBright else Phosphor.GreenDim,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        GreenDivider()

        // --- preview pane or result list -------------------------------------
        if (state.previewTitle.isNotBlank()) {
            PreviewPane(
                title = state.previewTitle,
                loading = state.previewLoading,
                text = state.previewText,
                onSave = { vm.saveToOfflineLibrary(targetSetlist) },
                onClose = { vm.dismissPreview() }
            )
        } else if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "LOADING CATALOG…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Phosphor.GreenDim
                )
            }
        } else if (state.filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.offline)
                        "OFFLINE\n\nYour saved songs still work.\nConnect to Wi-Fi to browse."
                    else
                        "NO RESULTS\n\nTry another catalog, or paste a raw URL above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Phosphor.GreenDim
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.filtered, key = { it.rawUrl }) { song ->
                    RemoteSongRow(song = song, onClick = { vm.preview(song) })
                }
            }
        }
    }
}

@Composable
private fun RemoteSongRow(song: OnlineSongRepository.RemoteSong, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(song.rawUrl) { detectTapGesturesCompat { onClick() } }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            color = Phosphor.Green,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.path,
            style = MaterialTheme.typography.bodySmall,
            color = Phosphor.GreenDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    GreenDivider()
}

@Composable
private fun PreviewPane(
    title: String,
    loading: Boolean,
    text: String,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Phosphor.GreenBright,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            GreenMiniButton(text = "CLOSE", onClick = onClose)
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("FETCHING…", style = MaterialTheme.typography.titleMedium, color = Phosphor.GreenDim)
                }

                text.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NOTHING TO PREVIEW", style = MaterialTheme.typography.bodyMedium, color = Phosphor.GreenDim)
                }

                else -> Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Phosphor.Green,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        GreenDivider()
        Spacer(Modifier.height(8.dp))
        GreenButton(
            text = "Save to Offline Library",
            filled = true,
            enabled = text.isNotBlank() && !loading,
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(12.dp))
    }
}
