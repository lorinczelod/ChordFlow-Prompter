package com.chordflow.prompter.data

import android.content.Context
import android.net.Uri
import com.chordflow.prompter.core.ChordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/** Single entry point for everything the app stores locally. 100% offline. */
class SongRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).songDao()

    fun observeAll(): Flow<List<Song>> = dao.observeAll()
    fun observeSetlists(): Flow<List<String>> = dao.observeSetlists()
    fun observeSong(id: Long): Flow<Song?> = dao.observeById(id)

    suspend fun getById(id: Long): Song? = dao.getById(id)

    /** Create a song from raw ChordPro / chord-over-lyric text, filling metadata from the parse. */
    suspend fun addFromText(
        text: String,
        setlist: String = Song.DEFAULT_SETLIST,
        fallbackTitle: String = "",
        source: String = ""
    ): Long {
        val parsed = ChordParser.parse(text)
        val title = parsed.title.ifBlank { fallbackTitle.ifBlank { "Untitled Song" } }
        val position = dao.nextPosition(setlist)
        return dao.insert(
            Song(
                title = title,
                artist = parsed.artist,
                originalKey = parsed.key,
                tempo = parsed.tempo,
                content = text,
                setlist = setlist,
                position = position,
                source = source
            )
        )
    }

    suspend fun update(song: Song) = dao.update(song.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(song: Song) = dao.delete(song)

    suspend fun setTranspose(id: Long, semitones: Int) =
        dao.setTranspose(id, semitones, System.currentTimeMillis())

    /** Rewrites positions after a drag-and-drop reorder. */
    suspend fun applyOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> dao.setPosition(id, index) }
    }

    suspend fun moveToSetlist(song: Song, setlist: String) {
        val position = dao.nextPosition(setlist)
        dao.moveToSetlist(song.id, setlist, position)
    }

    /**
     * Import a local .cho / .onsong / .pro / .txt file picked with the storage picker.
     * Reads through the content resolver, so Drive and Downloads both work.
     */
    suspend fun importFromUri(uri: Uri, setlist: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return@withContext Result.failure(IllegalStateException("Could not open that file"))

            if (text.isBlank()) {
                return@withContext Result.failure(IllegalStateException("That file is empty"))
            }
            val name = displayName(uri)
            val id = addFromText(
                text = text,
                setlist = setlist,
                fallbackTitle = name.substringBeforeLast('.'),
                source = "Imported from device: $name"
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Imported song"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.let { name = it }
                }
            }
        } catch (_: Exception) {
            // Keep the path-derived name.
        }
        return name.substringAfterLast('/')
    }
}
