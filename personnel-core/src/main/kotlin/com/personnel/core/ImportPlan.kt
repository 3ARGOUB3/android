package com.personnel.core

/**
 * How one sheet will be imported: which row holds the column names, where the lines start,
 * which columns are left out and which register receives it. Rows are zero-based here and
 * shown to the user as Excel row numbers (+1).
 */
data class SheetChoice(
    val sheet: Sheet,
    val headerRow: Int,
    val firstDataRow: Int,
    val excluded: Set<Int> = emptySet(),
    /** Register id, or null to skip this sheet. */
    val target: String? = null,
) {
    val rowCount: Int get() = sheet.rows.size

    /** All the columns this header row gives, before leaving any out. */
    val allColumns: List<String> by lazy { SpreadsheetReader.columnsOf(sheet, headerRow, firstDataRow) }

    /** What will be imported. */
    val table: Table by lazy {
        SpreadsheetReader.toTable(sheet, headerRow, firstDataRow, allColumns.indices.toSet() - excluded)
    }

    /** Raw cells of a row, for showing the user what is on it. */
    fun preview(row: Int): String =
        sheet.rows.getOrNull(row)?.filter { it.isNotBlank() }?.joinToString(" · ").orEmpty()

    /** New header row: lines start right below it and every column is kept again. */
    fun withHeaderRow(row: Int): SheetChoice {
        val header = row.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
        return copy(headerRow = header, firstDataRow = header + 1, excluded = emptySet())
    }

    fun withFirstDataRow(row: Int): SheetChoice {
        val first = row.coerceIn(headerRow + 1, rowCount.coerceAtLeast(headerRow + 1))
        // A different first line can change how many columns there are; keep only valid exclusions.
        val updated = copy(firstDataRow = first)
        return updated.copy(excluded = excluded.filter { it < updated.allColumns.size }.toSet())
    }

    fun toggleColumn(index: Int): SheetChoice =
        copy(excluded = if (index in excluded) excluded - index else excluded + index)

    companion object {
        /**
         * One choice per non-empty sheet, with the detected header row. Each sheet goes to the register
         * its name suggests; with [forRegisterId], the best sheet goes to that register and the rest are skipped.
         */
        fun plan(sheets: List<Sheet>, registers: List<Register>, forRegisterId: String?): List<SheetChoice> {
            val choices = sheets.mapNotNull { sheet ->
                SpreadsheetReader.detectHeaderRow(sheet)?.let { SheetChoice(sheet, it, it + 1) }
            }
            if (forRegisterId != null) {
                val best = choices.indexOfFirst { SheetMatcher.bestRegister(it.sheet.name, registers)?.id == forRegisterId }
                    .takeIf { it >= 0 } ?: choices.indexOfFirst { it.table.rows.isNotEmpty() }.coerceAtLeast(0)
                return choices.mapIndexed { i, c -> if (i == best) c.copy(target = forRegisterId) else c }
            }
            val used = HashSet<String>()
            return choices.map { c ->
                c.copy(target = SheetMatcher.bestRegister(c.sheet.name, registers)?.id?.takeIf { used.add(it) })
            }
        }

        /** Gives [registerId] to sheet [index]; a register takes one sheet, so others lose it. */
        fun setTarget(choices: List<SheetChoice>, index: Int, registerId: String?): List<SheetChoice> =
            choices.mapIndexed { i, c ->
                when {
                    i == index -> c.copy(target = registerId)
                    registerId != null && c.target == registerId -> c.copy(target = null)
                    else -> c
                }
            }
    }
}
