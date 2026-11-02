package com.personnel.core

/** A worker as seen across every register. */
data class Worker(
    val key: String,
    val matricule: String,
    val name: String,
    val function: String,
    val records: List<Record>,
)

/** A line about a worker in one register. */
data class Record(val register: Register, val rowIndex: Int) {
    val row: Row get() = register.rows[rowIndex]
}

/** Everyone who appears in the registers, keyed by matricule (or by name when there is none). */
class WorkerDirectory(registers: List<Register>) {

    val workers: List<Worker>
    private val byMatricule: Map<String, Worker>
    private val byKey: Map<String, Worker>

    init {
        class Acc(val key: String, var matricule: String, var name: String, var function: String, val records: MutableList<Record>)

        val acc = LinkedHashMap<String, Acc>()
        for (register in registers) {
            val mat = register.columnIndex(ColumnKind.MATRICULE)
            val name = register.columnIndex(ColumnKind.NAME)
            val function = register.columnIndex(ColumnKind.FUNCTION)
            register.rows.forEachIndexed { i, row ->
                val m = register.cell(row, mat)
                val n = register.cell(row, name)
                val f = register.cell(row, function)
                val key = keyOf(m, n) ?: return@forEachIndexed
                val entry = acc.getOrPut(key) { Acc(key, m, n, f, ArrayList()) }
                // Later lines win, so a newer function replaces an older one.
                if (m.isNotBlank()) entry.matricule = m
                if (n.isNotBlank()) entry.name = n
                if (f.isNotBlank()) entry.function = f
                entry.records += Record(register, i)
            }
        }
        workers = acc.values
            .map { Worker(it.key, it.matricule, it.name, it.function, it.records) }
            .sortedBy { normalize(it.name) }
        byMatricule = workers.filter { it.matricule.isNotBlank() }.associateBy { matriculeKey(it.matricule) }
        byKey = workers.associateBy { it.key }
    }

    fun withKey(key: String): Worker? = byKey[key]

    fun find(matricule: String): Worker? = byMatricule[matriculeKey(matricule)]

    /** Workers whose matricule, name or function contains every word of [query]. */
    fun search(query: String): List<Worker> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return workers
        return workers.filter { w ->
            val text = normalize("${w.matricule} ${w.name} ${w.function}")
            words.all { it in text }
        }
    }

    companion object {
        /** "00123" and "123" are the same matricule. */
        fun matriculeKey(matricule: String): String =
            normalize(matricule).replace(" ", "").trimStart('0').ifEmpty { "0" }

        private fun keyOf(matricule: String, name: String): String? = when {
            matricule.isNotBlank() -> "m:" + matriculeKey(matricule)
            name.isNotBlank() -> "n:" + normalize(name)
            else -> null
        }
    }
}

object Suggestions {

    /** Earlier values of a column that start with (or contain) what was typed, most used first. */
    fun forColumn(register: Register, column: Int, typed: String, limit: Int = 5): List<String> {
        val query = normalize(typed)
        val counts = HashMap<String, Int>()
        for (row in register.rows) {
            val value = row.cells.getOrElse(column) { "" }.trim()
            if (value.isNotEmpty()) counts[value] = (counts[value] ?: 0) + 1
        }
        return counts.entries
            .filter { (value, _) ->
                val v = normalize(value)
                v != query && (query.isEmpty() || query in v)
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { normalize(it.key).startsWith(query) }
                    .thenByDescending { it.value }
            )
            .take(limit)
            .map { it.key }
    }

    /** The next number for an "N°" column: one more than the largest number already used. */
    fun nextNumber(register: Register, column: Int): String {
        val max = register.rows.mapNotNull { it.cells.getOrElse(column) { "" }.trim().toLongOrNull() }.maxOrNull()
        return ((max ?: 0) + 1).toString()
    }
}

/** Rows of [register] matching every word of [query], newest first, with their index. */
fun Register.search(query: String): List<IndexedValue<Row>> {
    val words = normalize(query).split(' ').filter { it.isNotEmpty() }
    return rows.withIndex()
        .filter { (_, row) ->
            words.isEmpty() || normalize(row.cells.joinToString(" ")).let { text -> words.all { it in text } }
        }
        .reversed()
}

/** Replaces the columns, keeping each cell under the column it came from ([sources] = old index or -1). */
fun Register.withColumns(newColumns: List<String>, sources: List<Int>): Register = copy(
    columns = newColumns,
    rows = rows.map { row -> row.copy(cells = sources.map { if (it >= 0) row.cells.getOrElse(it) { "" } else "" }) },
)

/** Guesses which register a sheet belongs to from the sheet name ("Susp salaire" -> Suspension de salaire). */
object SheetMatcher {

    fun bestRegister(sheetName: String, registers: List<Register>): Register? {
        val sheetWords = words(sheetName)
        if (sheetWords.isEmpty()) return null
        val scored = registers.map { register ->
            val titleWords = words(register.title)
            val score = sheetWords.count { w -> titleWords.any { it.startsWith(w) || w.startsWith(it) } }
            // Prefer the title with the fewest unmatched words ("salaire" alone fits
            // "Suspension de salaire" and "Rétablissement de salaire" equally; extra words decide).
            Triple(register, score, titleWords.size - score)
        }
        val best = scored.maxWithOrNull(compareBy<Triple<Register, Int, Int>> { it.second }.thenByDescending { it.third })
            ?: return null
        return if (best.second >= 1) best.first else null
    }

    private val STOP_WORDS = setOf("de", "du", "des", "la", "le", "les", "pour", "a", "l", "d", "en", "sans", "date")

    private fun words(text: String): List<String> =
        normalize(text).split(Regex("[^a-z0-9]+")).filter { it.length > 1 && it !in STOP_WORDS }
}
