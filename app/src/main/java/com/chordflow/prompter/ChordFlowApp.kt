package com.chordflow.prompter

import android.app.Application
import com.chordflow.prompter.data.AppDatabase

class ChordFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Touch the database early so the two public-domain hymns are seeded
        // before the user reaches the song list.
        AppDatabase.get(this)
    }
}
