package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.EmailMessage
import com.example.model.MailAccount

@Database(
    entities = [MailAccount::class, EmailMessage::class],
    version = 1,
    exportSchema = false
)
abstract class MailDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao

    companion object {
        @Volatile
        private var instance: MailDatabase? = null

        fun getInstance(context: Context): MailDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MailDatabase::class.java,
                    "unified_mail_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
