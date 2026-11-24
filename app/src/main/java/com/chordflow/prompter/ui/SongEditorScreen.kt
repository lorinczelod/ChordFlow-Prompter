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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.ui.theme.Phosphor
import com.chordflow.prompter.vm.EditorViewModel

@Composable
fun SongEditorScreen(
    vm: EditorViewModel,
    songId: Long,
    onBack: () -> Unit,
    onOpenTeleprompter: (Long) -> Unit
) {
    LaunchedEffect(songId) { vm.load(songId) }
    val state by vm.state.collectAsState()
    var previewMode by remember { mutableStateOf(PreviewMode.CHORDS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Phosphor.Black)
    ) {
        GreenHeader(
            title = "Song Editor",
            subtitle = if (state.saved) "Saved" else "Unsaved changes",
            trailing = { GreenMiniButton(text = "BACK", onClick = { vm.save(); onBack() }) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            GreenTextField(
                value = state.title,
                onValueChange = vm::onTitleChange,
                label = "Title",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GreenTextField(
                    value = state.artist,
                    onValueChange = vm::onArtistChange,
                    label = "Artist",
                    modifier = Modifier.weight(2f)
                )
                GreenTextField(
                    value = state.tempo,
                    onValueChange = vm::onTempoChange,
                    label = "BPM",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            KeyChangerPanel(
                originalKey = state.originalKey,
                transpose = state.transpose,
                soundingKey = state.soundingKey,
                transposeLabel = state.transposeLabel,
                onOriginalKey = vm::setOriginalKey,
                onTargetKey = vm::setTargetKey,
                onNudge = vm::nudgeTranspose,
                onReset = vm::resetTranspose
            )

            Spacer(Modifier.height(12.dp))
            Text("CHART SOURCE", style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
            StatusLine("ChordPro directives, [G]inline chords, or chord-over-lyric all work.")
            Spacer(Modifier.height(6.dp))
            GreenTextField(
                value = state.content,
                onValueChange = vm::onContentChange,
                label = "Lyrics and chords",
                singleLine = false,
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
                Spacer(Modifier.weight(1f))
                PreviewMode.entries.forEach { mode ->
                    val selected = mode == previewMode
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (selected) Phosphor.GreenBright else Phosphor.GreenFaint,
                                RoundedCornerShape(3.dp)
                            )
                            .background(if (selected) Phosphor.GreenGhost else Phosphor.Black)
                            .pointerInput(mode) { detectTapGesturesCompat { previewMode = mode } }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Phosphor.GreenBright else Phosphor.GreenDim
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Phosphor.GreenFaint, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                val previewText = remember(state.content, state.transpose, previewMode, state.originalKey) {
                    when (previewMode) {
                        PreviewMode.CHORDS -> vm.previewWithChords()
                        PreviewMode.STAGE -> vm.previewLyricsOnly().joinToString("\n")
                    }
                }
                Text(
                    text = previewText.ifBlank { "(nothing to preview)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (previewMode == PreviewMode.STAGE) Phosphor.GreenBright else Phosphor.Green
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        GreenDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GreenButton(
                text = "Save",
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = { vm.save() }
            )
            GreenButton(
                text = "Save & Open Prompter",
                modifier = Modifier.weight(1f),
                onClick = { vm.save { onOpenTeleprompter(songId) } }
            )
        }
    }
}

private enum class PreviewMode(val label: String) {
    CHORDS("WITH CHORDS"),
    STAGE("STAGE VIEW")
}

@Composable
private fun KeyChangerPanel(
    originalKey: String,
    transpose: Int,
    soundingKey: String,
    transposeLabel: String,
    onOriginalKey: (String) -> Unit,
    onTargetKey: (String) -> Unit,
    onNudge: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Phosphor.GreenFaint, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Text("KEY CHANGER", style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyBadge(text = "ORIGINAL: $originalKey")
            KeyBadge(text = "SOUNDING: $soundingKey", bright = transpose != 0)
            Spacer(Modifier.weight(1f))
            GreenMiniButton(text = "-", onClick = { onNudge(-1) })
            Text(
                text = transposeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Phosphor.GreenBright,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            GreenMiniButton(text = "+", onClick = { onNudge(1) })
        }

        Spacer(Modifier.height(10.dp))
        Text("ORIGINAL KEY", style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
        KeyRow(selected = ChordTransposer.stripKeyQuality(originalKey), onPick = onOriginalKey)

        Spacer(Modifier.height(8.dp))
        Text("TRANSPOSE TO", style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
        KeyRow(selected = ChordTransposer.stripKeyQuality(soundingKey), onPick = onTargetKey, bright = true)

        Spacer(Modifier.height(8.dp))
        GreenMiniButton(text = "RESET TO ORIGINAL", onClick = onReset)
    }
}

@Composable
private fun KeyRow(selected: String, onPick: (String) -> Unit, bright: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChordTransposer.TARGET_KEYS.forEach { key ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .border(
                        1.dp,
                        if (isSelected) (if (bright) Phosphor.GreenBright else Phosphor.Green) else Phosphor.GreenFaint,
                        RoundedCornerShape(3.dp)
                    )
                    .background(if (isSelected) Phosphor.GreenGhost else Phosphor.Black)
                    .pointerInput(key) { detectTapGesturesCompat { onPick(key) } }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Phosphor.GreenBright else Phosphor.GreenDim
                )
            }
        }
    }
}
