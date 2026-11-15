package com.nahian.tasker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Notes live in their own database file so adding them never touches the existing data.

@Entity(tableName = "note")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String = "",
    val category: String = "",
    val pinned: Boolean = false,
    val updatedAt: Long = 0,
)

@Dao
interface NoteDao {
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Delete suspend fun delete(n: Note)
    @Query("SELECT * FROM note WHERE id = :id") suspend fun get(id: Long): Note?
    @Query("SELECT * FROM note ORDER BY pinned DESC, updatedAt DESC") fun flowNotes(): Flow<List<Note>>
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class NotesDb : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
