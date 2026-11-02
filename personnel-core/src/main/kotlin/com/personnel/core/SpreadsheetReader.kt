package com.personnel.core

import jxl.CellType
import jxl.DateCell
import jxl.NumberCell
import jxl.Workbook
import jxl.WorkbookSettings
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

class ImportException(message: String) : Exception(message)

/** A sheet as text cells, row by row. */
data class Sheet(val name: String, val rows: List<List<String>>)

/** A sheet split into its header row and its data rows. */
data class Table(val sheetName: String, val columns: List<String>, val rows: List<List<String>>)

/** Reads .xlsx and .xls workbooks into text. Dates come out as dd/MM/yyyy. */
object SpreadsheetReader {

    fun read(bytes: ByteArray): List<Sheet> {
        val sheets = when {
            bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> readXlsx(bytes)
            bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() -> readXls(bytes)
            else -> throw ImportException("Fichier non reconnu (utilisez un fichier Excel .xlsx ou .xls)")
        }
        return sheets.filter { sheet -> sheet.rows.any { row -> row.any { it.isNotBlank() } } }
    }

    /**
     * The header row (zero-based): the first row naming a "Matricule" or "Nom" column, else the
     * first row with two or more cells. Title lines above it are skipped. Null for an empty sheet.
     */
    fun detectHeaderRow(sheet: Sheet): Int? {
        val rows = sheet.rows
        val searched = rows.take(30)
        var headerIndex = searched.indexOfFirst { row ->
            row.any { ColumnKind.of(it) == ColumnKind.MATRICULE } ||
                row.count { ColumnKind.of(it) in setOf(ColumnKind.NAME, ColumnKind.FUNCTION) } >= 1 &&
                row.count { it.isNotBlank() } >= 2
        }
        if (headerIndex < 0) headerIndex = searched.indexOfFirst { row -> row.count { it.isNotBlank() } >= 2 }
        if (headerIndex < 0) headerIndex = rows.indexOfFirst { row -> row.any { it.isNotBlank() } }
        return headerIndex.takeIf { it >= 0 }
    }

    /** The sheet read with the detected header row, all columns and the rows right below it. */
    fun toTable(sheet: Sheet): Table? =
        detectHeaderRow(sheet)?.let { toTable(sheet, it, it + 1) }

    /** Every column name for this header row (empty headers become "Colonne N"). */
    fun columnsOf(sheet: Sheet, headerRow: Int, firstDataRow: Int): List<String> {
        val header = sheet.rows.getOrElse(headerRow) { emptyList() }
        val width = maxOf(
            header.indexOfLast { it.isNotBlank() } + 1,
            dataRows(sheet, firstDataRow).maxOfOrNull { row -> row.indexOfLast { it.isNotBlank() } + 1 } ?: 0,
        )
        return (0 until width).map { i ->
            header.getOrNull(i)?.replace(Regex("\\s+"), " ")?.trim().orEmpty().ifEmpty { "Colonne ${i + 1}" }
        }
    }

    /**
     * The sheet read with the user's choices: column names from [headerRow], lines from
     * [firstDataRow] on (both zero-based), keeping only the [keep] columns (null = all).
     * Lines left empty in the kept columns are dropped.
     */
    fun toTable(sheet: Sheet, headerRow: Int, firstDataRow: Int, keep: Set<Int>? = null): Table {
        val all = columnsOf(sheet, headerRow, firstDataRow)
        val kept = all.indices.filter { keep == null || it in keep }
        val body = dataRows(sheet, firstDataRow)
            .map { row -> kept.map { i -> row.getOrNull(i)?.trim().orEmpty() } }
            .filter { row -> row.any { it.isNotEmpty() } }
        return Table(sheet.name, kept.map { all[it] }, body)
    }

    private fun dataRows(sheet: Sheet, firstDataRow: Int): List<List<String>> =
        sheet.rows.drop(firstDataRow.coerceAtLeast(0))

    // ---------- .xls ----------

    private fun readXls(bytes: ByteArray): List<Sheet> {
        val settings = WorkbookSettings().apply {
            setLocale(Locale.FRENCH)
            setGCDisabled(true)
            setSuppressWarnings(true)
        }
        val workbook = try {
            Workbook.getWorkbook(ByteArrayInputStream(bytes), settings)
        } catch (e: Exception) {
            throw ImportException("Fichier .xls illisible : ${e.message}")
        }
        try {
            return workbook.sheets.map { sheet ->
                val rows = (0 until sheet.rows).map { r ->
                    sheet.getRow(r).map { cell ->
                        when (cell.type) {
                            CellType.DATE, CellType.DATE_FORMULA -> {
                                // jxl returns spreadsheet dates as UTC instants.
                                val date = (cell as DateCell).date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                                Dates.format(date)
                            }
                            CellType.NUMBER, CellType.NUMBER_FORMULA -> formatNumber((cell as NumberCell).value)
                            else -> cell.contents.trim()
                        }
                    }
                }
                Sheet(sheet.name, rows)
            }
        } finally {
            workbook.close()
        }
    }

    // ---------- .xlsx ----------

    private fun readXlsx(bytes: ByteArray): List<Sheet> {
        val parts = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) parts[entry.name.removePrefix("/")] = zip.readBytes()
                }
            }
        } catch (e: Exception) {
            throw ImportException("Fichier .xlsx illisible : ${e.message}")
        }
        val workbook = parts["xl/workbook.xml"] ?: throw ImportException("Fichier .xlsx illisible (workbook manquant)")

        val strings = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val dateStyles = parts["xl/styles.xml"]?.let(::parseDateStyles) ?: emptySet()
        val targets = parts["xl/_rels/workbook.xml.rels"]?.let(::parseRelationships) ?: emptyMap()

        val sheets = ArrayList<Sheet>()
        parse(workbook, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
                if (name(localName, qName) != "sheet") return
                val sheetName = attr(attrs, "name") ?: "Feuille ${sheets.size + 1}"
                val target = attr(attrs, "id")?.let(targets::get) ?: "worksheets/sheet${sheets.size + 1}.xml"
                val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                val data = parts[path] ?: return
                sheets += Sheet(sheetName, parseSheet(data, strings, dateStyles))
            }
        })
        return sheets
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val result = ArrayList<String>()
        parse(xml, object : DefaultHandler() {
            val text = StringBuilder()
            var inText = false
            var inPhonetic = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
                when (name(localName, qName)) {
                    "si" -> text.setLength(0)
                    "rPh" -> inPhonetic = true
                    "t" -> inText = !inPhonetic
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (name(localName, qName)) {
                    "si" -> result += text.toString()
                    "rPh" -> inPhonetic = false
                    "t" -> inText = false
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) text.append(ch, start, length)
            }
        })
        return result
    }

    /** Indexes of cell styles (cellXfs) that display a date. */
    private fun parseDateStyles(xml: ByteArray): Set<Int> {
        val customFormats = HashMap<Int, String>()
        val styleFormats = ArrayList<Int>()
        parse(xml, object : DefaultHandler() {
            var inCellXfs = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
                when (name(localName, qName)) {
                    "numFmt" -> {
                        val id = attr(attrs, "numFmtId")?.toIntOrNull() ?: return
                        customFormats[id] = attr(attrs, "formatCode").orEmpty()
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) styleFormats += attr(attrs, "numFmtId")?.toIntOrNull() ?: 0
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (name(localName, qName) == "cellXfs") inCellXfs = false
            }
        })
        return styleFormats.indices.filter { i ->
            val id = styleFormats[i]
            id in 14..22 || id in 27..36 || id in 45..47 || id in 50..58 ||
                customFormats[id]?.let(::isDateFormat) == true
        }.toSet()
    }

    private fun isDateFormat(code: String): Boolean {
        // Drop quoted text, escaped characters and [colour]/[locale] parts before looking for d/m/y.
        val cleaned = code.replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\\\."), "")
            .replace(Regex("\\[[^]]*]"), "")
            .lowercase()
        return cleaned.any { it == 'd' || it == 'y' || it == 'j' || it == 'a' } && !cleaned.contains("general")
    }

    private fun parseRelationships(xml: ByteArray): Map<String, String> {
        val result = HashMap<String, String>()
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
                if (name(localName, qName) != "Relationship") return
                val id = attr(attrs, "Id") ?: return
                result[id] = attr(attrs, "Target") ?: return
            }
        })
        return result
    }

    private fun parseSheet(xml: ByteArray, strings: List<String>, dateStyles: Set<Int>): List<List<String>> {
        val rows = ArrayList<MutableList<String>>()
        parse(xml, object : DefaultHandler() {
            var current: MutableList<String>? = null
            var rowIndex = -1
            var column = 0
            var type = ""
            var style = 0
            val value = StringBuilder()
            var inValue = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
                when (name(localName, qName)) {
                    "row" -> {
                        rowIndex = attr(attrs, "r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                        while (rows.size <= rowIndex) rows.add(ArrayList())
                        current = rows[rowIndex]
                        column = 0
                    }
                    "c" -> {
                        column = attr(attrs, "r")?.let(::columnOf) ?: column
                        type = attr(attrs, "t").orEmpty()
                        style = attr(attrs, "s")?.toIntOrNull() ?: 0
                        value.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (name(localName, qName)) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val row = current ?: return
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let(strings::getOrNull).orEmpty()
                            "str", "inlineStr", "e" -> raw
                            "b" -> if (raw.trim() == "1") "VRAI" else "FAUX"
                            else -> {
                                val number = raw.trim().toDoubleOrNull()
                                when {
                                    number == null -> raw
                                    style in dateStyles -> Dates.format(Dates.fromSerial(number))
                                    else -> formatNumber(number)
                                }
                            }
                        }
                        while (row.size <= column) row.add("")
                        row[column] = text.trim()
                        column++
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) value.append(ch, start, length)
            }
        })
        return rows
    }

    /** "AB12" -> 27 (zero-based column). */
    private fun columnOf(ref: String): Int? {
        var n = 0
        var letters = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                n = n * 26 + (ch - 'A' + 1)
                letters++
            } else {
                break
            }
        }
        return if (letters == 0) null else n - 1
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        try {
            factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
        } catch (e: Exception) {
            throw ImportException("Fichier .xlsx illisible : ${e.message}")
        }
    }

    private fun name(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

    private fun attr(attrs: Attributes, name: String): String? {
        for (i in 0 until attrs.length) {
            val local = attrs.getLocalName(i)?.takeIf { it.isNotEmpty() } ?: attrs.getQName(i).substringAfter(':')
            if (local == name) return attrs.getValue(i)
        }
        return null
    }

    /** 1234.0 -> "1234", 0.1 + 0.2 -> "0.3". */
    internal fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        return BigDecimal(value).round(MathContext(15)).stripTrailingZeros().toPlainString()
    }
}

/** Dates are kept as dd/MM/yyyy text, the way they read in French documents. */
object Dates {
    private val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun format(date: LocalDate): String = date.format(formatter)

    fun parse(text: String): LocalDate? {
        val match = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})$").find(text.trim()) ?: return null
        val (d, m, y) = match.destructured
        val year = y.toInt().let { if (y.length == 2) 2000 + it else it }
        return runCatching { LocalDate.of(year, m.toInt(), d.toInt()) }.getOrNull()
    }

    /** Excel serial day number (1900 date system) to a date. */
    fun fromSerial(serial: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(Math.floor(serial).toLong())

    fun toSerial(date: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date)
}
