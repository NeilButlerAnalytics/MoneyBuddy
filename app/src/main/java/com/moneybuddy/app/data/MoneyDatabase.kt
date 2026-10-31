package com.moneybuddy.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(transaction: Transaction)

    // Imports must never overwrite a category or note the user has already edited.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(transactions: List<Transaction>): List<Long>

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Database(entities = [Transaction::class], version = 2, exportSchema = false)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        // Version 2 adds the bank's payment-type code. Existing transactions keep an empty type.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "moneybuddy.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
