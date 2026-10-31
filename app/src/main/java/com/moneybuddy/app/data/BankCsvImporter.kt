package com.moneybuddy.app.data

import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

class CsvFormatException(message: String) : Exception(message)

data class ParsedStatement(
    val transactions: List<Transaction>,
    val skippedRows: Int,
    val latestBalancePence: Long?,
    val latestBalanceDate: String?,
)

data class MonthSummary(
    val label: String,
    val income: Long,
    val spending: Long,
    val transactions: List<Transaction>,
)

private class Columns(
    val date: Int,
    val description: Int,
    val amount: Int?,
    val paidOut: Int?,
    val paidIn: Int?,
    val balance: Int?,
    val type: Int? = null,
) {
    /** A row with fewer cells than this is a summary line (such as an overdraft limit), not a transaction. */
    val requiredWidth: Int = listOfNotNull(date, description, amount, paidOut, paidIn).max() + 1
}

private val dateFormats = listOf(
    "d/M/uuuu", "uuuu-MM-dd", "d-M-uuuu", "d MMM uuuu", "d/M/uu", "uuuu/MM/dd",
).map { pattern ->
    // Strict, so an impossible date such as 31/02/2026 is rejected rather than moved to 28 February.
    DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)
        .withResolverStyle(ResolverStyle.STRICT)
}

/**
 * Parses a bank statement CSV (HSBC UK online banking, Monzo, or any file with date, description and amount
 * columns). The file is only read in memory; nothing is logged.
 */
fun parseBankCsv(text: String): ParsedStatement {
    val rows = readCsvRows(text)
    if (rows.isEmpty()) throw CsvFormatException("The file is empty.")

    val headerIndex = rows.take(10).indexOfFirst { findColumns(it) != null }
    val columns: Columns
    val dataRows: List<List<String>>
    if (headerIndex >= 0) {
        columns = findColumns(rows[headerIndex])!!
        dataRows = rows.drop(headerIndex + 1)
    } else if (rows.first().size == 3 && parseDate(rows.first()[0]) != null && parsePence(rows.first()[2]) != null) {
        columns = Columns(date = 0, description = 1, amount = 2, paidOut = null, paidIn = null, balance = null)
        dataRows = rows
    } else {
        throw CsvFormatException(
            "Could not find Date, Description and Amount (or Paid out / Paid in) columns in the first rows.",
        )
    }

    class Entry(
        val date: String,
        val pence: Long,
        val description: String,
        val balance: Long?,
        val type: String,
    )

    var skipped = 0
    val entries = mutableListOf<Entry>()
    for (row in dataRows) {
        val date = parseDate(row.getOrNull(columns.date))
        val pence = rowAmount(row, columns)
        if (date == null || pence == null) {
            // Short lines such as "Arranged overdraft limit,2026-10-31,+£100.00" are not transactions.
            if (row.size >= columns.requiredWidth) skipped += 1
            continue
        }
        val description = cleanDescription(row.getOrNull(columns.description).orEmpty())
        val balance = columns.balance?.let { parsePence(row.getOrNull(it)) }
        val type = columns.type?.let { row.getOrNull(it) }.orEmpty().trim()
        entries += Entry(date, pence, description, balance, type)
    }

    val occurrences = HashMap<String, Int>()
    val transactions = entries.map { entry ->
        val key = "${entry.date}|${entry.pence}|${entry.description.lowercase(Locale.ROOT)}"
        val occurrence = occurrences.merge(key, 1, Int::plus)!!
        Transaction(
            id = stableId(key, occurrence),
            merchant = entry.description.ifEmpty { "Unknown" },
            date = entry.date,
            category = if (entry.pence > 0) "Income" else "Other",
            amountPence = entry.pence,
            isDemo = false,
            type = entry.type,
        )
    }

    val withBalance = entries.filter { it.balance != null }
    val latestDate = withBalance.maxOfOrNull { it.date }
    val newestFirst = entries.size > 1 && entries.first().date > entries.last().date
    val latest = latestDate?.let { date ->
        val sameDay = withBalance.filter { it.date == date }
        if (newestFirst) sameDay.first() else sameDay.last()
    }

    return ParsedStatement(transactions, skipped, latest?.balance, latest?.date)
}

fun summariseLatestMonth(all: List<Transaction>): MonthSummary {
    val latest = all.maxOfOrNull { it.date.take(7) } ?: return MonthSummary("No data", 0, 0, emptyList())
    val inMonth = all.filter { it.date.startsWith(latest) }
    val label = runCatching {
        YearMonth.parse(latest).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK))
    }.getOrDefault(latest)
    return MonthSummary(
        label = label,
        income = inMonth.filter { it.amountPence > 0 }.sumOf { it.amountPence },
        spending = inMonth.filter { it.amountPence < 0 }.sumOf { -it.amountPence },
        transactions = inMonth,
    )
}

internal fun readCsvRows(text: String): List<List<String>> {
    val source = text.removePrefix("\uFEFF")
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < source.length) {
        val char = source[index]
        if (inQuotes) {
            if (char == '"') {
                if (index + 1 < source.length && source[index + 1] == '"') {
                    field.append('"')
                    index += 1
                } else {
                    inQuotes = false
                }
            } else {
                field.append(char)
            }
        } else if (char == '"') {
            inQuotes = true
        } else if (char == ',') {
            row.add(field.toString())
            field.setLength(0)
        } else if (char == '\n' || char == '\r') {
            if (char == '\r' && index + 1 < source.length && source[index + 1] == '\n') index += 1
            row.add(field.toString())
            field.setLength(0)
            rows.add(row)
            row = mutableListOf()
        } else {
            field.append(char)
        }
        index += 1
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows.filter { cells -> cells.any { it.isNotBlank() } }
}

private fun normaliseHeader(header: String): String =
    header.lowercase(Locale.UK)
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("[^a-z ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Collapses runs of spaces and removes the "****" masking some banks add to card payment descriptions. */
internal fun cleanDescription(raw: String): String =
    raw.replace(Regex("\\*{2,}"), " ").replace(Regex("\\s+"), " ").trim()

private fun findColumns(header: List<String>): Columns? {
    val names = header.map(::normaliseHeader)
    fun first(vararg candidates: String): Int? =
        candidates.firstNotNullOfOrNull { name -> names.indexOf(name).takeIf { it >= 0 } }

    val date = first("date", "transaction date", "posting date", "booking date") ?: return null
    val description = first(
        "name", "merchant", "payee", "merchant description", "description", "details",
        "transaction description", "narrative",
    ) ?: return null
    // HSBC's "Debit/Credit" is one signed amount column (-£3.85 / +£2850.00).
    val amount = first("amount", "value", "debit credit")
    val paidOut = first("paid out", "money out", "debit", "debit amount", "withdrawals")
    val paidIn = first("paid in", "money in", "credit", "credit amount", "deposits")
    if (amount == null && paidOut == null && paidIn == null) return null
    return Columns(
        date, description, amount, paidOut, paidIn,
        balance = first("balance", "running balance"),
        type = first("type", "transaction type"),
    )
}

private fun rowAmount(row: List<String>, columns: Columns): Long? {
    if (columns.amount != null) return parsePence(row.getOrNull(columns.amount))
    val out = parsePence(row.getOrNull(columns.paidOut ?: -1))
    val into = parsePence(row.getOrNull(columns.paidIn ?: -1))
    if (out == null && into == null) return null
    return kotlin.math.abs(into ?: 0L) - kotlin.math.abs(out ?: 0L)
}

internal fun parseDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    for (format in dateFormats) {
        try {
            return LocalDate.parse(value, format).toString()
        } catch (_: Exception) {
            // Try the next format.
        }
    }
    return null
}

internal fun parsePence(raw: String?): Long? {
    var value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    var negative = false
    if (value.startsWith("(") && value.endsWith(")")) {
        negative = true
        value = value.substring(1, value.length - 1)
    }
    value = value.replace("\u00A3", "").replace(",", "").replace(" ", "")
    val pence = value.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
        ?: return null
    return if (negative) -pence else pence
}

private fun stableId(key: String, occurrence: Int): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("csv|$key|$occurrence".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(32)
}
