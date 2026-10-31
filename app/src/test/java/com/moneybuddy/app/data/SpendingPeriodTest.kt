package com.moneybuddy.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingPeriodTest {
    // Thursday 29 October 2026.
    private val thursday = LocalDate.of(2026, 10, 29)

    @Test
    fun weekSoFarRunsFromMondayToToday() {
        val range = SpendingPeriod(PeriodGrain.WEEK, rolling = false).range(thursday)

        assertEquals(LocalDate.of(2026, 10, 26), range.start)
        assertEquals(thursday, range.end)
    }

    @Test
    fun weekSoFarOnAMondayIsJustThatDay() {
        val monday = LocalDate.of(2026, 10, 26)

        assertEquals(DateRange(monday, monday), SpendingPeriod(PeriodGrain.WEEK).range(monday))
    }

    @Test
    fun weekSoFarOnASundayStartsOnTheMondayBefore() {
        val sunday = LocalDate.of(2026, 11, 1)

        assertEquals(LocalDate.of(2026, 10, 26), SpendingPeriod(PeriodGrain.WEEK).range(sunday).start)
    }

    @Test
    fun rollingWeekIsSevenDaysIncludingToday() {
        val range = SpendingPeriod(PeriodGrain.WEEK, rolling = true).range(thursday)

        assertEquals(LocalDate.of(2026, 10, 23), range.start)
        assertEquals(thursday, range.end)
    }

    @Test
    fun monthSoFarRunsFromTheFirstToToday() {
        val range = SpendingPeriod(PeriodGrain.MONTH, rolling = false).range(thursday)

        assertEquals(LocalDate.of(2026, 10, 1), range.start)
        assertEquals(thursday, range.end)
    }

    @Test
    fun rollingMonthIsThirtyDaysIncludingToday() {
        val range = SpendingPeriod(PeriodGrain.MONTH, rolling = true).range(thursday)

        assertEquals(LocalDate.of(2026, 9, 30), range.start)
        assertEquals(thursday, range.end)
    }

    @Test
    fun customRangeIsUsedAsChosenAndSwappedIfBackwards() {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 31)

        assertEquals(DateRange(start, end), SpendingPeriod(PeriodGrain.CUSTOM, customStart = start, customEnd = end).range(thursday))
        assertEquals(DateRange(start, end), SpendingPeriod(PeriodGrain.CUSTOM, customStart = end, customEnd = start).range(thursday))
    }

    @Test
    fun rangeIncludesItsFirstAndLastDayButNothingOutside() {
        val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertTrue(range.contains("2026-10-01"))
        assertTrue(range.contains("2026-10-31"))
        assertFalse(range.contains("2026-09-30"))
        assertFalse(range.contains("2026-11-01"))
    }

    @Test
    fun rangesAreDescribedForPeople() {
        assertEquals("26 Oct \u2013 29 Oct 2026", formatRange(DateRange(LocalDate.of(2026, 10, 26), thursday)))
        assertEquals("29 Oct 2026", formatRange(DateRange(thursday, thursday)))
        assertEquals(
            "20 Dec 2025 \u2013 3 Jan 2026",
            formatRange(DateRange(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 3))),
        )
    }

    @Test
    fun wholeMonthOfFindsTheFirstAndLastDay() {
        assertEquals(
            DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            wholeMonthOf("2026-02-14"),
        )
        assertNull(wholeMonthOf("not a date"))
    }
}

class CategoryTest {
    private val existing = listOf("Groceries", "Fuel", "Other")

    @Test
    fun aNewNameIsAccepted() {
        assertNull(validateCategoryName("Pets", null, existing))
    }

    @Test
    fun blankAndOverlongNamesAreRejected() {
        assertEquals("Enter a name.", validateCategoryName("   ", null, existing))
        assertEquals(
            "Use $MAX_CATEGORY_NAME_LENGTH characters or fewer.",
            validateCategoryName("x".repeat(MAX_CATEGORY_NAME_LENGTH + 1), null, existing),
        )
    }

    @Test
    fun duplicatesAreRejectedWhateverTheCase() {
        assertEquals("That category already exists.", validateCategoryName("groceries", null, existing))
    }

    @Test
    fun aCategoryMayKeepOrRecaseItsOwnName() {
        assertNull(validateCategoryName("Groceries", "Groceries", existing))
        assertNull(validateCategoryName("GROCERIES", "Groceries", existing))
    }

    @Test
    fun renamingOntoAnotherCategoryIsRejected() {
        assertEquals("That category already exists.", validateCategoryName("Fuel", "Groceries", existing))
    }

    @Test
    fun incomeIsReservedAndOtherCannotBeRenamed() {
        assertTrue(validateCategoryName("income", null, existing)!!.contains("money coming in"))
        assertTrue(validateCategoryName("Misc", "Other", existing)!!.contains("cannot be renamed"))
        assertNull(validateCategoryName("Other", "Other", existing))
    }

    @Test
    fun theDefaultListHasUniqueNamesAndEndsWithOther() {
        assertEquals(defaultCategories.size, defaultCategories.map { it.name.lowercase() }.toSet().size)
        assertEquals(OTHER_CATEGORY, defaultCategories.last().name)
    }
}
