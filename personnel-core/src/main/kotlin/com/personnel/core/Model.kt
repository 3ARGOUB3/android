package com.personnel.core

import kotlinx.serialization.Serializable
import java.text.Normalizer

/** One of the registers (Suspension de salaire, Mise en demeure, ...). */
@Serializable
data class Register(
    val id: String,
    val title: String,
    val columns: List<String> = DEFAULT_COLUMNS,
    val rows: List<Row> = emptyList(),
) {
    val addedCount: Int get() = rows.count { it.added }

    fun columnIndex(kind: ColumnKind): Int = columns.indexOfFirst { ColumnKind.of(it) == kind }

    fun cell(row: Row, index: Int): String = if (index >= 0) row.cells.getOrElse(index) { "" } else ""

    companion object {
        val DEFAULT_COLUMNS = listOf("Matricule", "Nom prénom", "Fonction")
    }
}

/** A line of a register. [added] marks lines typed in the app (not imported). */
@Serializable
data class Row(
    val cells: List<String>,
    val added: Boolean = false,
)

/** The registers the app starts with. Their columns come from the imported files. */
val DEFAULT_REGISTERS: List<Pair<String, String>> = listOf(
    "suspension_salaire" to "Suspension de salaire",
    "retablissement_salaire" to "Rétablissement de salaire",
    "mise_en_demeure" to "Mise en demeure",
    "questionnaire" to "Questionnaire",
    "convocation_entretien" to "Convocation pour entretien",
    "convocation_affaire" to "Convocation pour affaire (sans date)",
    "pv_audition" to "PV d'audition",
    "lettre_radiation" to "Lettre de radiation",
    "suspension_essai" to "Suspension mise à l'essai",
    "retablissement_essai" to "Rétablissement mise à l'essai",
)

/** What a column holds, guessed from its header. Drives auto-fill and the date picker. */
enum class ColumnKind {
    MATRICULE, NAME, FUNCTION, NUMBER, DATE, OTHER;

    companion object {
        private val NUMBER_HEADERS =
            setOf("n", "n°", "no", "n.", "num", "numero", "n°ordre", "nordre", "ordre", "n°d'ordre", "numerod'ordre")

        fun of(header: String): ColumnKind {
            val h = normalize(header)
            val words = h.split(Regex("[^a-z0-9°]+")).filter { it.isNotEmpty() }
            return when {
                words.any { it.startsWith("matricule") } || h == "mat" || h == "mle" -> MATRICULE
                "date" in words -> DATE
                "nom" in words || words.any { it.startsWith("prenom") } -> NAME
                words.any { it.startsWith("fonction") } || h.startsWith("poste") || h == "emploi" -> FUNCTION
                h.replace(" ", "") in NUMBER_HEADERS -> NUMBER
                else -> OTHER
            }
        }
    }
}

/** Lower case, no accents, single spaces: "  Nom  Prénom" -> "nom prenom". */
fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace('º', '°')
        .replace(Regex("\\s+"), " ")
        .trim()
