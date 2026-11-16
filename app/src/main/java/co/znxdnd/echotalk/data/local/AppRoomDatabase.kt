package co.znxdnd.echotalk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.znxdnd.echotalk.data.local.dao.ChatMessageDao
import co.znxdnd.echotalk.data.local.dao.PendingQueueDao
import co.znxdnd.echotalk.data.local.entity.LocalChatMessageEntity
import co.znxdnd.echotalk.data.local.entity.PendingQueueEntity

/**
 * AppRoomDatabase manages the SQLite database via Room for on-device persistence
 * of pending offline actions and local chat message caching.
 */
@Database(
    entities = [
        PendingQueueEntity::class,
        LocalChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppRoomDatabase : RoomDatabase() {

    abstract fun pendingQueueDao(): PendingQueueDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppRoomDatabase? = null

        fun getInstance(context: Context): AppRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppRoomDatabase::class.java,
                    "echotalk_room.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): AppRoomDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppRoomDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
