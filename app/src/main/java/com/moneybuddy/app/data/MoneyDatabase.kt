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

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<Transaction>

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Dao
abstract class CategoryDao {
    // "Other" always sits last, whatever order the rest were added in.
    @Query("SELECT * FROM categories ORDER BY CASE WHEN name = 'Other' THEN 1 ELSE 0 END, rowid")
    abstract fun observeAll(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(categories: List<Category>)

    @Query("SELECT COUNT(*) FROM categories")
    abstract suspend fun count(): Int

    @Query("UPDATE categories SET name = :newName, colorArgb = :colorArgb WHERE name = :oldName")
    protected abstract suspend fun updateCategory(oldName: String, newName: String, colorArgb: Long)

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    protected abstract suspend fun moveTransactions(oldName: String, newName: String)

    @Query("DELETE FROM categories WHERE name = :name")
    protected abstract suspend fun deleteCategory(name: String)

    /** Renames and recolours a category; every transaction in it follows the new name. */
    @androidx.room.Transaction
    open suspend fun edit(oldName: String, newName: String, colorArgb: Long) {
        updateCategory(oldName, newName, colorArgb)
        if (oldName != newName) moveTransactions(oldName, newName)
    }

    /** Deletes a category and moves its transactions to [fallback], so no transaction is ever left without one. */
    @androidx.room.Transaction
    open suspend fun deleteAndReassign(name: String, fallback: String) {
        moveTransactions(name, fallback)
        deleteCategory(name)
    }
}

@Database(entities = [Transaction::class, Category::class], version = 3, exportSchema = false)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        // Version 2 adds the bank's payment-type code. Existing transactions keep an empty type.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            }
        }

        // Version 3 adds editable categories. The default list is added on first launch afterwards.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` " +
                        "(`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, PRIMARY KEY(`name`))",
                )
            }
        }

        fun create(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "moneybuddy.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
