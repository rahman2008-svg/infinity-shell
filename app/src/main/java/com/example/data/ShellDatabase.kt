package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// ==========================================
// Entities
// ==========================================

@Entity(tableName = "virtual_files")
data class VirtualFile(
    @PrimaryKey val path: String, // Absolute virtual path, e.g., "/home/file.txt"
    val isDirectory: Boolean,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_history")
data class CommandHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class ShellSession(
    @PrimaryKey val id: String, // Unique Session ID, e.g., "session_1"
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "aliases")
data class ShellAlias(
    @PrimaryKey val shortcut: String,
    val expansion: String
)

// ==========================================
// DAO (Data Access Object)
// ==========================================

@Dao
interface ShellDao {
    // Virtual Files
    @Query("SELECT * FROM virtual_files")
    suspend fun getAllVirtualFiles(): List<VirtualFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVirtualFile(file: VirtualFile)

    @Query("DELETE FROM virtual_files WHERE path = :path")
    suspend fun deleteVirtualFileByPath(path: String)

    @Query("SELECT * FROM virtual_files WHERE path = :path")
    suspend fun getVirtualFile(path: String): VirtualFile?

    // Command History
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 200")
    fun getCommandHistoryFlow(): Flow<List<CommandHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandHistory(history: CommandHistory)

    @Query("DELETE FROM command_history")
    suspend fun clearCommandHistory()

    // Sessions
    @Query("SELECT * FROM sessions ORDER BY createdAt ASC")
    fun getSessionsFlow(): Flow<List<ShellSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ShellSession)

    @Delete
    suspend fun deleteSession(session: ShellSession)

    // Aliases
    @Query("SELECT * FROM aliases")
    fun getAliasesFlow(): Flow<List<ShellAlias>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: ShellAlias)

    @Delete
    suspend fun deleteAlias(alias: ShellAlias)
}

// ==========================================
// Database
// ==========================================

@Database(
    entities = [VirtualFile::class, CommandHistory::class, ShellSession::class, ShellAlias::class],
    version = 1,
    exportSchema = false
)
abstract class ShellDatabase : RoomDatabase() {
    abstract fun shellDao(): ShellDao

    companion object {
        @Volatile
        private var INSTANCE: ShellDatabase? = null

        fun getDatabase(context: Context): ShellDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShellDatabase::class.java,
                    "infinity_shell_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// Repository
// ==========================================

class ShellRepository(private val shellDao: ShellDao) {

    // Virtual Files
    suspend fun getAllVirtualFiles(): List<VirtualFile> = withContext(Dispatchers.IO) {
        shellDao.getAllVirtualFiles()
    }

    suspend fun insertVirtualFile(file: VirtualFile) = withContext(Dispatchers.IO) {
        shellDao.insertVirtualFile(file)
    }

    suspend fun deleteVirtualFileByPath(path: String) = withContext(Dispatchers.IO) {
        shellDao.deleteVirtualFileByPath(path)
    }

    suspend fun getVirtualFile(path: String): VirtualFile? = withContext(Dispatchers.IO) {
        shellDao.getVirtualFile(path)
    }

    // Command History
    val commandHistory: Flow<List<CommandHistory>> = shellDao.getCommandHistoryFlow()

    suspend fun insertCommandHistory(command: String) = withContext(Dispatchers.IO) {
        // Only insert if not empty and different from the last run command
        if (command.isNotBlank()) {
            shellDao.insertCommandHistory(CommandHistory(command = command.trim()))
        }
    }

    suspend fun clearCommandHistory() = withContext(Dispatchers.IO) {
        shellDao.clearCommandHistory()
    }

    // Sessions
    val sessions: Flow<List<ShellSession>> = shellDao.getSessionsFlow()

    suspend fun insertSession(session: ShellSession) = withContext(Dispatchers.IO) {
        shellDao.insertSession(session)
    }

    suspend fun deleteSession(session: ShellSession) = withContext(Dispatchers.IO) {
        shellDao.deleteSession(session)
    }

    // Aliases
    val aliases: Flow<List<ShellAlias>> = shellDao.getAliasesFlow()

    suspend fun insertAlias(alias: ShellAlias) = withContext(Dispatchers.IO) {
        shellDao.insertAlias(alias)
    }

    suspend fun deleteAlias(alias: ShellAlias) = withContext(Dispatchers.IO) {
        shellDao.deleteAlias(alias)
    }
}
