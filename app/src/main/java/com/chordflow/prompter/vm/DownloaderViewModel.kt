package com.chordflow.prompter.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chordflow.prompter.data.SongRepository
import com.chordflow.prompter.net.OnlineSongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the optional "Download Free Songs" screen. The only online code path. */
class DownloaderViewModel(app: Application) : AndroidViewModel(app) {

    private val online = OnlineSongRepository(app)
    private val local = SongRepository(app)

    data class UiState(
        val catalog: OnlineSongRepository.Catalog = OnlineSongRepository.CATALOGS.first(),
        val loading: Boolean = false,
        val songs: List<OnlineSongRepository.RemoteSong> = emptyList(),
        val query: String = "",
        val status: String = "",
        val offline: Boolean = false,
        val previewTitle: String = "",
        val previewText: String = "",
        val previewUrl: String = "",
        val previewLoading: Boolean = false,
        val pastedUrl: String = ""
    ) {
        val filtered: List<OnlineSongRepository.RemoteSong>
            get() = if (query.isBlank()) songs
            else songs.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val catalogs: List<OnlineSongRepository.Catalog> = OnlineSongRepository.CATALOGS

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onPastedUrlChange(value: String) {
        _state.value = _state.value.copy(pastedUrl = value)
    }

    fun selectCatalog(catalog: OnlineSongRepository.Catalog) {
        _state.value = _state.value.copy(catalog = catalog, songs = emptyList(), status = "")
        loadCatalog()
    }

    fun loadCatalog() {
        val catalog = _state.value.catalog
        _state.value = _state.value.copy(loading = true, status = "Loading ${catalog.label}…", offline = false)
        viewModelScope.launch {
            when (val result = online.loadCatalog(catalog)) {
                is OnlineSongRepository.LoadResult.Success -> _state.value = _state.value.copy(
                    loading = false,
                    songs = result.songs,
                    status = "${result.songs.size} chord sheets available",
                    offline = false
                )

                is OnlineSongRepository.LoadResult.Offline -> _state.value = _state.value.copy(
                    loading = false,
                    songs = emptyList(),
                    status = result.message,
                    offline = true
                )

                is OnlineSongRepository.LoadResult.Error -> _state.value = _state.value.copy(
                    loading = false,
                    songs = emptyList(),
                    status = result.message,
                    offline = false
                )
            }
        }
    }

    fun preview(song: OnlineSongRepository.RemoteSong) {
        _state.value = _state.value.copy(
            previewLoading = true,
            previewTitle = song.title,
            previewUrl = song.rawUrl,
            previewText = ""
        )
        viewModelScope.launch {
            val result = online.fetchSongText(song.rawUrl)
            _state.value = result.fold(
                onSuccess = { _state.value.copy(previewLoading = false, previewText = it) },
                onFailure = {
                    _state.value.copy(
                        previewLoading = false,
                        previewText = "",
                        status = "Preview failed: ${it.message ?: "unknown error"}"
                    )
                }
            )
        }
    }

    fun previewPastedUrl() {
        val url = online.normalizeUrl(_state.value.pastedUrl)
        if (url.isBlank()) {
            _state.value = _state.value.copy(status = "Paste a raw ChordPro URL first")
            return
        }
        _state.value = _state.value.copy(
            previewLoading = true,
            previewTitle = url.substringAfterLast('/'),
            previewUrl = url,
            previewText = ""
        )
        viewModelScope.launch {
            val result = online.fetchSongText(url)
            _state.value = result.fold(
                onSuccess = { _state.value.copy(previewLoading = false, previewText = it) },
                onFailure = {
                    _state.value.copy(
                        previewLoading = false,
                        status = "Download failed: ${it.message ?: "unknown error"}"
                    )
                }
            )
        }
    }

    fun dismissPreview() {
        _state.value = _state.value.copy(previewTitle = "", previewText = "", previewUrl = "", previewLoading = false)
    }

    /** Parse and store the previewed song permanently in Room. */
    fun saveToOfflineLibrary(setlist: String) {
        val s = _state.value
        if (s.previewText.isBlank()) return
        viewModelScope.launch {
            local.addFromText(
                text = s.previewText,
                setlist = setlist,
                fallbackTitle = s.previewTitle,
                source = s.previewUrl
            )
            _state.value = _state.value.copy(
                previewTitle = "",
                previewText = "",
                previewUrl = "",
                status = "Saved to your offline library."
            )
        }
    }
}
