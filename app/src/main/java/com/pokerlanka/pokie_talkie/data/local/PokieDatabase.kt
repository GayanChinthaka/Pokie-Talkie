package com.pokerlanka.pokie_talkie.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.pokerlanka.pokie_talkie.data.local.dao.ContactDao
import com.pokerlanka.pokie_talkie.data.local.dao.MessageDao
import com.pokerlanka.pokie_talkie.data.local.entity.ContactEntity
import com.pokerlanka.pokie_talkie.data.local.entity.MessageEntity
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class PokieTypeConverters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = try {
        MessageType.valueOf(value)
    } catch (e: Exception) {
        MessageType.TEXT
    }

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = try {
        MessageStatus.valueOf(value)
    } catch (e: Exception) {
        MessageStatus.SENDING
    }

    @TypeConverter
    fun fromCallType(value: CallType): String = value.name

    @TypeConverter
    fun toCallType(value: String): CallType = try {
        CallType.valueOf(value)
    } catch (e: Exception) {
        CallType.VOICE
    }
}

@Database(
    entities = [
        MessageEntity::class,
        ContactEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(PokieTypeConverters::class)
abstract class PokieDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: PokieDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN bluetoothAddress TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS call_logs")
            }
        }

        fun getInstance(context: Context): PokieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PokieDatabase::class.java,
                    "pokie_talkie.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
