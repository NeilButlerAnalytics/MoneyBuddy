package com.moneybuddy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val merchant: String,
    val date: String,
    val category: String,
    val amountPence: Long,
    val note: String = "",
    val isDemo: Boolean = true,
    // The bank's payment-type code (for example DD or VIS) where the statement provides one.
    val type: String = "",
)

/** Plain-English names for HSBC UK payment-type codes. Unknown codes are shown as they appear. */
fun paymentTypeLabel(code: String): String = when (code.trim().uppercase()) {
    ")))" -> "Contactless"
    "VIS" -> "Visa debit card"
    "DD" -> "Direct debit"
    "SO" -> "Standing order"
    "CR" -> "Credit"
    "TFR" -> "Transfer"
    "BP" -> "Bill payment"
    "ATM" -> "Cash machine"
    "VRP" -> "Variable recurring payment"
    "OBP" -> "Open Banking payment"
    else -> code.trim()
}

val demoTransactions = listOf(
    Transaction("1", "Salary", "2026-10-28", "Income", 285000),
    Transaction("2", "HSBC Mortgage", "2026-10-01", "Mortgage / Rent", -95000),
    Transaction("3", "Tesco", "2026-10-03", "Groceries", -8420),
    Transaction("4", "British Gas", "2026-10-05", "Utilities", -12400),
    Transaction("5", "Shell", "2026-10-07", "Fuel", -5200),
    Transaction("6", "Netflix", "2026-10-09", "Subscriptions", -1599),
    Transaction("7", "Sainsbury's", "2026-10-12", "Groceries", -6350),
    Transaction("8", "Amazon", "2026-10-15", "Shopping", -3499),
    Transaction("9", "Local Restaurant", "2026-10-18", "Eating out", -4250),
    Transaction("10", "Trainline", "2026-10-21", "Transport", -2800),
    Transaction("11", "Spotify", "2026-10-23", "Subscriptions", -1099),
)
