package com.example.rifas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Raffle::class, SoldNumber::class], version = 6, exportSchema = false)
abstract class RaffleDatabase : RoomDatabase() {
    abstract fun raffleDao(): RaffleDao

    companion object {
        @Volatile
        private var INSTANCE: RaffleDatabase? = null

        fun getInstance(context: Context): RaffleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RaffleDatabase::class.java,
                    "rifas.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
