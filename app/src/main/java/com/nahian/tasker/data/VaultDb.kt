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

// The vault has its own database file. Fields ending in "Enc" hold AES-GCM ciphertext.

@Entity(tableName = "secret")
data class Secret(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val usernameEnc: String = "",
    val passwordEnc: String = "",
    val url: String = "",
    val notesEnc: String = "",
    val category: String = "",
    val updatedAt: Long = 0,
)

@Entity(tableName = "doc")
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mime: String,
    val fileName: String,      // encrypted file inside the app's private "vault" folder
    val category: String = "",
    val size: Long = 0,
    val addedAt: Long = 0,
)

@Dao
interface VaultDao {
    @Insert suspend fun insertSecret(s: Secret): Long
    @Update suspend fun updateSecret(s: Secret)
    @Delete suspend fun deleteSecret(s: Secret)
    @Query("SELECT * FROM secret WHERE id = :id") suspend fun getSecret(id: Long): Secret?
    @Query("SELECT * FROM secret ORDER BY title COLLATE NOCASE") fun flowSecrets(): Flow<List<Secret>>

    @Insert suspend fun insertDoc(d: Doc): Long
    @Update suspend fun updateDoc(d: Doc)
    @Delete suspend fun deleteDoc(d: Doc)
    @Query("SELECT * FROM doc ORDER BY addedAt DESC") fun flowDocs(): Flow<List<Doc>>
}

@Database(entities = [Secret::class, Doc::class], version = 1, exportSchema = false)
abstract class VaultDb : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
