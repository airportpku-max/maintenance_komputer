package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Device::class, TroubleTicket::class, MaintenanceLog::class, UploadedFile::class],
    version = 13,
    exportSchema = false
)
abstract class MaintenanceDatabase : RoomDatabase() {
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        @Volatile
        private var INSTANCE: MaintenanceDatabase? = null

        fun getDatabase(context: Context): MaintenanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MaintenanceDatabase::class.java,
                    "maintenance_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed data on database creation
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = database.maintenanceDao()
                                seedData(dao)
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedData(dao: MaintenanceDao) {
            // Seed data cleared as requested by user ("hapus saja data bawaan seperti ini")
        }
    }
}
