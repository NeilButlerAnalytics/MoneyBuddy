package com.moneybuddy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A spending category the user can add to, rename, recolour and delete. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val colorArgb: Long,
)

/** The fallback for deleted categories and uncategorised spending. It cannot be renamed or deleted. */
const val OTHER_CATEGORY = "Other"

/** Used for money coming in. It is not a spending category, so it is reserved and not managed here. */
const val INCOME_CATEGORY = "Income"

const val MAX_CATEGORY_NAME_LENGTH = 30

val categoryPalette: List<Long> = listOf(
    0xFFF28C28, 0xFF39B978, 0xFF5B8DEF, 0xFFE5989B, 0xFF6BCBCA,
    0xFFB388EB, 0xFFF2C14E, 0xFFE56B6F, 0xFF8FBF5A, 0xFF4FB0C6,
    0xFFD98E73, 0xFF9AA5B1, 0xFFC77DBB,
)

val defaultCategories: List<Category> = listOf(
    "Groceries", "Shopping", "Fuel", "Transport", "Utilities", "Mortgage / Rent", "Insurance",
    "Eating out", "Entertainment", "Subscriptions", "Family", "Holidays", OTHER_CATEGORY,
).mapIndexed { index, name -> Category(name, categoryPalette[index]) }

/** A stable colour for a category that is not in the list, such as one left behind by an older import. */
fun fallbackCategoryColor(name: String): Long =
    categoryPalette[(name.hashCode() and Int.MAX_VALUE) % categoryPalette.size]

/**
 * Returns a message if [rawName] cannot be used, or null if it can. [original] is the category being edited (null
 * when adding), so keeping or re-casing its own name is allowed.
 */
fun validateCategoryName(rawName: String, original: String?, existing: List<String>): String? {
    val name = rawName.trim()
    return when {
        name.isEmpty() -> "Enter a name."
        name.length > MAX_CATEGORY_NAME_LENGTH -> "Use $MAX_CATEGORY_NAME_LENGTH characters or fewer."
        name.equals(INCOME_CATEGORY, ignoreCase = true) -> "\u201C$INCOME_CATEGORY\u201D is kept for money coming in."
        original == OTHER_CATEGORY && name != OTHER_CATEGORY -> "\u201C$OTHER_CATEGORY\u201D cannot be renamed."
        existing.any { it != original && it.equals(name, ignoreCase = true) } -> "That category already exists."
        else -> null
    }
}
