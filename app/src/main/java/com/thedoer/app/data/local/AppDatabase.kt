package com.thedoer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room de l'app. Singleton : une seule instance
 * vivante à la fois, partagée par tous les composants qui ont
 * besoin d'accéder aux données locales (surnoms pour l'instant,
 * mémoire/routines plus tard en Phase 2).
 */
@Database(
    entities = [ContactNickname::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactNicknameDao(): ContactNicknameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "thedoer_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
