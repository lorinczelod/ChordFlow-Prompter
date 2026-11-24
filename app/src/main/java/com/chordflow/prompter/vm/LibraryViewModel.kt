package com.chordflow.prompter.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chordflow.prompter.data.SettingsStore
import com.chordflow.prompter.data.Song
import com.chordflow.prompter.data.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Setlist manager state: the offline library, grouped into setlists. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SongRepository(app)
    private val settings = SettingsStore(app)

    val songs: StateFlow<List<Song>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val setlists: StateFlow<List<String>> = repo.observeSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(Song.DEFAULT_SETLIST))

    private val _activeSetlist = MutableStateFlow(Song.DEFAULT_SETLIST)
    val activeSetlist: StateFlow<String> = _activeSetlist.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                if (_activeSetlist.value == Song.DEFAULT_SETLIST && s.lastSetlist.isNotBlank()) {
                    _activeSetlist.value = s.lastSetlist
                }
            }
        }
    }

    fun songsInActiveSetlist(): List<Song> =
        songs.value.filter { it.setlist == _activeSetlist.value }.sortedBy { it.position }

    fun selectSetlist(name: String) {
        _activeSetlist.value = name
        viewModelScope.launch { settings.setLastSetlist(name) }
    }

    fun createSetlist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _activeSetlist.value = clean
        viewModelScope.launch { settings.setLastSetlist(clean) }
        _message.value = "Setlist \"$clean\" ready — add songs to it from any song's menu."
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val result = repo.importFromUri(uri, _activeSetlist.value)
            _message.value = result.fold(
                onSuccess = { "Imported into \"${_activeSetlist.value}\"." },
                onFailure = { "Import failed: ${it.message ?: "unreadable file"}" }
            )
        }
    }

    fun createBlankSong(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.addFromText(
                text = "{title: New Song}\n{key: G}\n\n[G]Type your lyrics [D]here\n",
                setlist = _activeSetlist.value,
                fallbackTitle = "New Song",
                source = "Created in app"
            )
            onCreated(id)
        }
    }

    fun delete(song: Song) {
        viewModelScope.launch {
            repo.delete(song)
            _message.value = "Deleted \"${song.title}\"."
        }
    }

    fun moveToSetlist(song: Song, setlist: String) {
        viewModelScope.launch { repo.moveToSetlist(song, setlist) }
    }

    /** Persist a drag-and-drop reorder of the active setlist. */
    fun applyOrder(ordered: List<Song>) {
        viewModelScope.launch { repo.applyOrder(ordered.map { it.id }) }
    }

    fun nudgeTranspose(song: Song, delta: Int) {
        viewModelScope.launch {
            val next = com.chordflow.prompter.core.ChordTransposer.normalizeSemitones(
                song.transposeSemitones + delta
            )
            repo.setTranspose(song.id, next)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
