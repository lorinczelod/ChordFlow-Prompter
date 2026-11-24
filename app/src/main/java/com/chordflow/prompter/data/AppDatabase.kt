package com.chordflow.prompter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chordflow.db"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed the two public-domain hymns synchronously as the database
                            // is created, so the library is never empty on first launch.
                            SeedSongs.seeds().forEach { song ->
                                db.execSQL(
                                    """
                                    INSERT INTO songs
                                      (title, artist, originalKey, transposeSemitones, tempo,
                                       content, setlist, position, source, updatedAt)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """.trimIndent(),
                                    arrayOf(
                                        song.title,
                                        song.artist,
                                        song.originalKey,
                                        song.transposeSemitones,
                                        song.tempo,
                                        song.content,
                                        song.setlist,
                                        song.position,
                                        song.source,
                                        song.updatedAt
                                    )
                                )
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
