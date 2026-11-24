package com.chordflow.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chordflow.prompter.core.ChordParser
import com.chordflow.prompter.ui.theme.Phosphor
import com.chordflow.prompter.vm.TeleprompterViewModel

/**
 * Stage view. Lyrics only — every chord and directive has already been stripped by the
 * parser. The chords live on as invisible triggers that the microphone matches against.
 */
@Composable
fun TeleprompterScreen(
    vm: TeleprompterViewModel,
    songId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(songId) { vm.load(songId) }
    val state by vm.state.collectAsState()
    val detection by vm.detectorState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) vm.startListening()
    }

    // Keep the screen awake for the whole service.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            vm.stopListening()
        }
    }

    val listState = rememberLazyListState()

    // Smooth auto-scroll: bring the active line into the top-third reading zone.
    LaunchedEffect(state.activeIndex, state.lines.size) {
        if (state.lines.isEmpty()) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        val offset = if (viewport > 0) -(viewport / 3) else 0
        listState.animateScrollToItem(
            index = state.activeIndex.coerceIn(0, state.lines.lastIndex),
            scrollOffset = offset
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Phosphor.Black)
    ) {
        TeleprompterHud(
            title = state.title,
            soundingKey = state.soundingKey,
            transposeLabel = state.transposeLabel,
            heardChord = if (detection.gated) "" else detection.chord,
            nextChord = state.nextTriggerChord,
            listening = state.listening,
            autoScroll = state.autoScroll,
            level = detection.level,
            confidence = detection.confidence,
            onKeyDown = { vm.nudgeTranspose(-1) },
            onKeyUp = { vm.nudgeTranspose(1) },
            onToggleMic = {
                if (!hasMicPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    vm.toggleListening()
                }
            },
            onToggleAutoScroll = { vm.toggleAutoScroll() },
            onSettings = { showSettings = !showSettings },
            onBack = onBack
        )

        if (state.micError != null) {
            Text(
                text = "! ${state.micError}",
                style = MaterialTheme.typography.bodySmall,
                color = Phosphor.GreenBright,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        if (showSettings) {
            SettingsPanel(
                sensitivity = state.sensitivity,
                confidence = state.confidence,
                fontScale = state.fontScale,
                strictness = state.strictness.name,
                onSensitivity = vm::setSensitivity,
                onConfidence = vm::setConfidence,
                onFontScale = vm::setFontScale,
                onCycleStrictness = vm::cycleStrictness
            )
        }

        // --- lyrics, and nothing but lyrics --------------------------------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            itemsIndexed(state.lines) { index, line ->
                LyricRow(
                    line = line,
                    isActive = index == state.activeIndex,
                    isPast = index < state.activeIndex,
                    fontScale = state.fontScale,
                    onTap = { vm.setActiveIndex(index) }
                )
            }
            item { Spacer(Modifier.height(420.dp)) }
        }

        // --- manual fallback controls ---------------------------------------
        GreenDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GreenButton(text = "▲ PREV", onClick = { vm.stepLine(-1) }, modifier = Modifier.weight(1f))
            GreenButton(text = "RESTART", onClick = { vm.restart() })
            GreenButton(text = "NEXT ▼", onClick = { vm.stepLine(1) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LyricRow(
    line: ChordParser.SongLine,
    isActive: Boolean,
    isPast: Boolean,
    fontScale: Float,
    onTap: () -> Unit
) {
    if (line.isBlank && line.lyric.isBlank()) {
        Spacer(Modifier.height((18 * fontScale).dp))
        return
    }

    val color: Color = when {
        isActive -> Phosphor.GreenBright
        isPast -> Phosphor.GreenDim
        else -> Phosphor.Green
    }

    if (line.isSection) {
        Text(
            text = line.lyric.uppercase(),
            fontSize = (15 * fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = Phosphor.GreenDim,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGesturesCompat { onTap() } }
                .padding(top = 18.dp, bottom = 4.dp)
        )
        return
    }

    Text(
        text = line.lyric,
        fontSize = (30 * fontScale).sp,
        lineHeight = (40 * fontScale).sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGesturesCompat { onTap() } }
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun TeleprompterHud(
    title: String,
    soundingKey: String,
    transposeLabel: String,
    heardChord: String,
    nextChord: String,
    listening: Boolean,
    autoScroll: Boolean,
    level: Float,
    confidence: Float,
    onKeyDown: () -> Unit,
    onKeyUp: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Phosphor.Black)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GreenMiniButton(text = "←", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Phosphor.GreenDim,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            GreenMiniButton(text = if (autoScroll) "AUTO" else "MANUAL", onClick = onToggleAutoScroll)
            Spacer(Modifier.width(6.dp))
            GreenMiniButton(text = "⚙", onClick = onSettings)
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live key changer
            Text(
                text = "KEY: $soundingKey",
                style = MaterialTheme.typography.titleMedium,
                color = Phosphor.GreenBright
            )
            Spacer(Modifier.width(6.dp))
            GreenMiniButton(text = "-1", onClick = onKeyDown)
            Spacer(Modifier.width(4.dp))
            GreenMiniButton(text = "+1", onClick = onKeyUp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "($transposeLabel)",
                style = MaterialTheme.typography.labelSmall,
                color = Phosphor.GreenDim
            )
            Spacer(Modifier.weight(1f))
            GreenMiniButton(text = if (listening) "MIC ON" else "MIC OFF", onClick = onToggleMic)
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MIC: [${heardChord.ifBlank { "--" }}]  ->  NEXT: [${nextChord.ifBlank { "--" }}]",
                style = MaterialTheme.typography.labelMedium,
                color = if (heardChord.isNotBlank()) Phosphor.GreenBright else Phosphor.GreenDim,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            LevelMeter(level = level, confidence = confidence)
        }

        GreenDivider(Modifier.padding(top = 6.dp))
    }
}

/** Twelve-segment input level bar with a confidence tick. */
@Composable
private fun LevelMeter(level: Float, confidence: Float) {
    val segments = 12
    val lit = (level.coerceIn(0f, 1f) * segments).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(segments) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(4.dp)
                    .height(if (i < lit) 12.dp else 4.dp)
                    .background(if (i < lit) Phosphor.Green else Phosphor.GreenFaint)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${(confidence.coerceIn(0f, 1f) * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = Phosphor.GreenDim
        )
    }
}

@Composable
private fun SettingsPanel(
    sensitivity: Float,
    confidence: Float,
    fontScale: Float,
    strictness: String,
    onSensitivity: (Float) -> Unit,
    onConfidence: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onCycleStrictness: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, Phosphor.GreenFaint, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        SliderRow(
            label = "MIC SENSITIVITY / NOISE GATE",
            value = sensitivity,
            onChange = onSensitivity,
            hint = "Higher = ignores more stage bleed"
        )
        SliderRow(
            label = "MATCH CONFIDENCE",
            value = confidence,
            onChange = onConfidence,
            hint = "Higher = fewer false triggers"
        )
        SliderRow(
            label = "LYRIC SIZE",
            value = (fontScale - 0.6f) / 1.4f,
            onChange = { onFontScale(0.6f + it * 1.4f) },
            hint = "Scale ${"%.2f".format(fontScale)}x"
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CHORD MATCHING",
                style = MaterialTheme.typography.labelSmall,
                color = Phosphor.GreenDim,
                modifier = Modifier.weight(1f)
            )
            GreenMiniButton(text = strictness, onClick = onCycleStrictness)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    hint: String
) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenDim)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Phosphor.GreenBright,
                activeTrackColor = Phosphor.Green,
                inactiveTrackColor = Phosphor.GreenFaint
            )
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Phosphor.GreenFaint)
    }
}
