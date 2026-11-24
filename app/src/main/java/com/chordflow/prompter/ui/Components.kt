package com.chordflow.prompter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.ui.theme.Phosphor

/** Shared monochrome-green building blocks used by every screen. */

/**
 * Thin wrapper over [androidx.compose.foundation.gestures.detectTapGestures] so screens can
 * attach a tap and an optional long-press without importing the gesture package everywhere.
 */
suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesCompat(
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
) {
    androidx.compose.foundation.gestures.detectTapGestures(
        onLongPress = onLongPress,
        onTap = onTap
    )
}

@Composable
fun GreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Phosphor.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Phosphor.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Phosphor.GreenDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) trailing()
        }
        GreenDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
fun GreenDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Phosphor.GreenFaint)
    )
}

@Composable
fun GreenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Phosphor.Green,
                contentColor = Phosphor.Black,
                disabledContainerColor = Phosphor.GreenGhost,
                disabledContentColor = Phosphor.GreenDim
            )
        ) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, if (enabled) Phosphor.Green else Phosphor.GreenFaint),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Phosphor.Green,
                disabledContentColor = Phosphor.GreenFaint
            )
        ) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Compact square-ish button for +/- controls on the HUD. */
@Composable
fun GreenMiniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(3.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        border = BorderStroke(1.dp, if (enabled) Phosphor.Green else Phosphor.GreenFaint),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Phosphor.Green,
            disabledContentColor = Phosphor.GreenFaint
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GreenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Phosphor.Green,
            unfocusedTextColor = Phosphor.Green,
            disabledTextColor = Phosphor.GreenDim,
            cursorColor = Phosphor.GreenBright,
            focusedBorderColor = Phosphor.Green,
            unfocusedBorderColor = Phosphor.GreenDim,
            focusedLabelColor = Phosphor.Green,
            unfocusedLabelColor = Phosphor.GreenDim,
            focusedContainerColor = Phosphor.Black,
            unfocusedContainerColor = Phosphor.Black
        )
    )
}

/** Small bracketed key badge, e.g. [ KEY: G ] */
@Composable
fun KeyBadge(text: String, modifier: Modifier = Modifier, bright: Boolean = false) {
    Box(
        modifier = modifier
            .border(1.dp, if (bright) Phosphor.GreenBright else Phosphor.GreenDim, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (bright) Phosphor.GreenBright else Phosphor.Green,
            maxLines = 1
        )
    }
}

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Phosphor.GreenDim,
        modifier = modifier
    )
}
