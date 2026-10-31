package com.moneybuddy.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BankCsvImporterTest {
    @Test
    fun parsesHsbcMiDataExportLayout() {
        val pound = "\u00A3"
        val csv = "\uFEFFDate,Type,Merchant/Description,Debit/Credit,Balance\r\n" +
            "2026-09-30,))),SAMPLE SUPERMARKET TOWN ****,-${pound}3.85,+${pound}2584.16\r\n" +
            "2026-09-29,DD,SAMPLE   ENERGY CO,-${pound}124.00,+${pound}2588.01\r\n" +
            "2026-09-28,CR,SAMPLE EMPLOYER LTD,+${pound}2850.00,+${pound}2712.01\r\n" +
            "Arranged overdraft limit,2026-10-31,+${pound}100.00\r\n"

        val statement = parseBankCsv(csv)

        assertEquals(3, statement.transactions.size)
        assertEquals(0, statement.skippedRows)
        assertEquals(listOf(-385L, -12400L, 285000L), statement.transactions.map { it.amountPence })
        assertEquals("SAMPLE SUPERMARKET TOWN", statement.transactions[0].merchant)
        assertEquals("SAMPLE ENERGY CO", statement.transactions[1].merchant)
        assertEquals(listOf(")))", "DD", "CR"), statement.transactions.map { it.type })
        assertEquals("Income", statement.transactions[2].category)
        assertEquals(258416L, statement.latestBalancePence)
        assertEquals("2026-09-30", statement.latestBalanceDate)
    }

    @Test
    fun parsesHsbcTransactionHistoryLayoutWithNoHeaderAndTheTypeOnTheDescription() {
        val csv = "30/10/2026,SAMPLE CAR PARK TOWN VIS,-2.40\r\n" +
            "28/10/2026,SAMPLE   SHOP YORK ))),-6.55\r\n" +
            "27/10/2026,SAMPLE PERSON NAME CR,\"1,250.00\"\r\n" +
            "26/10/2026,ACME LTD,-5.00\r\n"

        val statement = parseBankCsv(csv)

        assertEquals(4, statement.transactions.size)
        assertEquals(0, statement.skippedRows)
        assertEquals(listOf(-240L, -655L, 125000L, -500L), statement.transactions.map { it.amountPence })
        assertEquals(
            listOf("SAMPLE CAR PARK TOWN", "SAMPLE SHOP YORK", "SAMPLE PERSON NAME", "ACME LTD"),
            statement.transactions.map { it.merchant },
        )
        // "LTD" is part of a name, not a payment type.
        assertEquals(listOf("VIS", ")))", "CR", ""), statement.transactions.map { it.type })
        assertEquals("2026-10-30", statement.transactions[0].date)
        assertNull(statement.latestBalancePence)
    }

    @Test
    fun aDescriptionHiddenByAsterisksIsLabelledNotLost() {
        val pound = "\u00A3"
        val csv = "Date,Type,Merchant/Description,Debit/Credit,Balance\r\n" +
            "2026-09-29,TFR,************************************,-${pound}68.00,+${pound}100.00\r\n" +
            "2026-09-28,)))," + "SAMPLE STORE **** TOWN,-${pound}4.00,+${pound}168.00\r\n"

        val statement = parseBankCsv(csv)

        assertEquals(MASKED_DESCRIPTION, statement.transactions[0].merchant)
        assertEquals("TFR", statement.transactions[0].type)
        assertEquals("SAMPLE STORE TOWN", statement.transactions[1].merchant)
    }

    @Test
    fun paymentTypeCodesGetPlainEnglishNames() {
        assertEquals("Contactless", paymentTypeLabel(")))"))
        assertEquals("Direct debit", paymentTypeLabel("DD"))
        assertEquals("XYZ", paymentTypeLabel("XYZ"))
    }

    @Test
    fun parsesPaidOutPaidInStatementWithHeader() {
        val csv = """
            Date,Type,Description,Paid out,Paid in,Balance
            03/10/2026,DEB,TESCO STORES 1234,12.50,,987.50
            02/10/2026,CR,SALARY ACME LTD,,2850.00,1000.00
        """.trimIndent()

        val statement = parseBankCsv(csv)

        assertEquals(2, statement.transactions.size)
        assertEquals(-1250L, statement.transactions[0].amountPence)
        assertEquals("2026-10-03", statement.transactions[0].date)
        assertEquals("TESCO STORES 1234", statement.transactions[0].merchant)
        assertEquals("Other", statement.transactions[0].category)
        assertEquals(285000L, statement.transactions[1].amountPence)
        assertEquals("Income", statement.transactions[1].category)
        assertFalse(statement.transactions.any { it.isDemo })
    }

    @Test
    fun parsesHeaderlessThreeColumnFile() {
        val statement = parseBankCsv("03/10/2026,TESCO STORES,-12.50\n02/10/2026,SALARY,\"2,850.00\"")

        assertEquals(listOf(-1250L, 285000L), statement.transactions.map { it.amountPence })
    }

    @Test
    fun parsesMonzoStyleExportWithQuotedCommas() {
        val csv = "Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency\n" +
            "tx_1,03/10/2026,12:01:02,Card payment,\"Pret, A Manger\",,Eating out,-4.20,GBP\n"

        val statement = parseBankCsv(csv)

        assertEquals("Pret, A Manger", statement.transactions.single().merchant)
        assertEquals(-420L, statement.transactions.single().amountPence)
    }

    @Test
    fun idsAreStableAndRepeatedIdenticalRowsStayDistinct() {
        val csv = "Date,Description,Amount\n" +
            "03/10/2026,COFFEE,-3.00\n03/10/2026,COFFEE,-3.00\n"

        val first = parseBankCsv(csv).transactions
        val second = parseBankCsv(csv).transactions

        assertEquals(2, first.map { it.id }.toSet().size)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun overlappingExportsProduceTheSameIds() {
        val older = parseBankCsv("Date,Description,Amount\n01/10/2026,A,-1.00\n02/10/2026,B,-2.00\n")
        val newer = parseBankCsv("Date,Description,Amount\n02/10/2026,B,-2.00\n03/10/2026,C,-3.00\n")

        assertEquals(older.transactions[1].id, newer.transactions[0].id)
    }

    @Test
    fun latestBalanceUsesNewestRowWhateverTheOrder() {
        val descending = parseBankCsv(
            "Date,Description,Amount,Balance\n03/10/2026,B,-2.00,98.00\n02/10/2026,A,-1.00,100.00\n",
        )
        val ascending = parseBankCsv(
            "Date,Description,Amount,Balance\n02/10/2026,A,-1.00,100.00\n03/10/2026,B,-2.00,98.00\n",
        )

        assertEquals(9800L, descending.latestBalancePence)
        assertEquals(9800L, ascending.latestBalancePence)
        assertEquals("2026-10-03", ascending.latestBalanceDate)
    }

    @Test
    fun skipsUnreadableRowsAndCountsThem() {
        val statement = parseBankCsv(
            "Date,Description,Amount\nnot a date,X,-1.00\n03/10/2026,Y,abc\n03/10/2026,Z,-1.00\n",
        )

        assertEquals(1, statement.transactions.size)
        assertEquals(2, statement.skippedRows)
    }

    @Test
    fun rejectsFilesWithoutRecognisableColumns() {
        try {
            parseBankCsv("foo,bar\n1,2\n")
            fail("Expected a CsvFormatException")
        } catch (expected: CsvFormatException) {
            assertTrue(expected.message!!.contains("Date"))
        }
    }

    @Test
    fun parsesMoneyAndDateVariants() {
        assertEquals(-1234L, parsePence("(12.34)"))
        assertEquals(-1234L, parsePence("-\u00A312.34"))
        assertEquals(123456L, parsePence("1,234.56"))
        assertNull(parsePence("abc"))
        assertEquals("2026-10-03", parseDate("3 Oct 2026"))
        assertEquals("2026-10-03", parseDate("2026-10-03"))
        assertNull(parseDate("31/02/2026"))
    }

    @Test
    fun summarisesTheLatestMonthOnly() {
        val all = listOf(
            Transaction("1", "Pay", "2026-10-28", "Income", 100000),
            Transaction("2", "Shop", "2026-10-03", "Other", -2500),
            Transaction("3", "Old", "2026-09-30", "Other", -9999),
        )

        val summary = summariseLatestMonth(all)

        assertEquals("October 2026", summary.label)
        assertEquals(100000L, summary.income)
        assertEquals(2500L, summary.spending)
        assertEquals(2, summary.transactions.size)
    }
}
