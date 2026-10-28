package com.masroufi.core

import jxl.CellType
import jxl.DateCell
import jxl.NumberCell
import jxl.Sheet
import jxl.Workbook
import jxl.WorkbookSettings
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class ImportException(message: String) : Exception(message)

data class ImportResult(
    val transactions: List<Transaction>,
    /** 1-based spreadsheet row numbers that could not be read. */
    val skippedRows: List<Int>,
)

/** Reads the .xls export of the expense-tracker app (sheet "المعاملات"). */
object XlsImporter {

    private enum class Column(vararg val headers: String) {
        NOTE("الملاحظات", "notes", "note"),
        GROUP("المجموعة", "group"),
        CONTACT("جهة الإتصال", "جهة الاتصال", "contact"),
        TO_WALLET("الى (المحفظة)", "إلى (المحفظة)", "to (wallet)", "to wallet"),
        WALLET("المحفظة", "wallet"),
        CURRENCY("العملة", "currency"),
        AMOUNT("التكلفة", "المبلغ", "amount", "cost"),
        CATEGORY("القسم", "category"),
        MAIN_CATEGORY("القسم الرئيسي", "main category"),
        DATE("التاريخ", "date"),
    }

    /** Column positions used by the app's export when no header row is recognised. */
    private val defaultLayout = Column.entries.associateWith { it.ordinal }

    fun parse(input: InputStream): ImportResult {
        val settings = WorkbookSettings().apply {
            setLocale(Locale.ENGLISH)
            setGCDisabled(true)
            setSuppressWarnings(true)
        }
        val workbook = try {
            Workbook.getWorkbook(input, settings)
        } catch (e: Exception) {
            throw ImportException("Not a readable .xls file: ${e.message}")
        }
        try {
            val sheet = workbook.sheets.firstOrNull { it.rows > 0 }
                ?: throw ImportException("The file has no data")
            return parseSheet(sheet)
        } finally {
            workbook.close()
        }
    }

    private fun parseSheet(sheet: Sheet): ImportResult {
        val headerLayout = detectLayout(sheet)
        val layout = headerLayout ?: defaultLayout
        val firstDataRow = if (headerLayout != null) 1 else 0

        val transactions = ArrayList<Transaction>(sheet.rows)
        val skipped = ArrayList<Int>()
        for (r in firstDataRow until sheet.rows) {
            fun text(col: Column): String {
                val c = layout[col] ?: return ""
                return if (c < sheet.getRow(r).size) sheet.getCell(c, r).contents.trim() else ""
            }
            if (sheet.getRow(r).all { it.contents.isBlank() }) continue

            val amount = readAmount(sheet, layout[Column.AMOUNT], r)
            val date = readDate(sheet, layout[Column.DATE], r)
            if (amount == null || date == null) {
                skipped += r + 1
                continue
            }
            transactions += Transaction(
                note = text(Column.NOTE),
                group = text(Column.GROUP),
                contact = text(Column.CONTACT),
                toWallet = text(Column.TO_WALLET),
                wallet = text(Column.WALLET),
                currency = text(Column.CURRENCY),
                amount = amount,
                category = text(Column.CATEGORY),
                mainCategory = text(Column.MAIN_CATEGORY),
                timestamp = Transaction.timestampOf(date),
            )
        }
        if (transactions.isEmpty()) {
            throw ImportException("No transactions found in the file")
        }
        transactions.sortByDescending { it.timestamp }
        return ImportResult(transactions, skipped)
    }

    private fun detectLayout(sheet: Sheet): Map<Column, Int>? {
        val header = sheet.getRow(0).map { ArabicDateParser.normalize(it.contents) }
        val layout = Column.entries.mapNotNull { col ->
            val names = col.headers.map(ArabicDateParser::normalize)
            val idx = header.indexOfFirst { it in names }
            if (idx >= 0) col to idx else null
        }.toMap()
        return if (Column.AMOUNT in layout && Column.DATE in layout) layout else null
    }

    private fun readAmount(sheet: Sheet, col: Int?, row: Int): Double? {
        if (col == null || col >= sheet.getRow(row).size) return null
        val cell = sheet.getCell(col, row)
        if (cell.type == CellType.NUMBER || cell.type == CellType.NUMBER_FORMULA) {
            return (cell as NumberCell).value
        }
        return ArabicDateParser.normalizeDigits(cell.contents)
            .replace('٫', '.')
            .replace(Regex("[\\s,٬]"), "")
            .toDoubleOrNull()
    }

    private fun readDate(sheet: Sheet, col: Int?, row: Int): LocalDateTime? {
        if (col == null || col >= sheet.getRow(row).size) return null
        val cell = sheet.getCell(col, row)
        if (cell.type == CellType.DATE || cell.type == CellType.DATE_FORMULA) {
            // jxl returns spreadsheet dates as UTC instants.
            return LocalDateTime.ofInstant((cell as DateCell).date.toInstant(), ZoneOffset.UTC)
        }
        return ArabicDateParser.parse(cell.contents)
    }
}
