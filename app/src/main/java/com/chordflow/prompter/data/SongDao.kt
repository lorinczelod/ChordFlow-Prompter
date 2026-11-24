package com.chordflow.prompter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY setlist ASC, position ASC, title ASC")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE setlist = :setlist ORDER BY position ASC, title ASC")
    fun observeSetlist(setlist: String): Flow<List<Song>>

    @Query("SELECT DISTINCT setlist FROM songs ORDER BY setlist ASC")
    fun observeSetlists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: Long): Flow<Song?>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM songs WHERE setlist = :setlist")
    suspend fun nextPosition(setlist: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("UPDATE songs SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("UPDATE songs SET setlist = :setlist, position = :position WHERE id = :id")
    suspend fun moveToSetlist(id: Long, setlist: String, position: Int)

    @Query("UPDATE songs SET transposeSemitones = :semitones, updatedAt = :now WHERE id = :id")
    suspend fun setTranspose(id: Long, semitones: Int, now: Long)
}
