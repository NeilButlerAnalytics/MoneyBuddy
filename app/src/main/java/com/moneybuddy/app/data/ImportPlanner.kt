package com.moneybuddy.app.data

/**
 * What an import should do. [toInsert] are genuinely new transactions, [toUpdate] are existing ones whose hidden
 * description can now be filled in, and [alreadyPresent] is how many incoming rows were already stored.
 */
class ImportPlan(
    val toInsert: List<Transaction>,
    val toUpdate: List<Transaction>,
    val alreadyPresent: Int,
)

private data class GroupKey(val date: String, val amountPence: Long, val type: String)

private fun Transaction.hasHiddenDescription() = merchant == MASKED_DESCRIPTION || merchant == "Unknown"

/**
 * Works out which incoming rows are new. Two exports of the same account can word the same payment differently
 * (HSBC's MiData file masks parts of the text that its other download shows in full), so a row counts as already
 * stored when the same day, amount and payment type are already there, not when the description matches. The
 * same-day same-amount rows are counted, so two genuine £3.00 coffees on one day are still two transactions.
 *
 * Where a stored transaction has a hidden description and the incoming row for it is unambiguous, the stored one
 * is updated with the readable description.
 */
fun planImport(existing: List<Transaction>, incoming: List<Transaction>): ImportPlan {
    val stored = existing.filter { !it.isDemo }
        .groupBy { GroupKey(it.date, it.amountPence, it.type) }
        .mapValues { (_, rows) -> rows.sortedBy { it.id } }

    val toInsert = mutableListOf<Transaction>()
    val toUpdate = mutableListOf<Transaction>()
    var alreadyPresent = 0

    incoming.groupBy { GroupKey(it.date, it.amountPence, it.type) }.forEach { (key, rows) ->
        val present = stored[key].orEmpty()
        rows.forEachIndexed { index, row -> if (index >= present.size) toInsert += row else alreadyPresent += 1 }

        if (present.size == 1 && rows.size == 1) {
            val old = present.first()
            val new = rows.first()
            if (old.hasHiddenDescription() && new.merchant != old.merchant && new.merchant != "Unknown") {
                toUpdate += old.copy(merchant = new.merchant)
            }
        } else if (present.size == rows.size && present.all { it.hasHiddenDescription() } &&
            rows.all { it.merchant == MASKED_DESCRIPTION }
        ) {
            // Every one is hidden on both sides, so only the wording of "hidden" needs tidying.
            toUpdate += present.filter { it.merchant != MASKED_DESCRIPTION }.map { it.copy(merchant = MASKED_DESCRIPTION) }
        }
    }
    return ImportPlan(toInsert, toUpdate, alreadyPresent)
}
