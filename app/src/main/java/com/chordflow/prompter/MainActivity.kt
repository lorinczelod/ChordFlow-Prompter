package com.chordflow.prompter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chordflow.prompter.ui.SongDownloaderScreen
import com.chordflow.prompter.ui.SongEditorScreen
import com.chordflow.prompter.ui.SongListScreen
import com.chordflow.prompter.ui.TeleprompterScreen
import com.chordflow.prompter.ui.theme.ChordFlowTheme
import com.chordflow.prompter.ui.theme.Phosphor
import com.chordflow.prompter.vm.DownloaderViewModel
import com.chordflow.prompter.vm.EditorViewModel
import com.chordflow.prompter.vm.LibraryViewModel
import com.chordflow.prompter.vm.TeleprompterViewModel

/** Simple, dependency-free screen state. No navigation library needed for four screens. */
private sealed interface Screen {
    data object Library : Screen
    data object Downloader : Screen
    data class Editor(val songId: Long) : Screen
    data class Teleprompter(val songId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChordFlowTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Phosphor.Black),
                    color = Phosphor.Black
                ) {
                    ChordFlowRoot()
                }
            }
        }
    }
}

@Composable
private fun ChordFlowRoot() {
    val libraryVm: LibraryViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    val activeSetlist by libraryVm.activeSetlist.collectAsState()

    BackHandler(enabled = screen !is Screen.Library) {
        screen = Screen.Library
    }

    when (val current = screen) {
        is Screen.Library -> SongListScreen(
            vm = libraryVm,
            onOpenTeleprompter = { id -> screen = Screen.Teleprompter(id) },
            onEditSong = { id -> screen = Screen.Editor(id) },
            onOpenDownloader = { screen = Screen.Downloader }
        )

        is Screen.Downloader -> {
            val vm: DownloaderViewModel = viewModel()
            SongDownloaderScreen(
                vm = vm,
                targetSetlist = activeSetlist,
                onBack = { screen = Screen.Library }
            )
        }

        is Screen.Editor -> {
            val vm: EditorViewModel = viewModel()
            SongEditorScreen(
                vm = vm,
                songId = current.songId,
                onBack = { screen = Screen.Library },
                onOpenTeleprompter = { id -> screen = Screen.Teleprompter(id) }
            )
        }

        is Screen.Teleprompter -> {
            val vm: TeleprompterViewModel = viewModel()
            TeleprompterScreen(
                vm = vm,
                songId = current.songId,
                onBack = { screen = Screen.Library }
            )
        }
    }
}
