package com.nephrocare.core

/** A row of the shareable centers list. type: CENTER, HOSPITAL or NEPHROLOGIST. */
data class CenterRow(
    val name: String,
    val type: String = "CENTER",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val notes: String = "",
)

/**
 * Reads and writes the dialysis-centers directory as CSV, so an association or a care team can
 * prepare one list in a spreadsheet and share it with patients.
 *
 * Columns: name, type, city, address, phone, notes. The header row is optional; when present,
 * columns may be in any order and names are recognised in English, French and Arabic.
 * Comma, semicolon (French Excel) and tab separators are accepted.
 */
object CentersCsv {
    private val HEADER_ALIASES = mapOf(
        "name" to listOf("name", "nom", "nombre", "الاسم", "اسم"),
        "type" to listOf("type", "tipo", "النوع"),
        "city" to listOf("city", "ville", "wilaya", "ciudad", "المدينة", "الولاية"),
        "address" to listOf("address", "adresse", "dirección", "direccion", "العنوان"),
        "phone" to listOf("phone", "téléphone", "telephone", "tel", "tél", "teléfono", "telefono", "الهاتف"),
        "notes" to listOf("notes", "note", "remarques", "notas", "ملاحظات"),
    )
    private val DEFAULT_ORDER = listOf("name", "type", "city", "address", "phone", "notes")

    fun format(rows: List<CenterRow>): String = buildString {
        append(DEFAULT_ORDER.joinToString(",")).append("\r\n")
        rows.forEach { r ->
            append(listOf(r.name, r.type, r.city, r.address, r.phone, r.notes).joinToString(",") { quote(it) })
            append("\r\n")
        }
    }

    fun parse(text: String): List<CenterRow> {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val separator = listOf('\t', ';', ',').maxByOrNull { sep -> firstLine.count { it == sep } } ?: ','
        val records = splitRecords(clean, separator).filter { rec -> rec.any { it.isNotBlank() } }
        if (records.isEmpty()) return emptyList()

        val headerMap = headerIndexes(records.first())
        val (columns, body) = if (headerMap.containsKey("name")) headerMap to records.drop(1)
        else DEFAULT_ORDER.withIndex().associate { it.value to it.index } to records

        return body.mapNotNull { rec ->
            fun col(key: String) = columns[key]?.let { rec.getOrNull(it) }?.trim().orEmpty()
            val name = col("name")
            if (name.isEmpty()) null
            else CenterRow(name, normalizeType(col("type")), col("city"), col("address"), col("phone"), col("notes"))
        }
    }

    fun normalizeType(raw: String): String {
        val t = TextNormalizer.normalize(raw.trim())
        return when {
            t.isEmpty() -> "CENTER"
            listOf("hosp", "مستشف", "chu", "ehs").any { t.contains(it) } -> "HOSPITAL"
            listOf("nephrolog", "doctor", "medecin", "medico", "طبيب", "dr").any { t.contains(it) } -> "NEPHROLOGIST"
            else -> "CENTER"
        }
    }

    private fun headerIndexes(header: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        header.forEachIndexed { i, cell ->
            val n = TextNormalizer.normalize(cell.trim())
            HEADER_ALIASES.forEach { (key, aliases) ->
                if (key !in result && aliases.any { TextNormalizer.normalize(it) == n }) result[key] = i
            }
        }
        return result
    }

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    /** RFC 4180 style: quoted fields may contain separators, doubled quotes and line breaks. */
    private fun splitRecords(text: String, sep: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                sep -> { fields.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { fields.add(field.toString()); field.clear(); records.add(fields); fields = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) { fields.add(field.toString()); records.add(fields) }
        return records
    }
}
