package com.moneybuddy.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.moneybuddy.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingChartTest {
    private val size = Size(400f, 400f)
    private val centre = Offset(200f, 200f)

    // Two equal slices: the first covers 12 o'clock to 6 o'clock clockwise (the right-hand side).
    private val halves = listOf(
        ChartSlice("Right", 1000, Color.Red, emptyList()),
        ChartSlice("Left", 1000, Color.Blue, emptyList()),
    )

    @Test
    fun tapOnTheRingFindsTheSliceUnderIt() {
        val right = hitTestDonut(Offset(centre.x + 117f, centre.y), size, halves) { 0f }
        val left = hitTestDonut(Offset(centre.x - 117f, centre.y), size, halves) { 0f }

        assertEquals(0, right)
        assertEquals(1, left)
    }

    @Test
    fun tapInsideTheRingIsTheHoleAndOutsideIsBackground() {
        assertEquals(HIT_HOLE, hitTestDonut(centre, size, halves) { 0f })
        assertEquals(HIT_NONE, hitTestDonut(Offset(4f, 4f), size, halves) { 0f })
    }

    @Test
    fun aDetachedSliceIsHitWhereItIsDrawn() {
        // 360 px right of centre is beyond the ring, but inside the right-hand slice once it has moved outwards.
        val tap = Offset(centre.x + 191f, centre.y)

        assertEquals(HIT_NONE, hitTestDonut(tap, size, halves) { 0f })
        assertEquals(0, hitTestDonut(tap, size, halves) { if (it == 0) 1f else 0f })
    }

    @Test
    fun anEmptyChartHasNothingToHit() {
        assertEquals(HIT_NONE, hitTestDonut(centre, size, emptyList()) { 0f })
    }

    @Test
    fun slicesGroupSpendingByCategoryLargestFirstAndNewestTransactionFirst() {
        val slices = buildSlices(
            listOf(
                Transaction("1", "Shop A", "2026-10-01", "Shopping", -1000),
                Transaction("2", "Shop B", "2026-10-05", "Shopping", -500),
                Transaction("3", "Mortgage", "2026-10-02", "Mortgage / Rent", -90000),
                Transaction("4", "Salary", "2026-10-03", "Income", 285000),
            ),
        )

        assertEquals(listOf("Mortgage / Rent", "Shopping"), slices.map { it.category })
        assertEquals(1500L, slices[1].totalPence)
        assertEquals(listOf("Shop B", "Shop A"), slices[1].transactions.map { it.merchant })
    }

    @Test
    fun sameCategoryAlwaysGetsTheSameColour() {
        assertEquals(categoryColor("Groceries"), categoryColor("Groceries"))
        assertEquals(categoryColor("Imported thing"), categoryColor("Imported thing"))
    }
}
