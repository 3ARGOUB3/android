package com.personnel.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoreTest {

    private fun register(columns: List<String>, vararg rows: List<String>) =
        Register("r", "Mise en demeure", columns, rows.map { Row(it) })

    @Test
    fun columnKinds() {
        assertEquals(ColumnKind.MATRICULE, ColumnKind.of("Matricule"))
        assertEquals(ColumnKind.MATRICULE, ColumnKind.of("N° Matricule"))
        assertEquals(ColumnKind.NAME, ColumnKind.of("Nom  Prénom"))
        assertEquals(ColumnKind.NAME, ColumnKind.of("Nom & Prénoms"))
        assertEquals(ColumnKind.OTHER, ColumnKind.of("Nombre de jours"))
        assertEquals(ColumnKind.FUNCTION, ColumnKind.of("FONCTION"))
        assertEquals(ColumnKind.DATE, ColumnKind.of("Date d'effet"))
        assertEquals(ColumnKind.NUMBER, ColumnKind.of("N°"))
        assertEquals(ColumnKind.OTHER, ColumnKind.of("Motif"))
    }

    @Test
    fun xlsxRoundTrip() {
        val reg = register(
            listOf("N°", "Matricule", "Nom prénom", "Fonction", "Date", "Motif"),
            listOf("1", "00123", "Benali Ahmed", "Chauffeur", "05/03/2025", "Absence <3 jours> & retard"),
            listOf("2", "456", "Saidi Karim", "Agent", "", "Abandon de poste"),
        )
        val out = ByteArrayOutputStream()
        XlsxWriter.write(listOf(reg, Register("x", "Questionnaire")), out)

        val sheets = SpreadsheetReader.read(out.toByteArray())
        assertEquals(listOf("Mise en demeure", "Questionnaire"), sheets.map { it.name })
        val table = assertNotNull(SpreadsheetReader.toTable(sheets[0]))
        assertEquals(reg.columns, table.columns)
        assertEquals(reg.rows.map { it.cells }, table.rows)
    }

    @Test
    fun headerRowBelowTitleLines() {
        val sheet = Sheet(
            "Feuil1",
            listOf(
                listOf("SUIVI DES MISES EN DEMEURE"),
                listOf(),
                listOf("Matricule", "Nom Prénom", "Fonction", "", "Observation"),
                listOf("12", "A B", "Agent", "", "x"),
                listOf("", "", ""),
                listOf("13", "C D", "Chef", "", "", ""),
            ),
        )
        val table = assertNotNull(SpreadsheetReader.toTable(sheet))
        assertEquals(listOf("Matricule", "Nom Prénom", "Fonction", "Colonne 4", "Observation"), table.columns)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("13", "C D", "Chef", "", ""), table.rows[1])
    }

    @Test
    fun numbersAndDates() {
        assertEquals("1234", SpreadsheetReader.formatNumber(1234.0))
        assertEquals("0.3", SpreadsheetReader.formatNumber(0.1 + 0.2))
        assertEquals(LocalDate.of(2025, 3, 5), Dates.fromSerial(Dates.toSerial(LocalDate.of(2025, 3, 5)).toDouble()))
        assertEquals(LocalDate.of(2024, 1, 31), Dates.parse("31/1/2024"))
        assertNull(Dates.parse("31/02/2024"))
    }

    @Test
    fun directoryFindsWorkersAcrossRegisters() {
        val a = register(listOf("Matricule", "Nom prénom", "Fonction"), listOf("00123", "Benali Ahmed", "Agent"))
        val b = Register(
            "b", "Questionnaire",
            listOf("Date", "Matricule", "Nom et prénom", "Fonction", "Objet"),
            listOf(Row(listOf("01/02/2025", "123", "Benali Ahmed", "Chauffeur", "Retard"))),
        )
        val directory = WorkerDirectory(listOf(a, b))
        val worker = assertNotNull(directory.find("123"))
        assertEquals("Chauffeur", worker.function)
        assertEquals(2, worker.records.size)
        assertEquals(1, directory.search("benali chauf").size)
        assertEquals(0, directory.search("karim").size)
    }

    @Test
    fun suggestionsAndNumbering() {
        val reg = register(
            listOf("N°", "Motif"),
            listOf("7", "Absence"), listOf("8", "Absence"), listOf("9", "Abandon de poste"),
        )
        assertEquals("10", Suggestions.nextNumber(reg, 0))
        assertEquals(listOf("Absence", "Abandon de poste"), Suggestions.forColumn(reg, 1, "ab"))
        assertEquals(listOf("Abandon de poste"), Suggestions.forColumn(reg, 1, "poste"))
    }

    @Test
    fun sheetNamesMatchRegisters() {
        val registers = DEFAULT_REGISTERS.map { (id, title) -> Register(id, title) }
        fun match(name: String) = SheetMatcher.bestRegister(name, registers)?.id
        assertEquals("suspension_salaire", match("Susp salaire"))
        assertEquals("retablissement_essai", match("Rétab. mise à l'essai"))
        assertEquals("mise_en_demeure", match("MISE EN DEMEURE"))
        assertEquals("pv_audition", match("PV"))
        assertEquals("convocation_affaire", match("Convocation affaire"))
        assertNull(match("Feuil1"))
    }

    @Test
    fun changingColumnsKeepsCells() {
        val reg = register(listOf("A", "B", "C"), listOf("1", "2", "3"))
        val changed = reg.withColumns(listOf("C", "A", "New"), listOf(2, 0, -1))
        assertEquals(listOf("3", "1", ""), changed.rows[0].cells)
    }

    @Test
    fun storeSavesAndLoads() {
        val dir = Files.createTempDirectory("store").toFile()
        val store = RegisterStore(dir)
        val all = store.loadAll()
        assertEquals(10, all.size)
        store.save(all[2].copy(columns = listOf("Matricule", "Motif"), rows = listOf(Row(listOf("1", "x"), added = true))))
        val reloaded = RegisterStore(dir).loadAll()[2]
        assertEquals(listOf("Matricule", "Motif"), reloaded.columns)
        assertEquals(1, reloaded.addedCount)
        File(dir.path).deleteRecursively()
    }
}
