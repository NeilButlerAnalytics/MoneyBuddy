package com.moneybuddy.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodGrain { WEEK, MONTH, CUSTOM }

/** An inclusive range of days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    /** [isoDate] is a transaction date such as 2026-10-03; ISO dates sort the same way as the days they name. */
    fun contains(isoDate: String): Boolean = isoDate >= start.toString() && isoDate <= end.toString()
}

/**
 * The days the spending chart covers. A week or month can be rolling (the last 7 or 30 days, including today) or
 * "so far" (Monday, or the 1st, up to today). A custom range is whatever the user picked.
 */
data class SpendingPeriod(
    val grain: PeriodGrain = PeriodGrain.MONTH,
    val rolling: Boolean = false,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
) {
    fun range(today: LocalDate): DateRange = when (grain) {
        PeriodGrain.WEEK ->
            if (rolling) {
                DateRange(today.minusDays(6), today)
            } else {
                DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
            }
        PeriodGrain.MONTH ->
            if (rolling) DateRange(today.minusDays(29), today) else DateRange(today.withDayOfMonth(1), today)
        PeriodGrain.CUSTOM -> {
            val start = customStart ?: today.withDayOfMonth(1)
            val end = customEnd ?: today
            if (end.isBefore(start)) DateRange(end, start) else DateRange(start, end)
        }
    }

    val title: String
        get() = when (grain) {
            PeriodGrain.WEEK -> if (rolling) "Last 7 days" else "This week so far"
            PeriodGrain.MONTH -> if (rolling) "Last 30 days" else "This month so far"
            PeriodGrain.CUSTOM -> "Custom range"
        }
}

/** What the chart shows for a [SpendingPeriod]: the transactions inside it, plus the newest transaction overall. */
data class PeriodSpending(
    val period: SpendingPeriod,
    val range: DateRange,
    val transactions: List<Transaction>,
    val latestTransactionDate: String?,
)

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

fun formatRange(range: DateRange): String = when {
    range.start == range.end -> range.end.format(dayMonthYear)
    range.start.year == range.end.year -> "${range.start.format(dayMonth)} \u2013 ${range.end.format(dayMonthYear)}"
    else -> "${range.start.format(dayMonthYear)} \u2013 ${range.end.format(dayMonthYear)}"
}

/** The whole calendar month containing [isoDate], or null if it is not a date. */
fun wholeMonthOf(isoDate: String): DateRange? = runCatching {
    val date = LocalDate.parse(isoDate)
    DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
}.getOrNull()

fun formatDay(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(dayMonthYear) }.getOrDefault(isoDate)
