package com.moneybuddy.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {
    private fun tx(
        id: String,
        merchant: String,
        date: String,
        pence: Long,
        type: String,
        category: String = "Other",
        note: String = "",
        demo: Boolean = false,
    ) = Transaction(id, merchant, date, category, pence, note, demo, type)

    @Test
    fun importingTheSameRowsAgainAddsNothing() {
        val rows = listOf(tx("a", "SHOP", "2026-10-01", -500, "VIS"), tx("b", "CAFE", "2026-10-02", -300, ")))"))

        val plan = planImport(existing = rows, incoming = rows)

        assertTrue(plan.toInsert.isEmpty())
        assertTrue(plan.toUpdate.isEmpty())
        assertEquals(2, plan.alreadyPresent)
    }

    @Test
    fun theSamePaymentWordedDifferentlyByAnotherExportIsNotDuplicated() {
        val miData = listOf(tx("m1", "TESCO STORES LANCASHIRE", "2026-09-29", -4391, ")))"))
        val history = listOf(tx("h1", "TESCO STORES 5731 LANCASHIRE", "2026-09-29", -4391, ")))"))

        val plan = planImport(existing = miData, incoming = history)

        assertTrue(plan.toInsert.isEmpty())
        assertEquals(1, plan.alreadyPresent)
    }

    @Test
    fun rowsFromAfterTheStoredPeriodAreNew() {
        val stored = listOf(tx("m1", "SHOP", "2026-09-30", -100, "VIS"))
        val incoming = listOf(
            tx("h1", "SHOP", "2026-09-30", -100, "VIS"),
            tx("h2", "OTHER SHOP", "2026-10-01", -250, "VIS"),
        )

        val plan = planImport(stored, incoming)

        assertEquals(listOf("h2"), plan.toInsert.map { it.id })
        assertEquals(1, plan.alreadyPresent)
    }

    @Test
    fun twoGenuineIdenticalPaymentsOnOneDayAreStillTwo() {
        val stored = listOf(tx("m1", "COFFEE", "2026-10-01", -300, ")))"))
        val incoming = listOf(tx("h1", "COFFEE", "2026-10-01", -300, ")))"), tx("h2", "COFFEE", "2026-10-01", -300, ")))"))

        val plan = planImport(stored, incoming)

        assertEquals(1, plan.toInsert.size)
        assertEquals(1, plan.alreadyPresent)
    }

    @Test
    fun aHiddenDescriptionIsFilledInAndTheUsersCategoryAndNoteAreKept() {
        val stored = listOf(
            tx("m1", MASKED_DESCRIPTION, "2026-09-29", -6800, "TFR", category = "Family", note = "school trip"),
        )
        val incoming = listOf(tx("h1", "SAMPLE PERSON NAME", "2026-09-29", -6800, "TFR"))

        val plan = planImport(stored, incoming)

        assertTrue(plan.toInsert.isEmpty())
        val updated = plan.toUpdate.single()
        assertEquals("m1", updated.id)
        assertEquals("SAMPLE PERSON NAME", updated.merchant)
        assertEquals("Family", updated.category)
        assertEquals("school trip", updated.note)
    }

    @Test
    fun aRowAlreadyStoredAsUnknownIsRelabelledAsHidden() {
        val stored = listOf(tx("m1", "Unknown", "2026-09-29", -6800, "TFR"))
        val incoming = listOf(tx("h1", MASKED_DESCRIPTION, "2026-09-29", -6800, "TFR"))

        val plan = planImport(stored, incoming)

        assertEquals(MASKED_DESCRIPTION, plan.toUpdate.single().merchant)
    }

    @Test
    fun descriptionsAreNotGuessedWhenSeveralRowsShareADayAmountAndType() {
        val stored = listOf(
            tx("m1", MASKED_DESCRIPTION, "2026-09-29", -1000, "TFR"),
            tx("m2", MASKED_DESCRIPTION, "2026-09-29", -1000, "TFR"),
        )
        val incoming = listOf(
            tx("h1", "PERSON ONE", "2026-09-29", -1000, "TFR"),
            tx("h2", "PERSON TWO", "2026-09-29", -1000, "TFR"),
        )

        val plan = planImport(stored, incoming)

        assertTrue(plan.toUpdate.isEmpty())
        assertTrue(plan.toInsert.isEmpty())
    }

    @Test
    fun demoTransactionsNeverCountAsAlreadyImported() {
        val stored = listOf(tx("d1", "Demo shop", "2026-10-01", -500, "", demo = true))
        val incoming = listOf(tx("h1", "SHOP", "2026-10-01", -500, ""))

        val plan = planImport(stored, incoming)

        assertEquals(1, plan.toInsert.size)
    }
}
