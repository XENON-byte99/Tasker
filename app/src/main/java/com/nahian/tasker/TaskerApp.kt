package com.nahian.tasker

import android.app.Application
import androidx.room.Room
import com.nahian.tasker.core.Notifier
import com.nahian.tasker.core.Planner
import com.nahian.tasker.data.AppDao
import com.nahian.tasker.data.Db
import com.nahian.tasker.data.NoteDao
import com.nahian.tasker.data.NotesDb
import com.nahian.tasker.data.Prefs
import com.nahian.tasker.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskerApp : Application() {
    lateinit var db: Db
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repo
        private set
    val dao: AppDao get() = db.dao()
    lateinit var notesDb: NotesDb
        private set
    val notes: NoteDao get() = notesDb.noteDao()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** For saves started from screens and dialogs: keeps running after the dialog or screen closes. */
    val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, Db::class.java, "tasker.db").build()
        notesDb = Room.databaseBuilder(this, NotesDb::class.java, "tasker_notes.db").build()
        prefs = Prefs(this)
        com.nahian.tasker.ui.Skins.current = com.nahian.tasker.ui.Skins.byId(prefs.skinId)
        repo = Repo(this, db.dao(), prefs)
        Notifier.createChannels(this)
        scope.launch {
            repo.seed()
            Planner.sync(this@TaskerApp)
        }
    }
}
