package com.personnel.core

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes registers as an .xlsx workbook, one sheet per register, with a bold frozen header row. */
object XlsxWriter {

    fun write(registers: List<Register>, out: OutputStream) {
        val names = sheetNames(registers.map { it.title })
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(registers.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(names))
            put("xl/_rels/workbook.xml.rels", workbookRels(registers.size))
            put("xl/styles.xml", STYLES)
            registers.forEachIndexed { i, register -> put("xl/worksheets/sheet${i + 1}.xml", sheet(register)) }
        }
    }

    private fun sheet(register: Register): String = buildString {
        val columns = register.columns
        append(XML_HEADER)
        append("<worksheet xmlns=\"$MAIN_NS\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        if (columns.isNotEmpty()) {
            append("<cols>")
            columns.forEachIndexed { i, header ->
                val longest = maxOf(header.length, register.rows.maxOfOrNull { it.cells.getOrElse(i) { "" }.length } ?: 0)
                val width = (longest + 2).coerceIn(8, 60)
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        appendRow(1, columns, header = true)
        register.rows.forEachIndexed { r, row ->
            appendRow(r + 2, columns.indices.map { row.cells.getOrElse(it) { "" } }, header = false)
        }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendRow(number: Int, cells: List<String>, header: Boolean) {
        append("<row r=\"$number\">")
        cells.forEachIndexed { c, value ->
            if (value.isEmpty()) return@forEachIndexed
            val ref = columnName(c) + number
            val date = if (header) null else Dates.parse(value)
            when {
                header -> append("<c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t>${escape(value)}</t></is></c>")
                date != null -> append("<c r=\"$ref\" s=\"2\"><v>${Dates.toSerial(date)}</v></c>")
                isPlainNumber(value) -> append("<c r=\"$ref\"><v>$value</v></c>")
                else -> append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>")
            }
        }
        append("</row>")
    }

    /** Numbers are written as numbers unless that would lose a leading zero (matricule "00123"). */
    private fun isPlainNumber(value: String): Boolean =
        value.length < 15 && Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?$").matches(value)

    internal fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** Excel sheet names: 31 characters at most, no []:*?/\ and unique. */
    internal fun sheetNames(titles: List<String>): List<String> {
        val used = HashSet<String>()
        return titles.mapIndexed { i, title ->
            val base = title.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifEmpty { "Feuille ${i + 1}" }
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) {
                val suffix = " ($n)"
                name = base.take(31 - suffix.length) + suffix
                n++
            }
            name
        }
    }

    private fun escape(text: String): String = buildString {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
                ch < ' ' -> {} // not allowed in XML
                else -> append(ch)
            }
        }
    }

    private fun contentTypes(sheets: Int) = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheets) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append(XML_HEADER)
        append("<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\"><sheets>")
        names.forEachIndexed { i, name ->
            append("<sheet name=\"${escape(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheets: Int) = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheets) {
            append("<Relationship Id=\"rId$i\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheets + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = XML_HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    // Style 0: normal, 1: bold header with grey fill, 2: date dd/mm/yyyy.
    private const val STYLES = XML_HEADER +
        "<styleSheet xmlns=\"$MAIN_NS\">" +
        "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy\"/></numFmts>" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9D9D9\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
