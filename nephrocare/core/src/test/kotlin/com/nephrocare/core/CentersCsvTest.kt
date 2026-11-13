package com.nephrocare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CentersCsvTest {
    @Test
    fun roundTripKeepsQuotesCommasAndLineBreaks() {
        val rows = listOf(
            CenterRow("Centre \"Ennour\"", "CENTER", "Alger", "12, rue Didouche\nBab El Oued", "021 00 00 00", "Lun; mer; sam"),
            CenterRow("مستشفى مصطفى باشا", "HOSPITAL", "الجزائر العاصمة"),
        )
        assertEquals(rows, CentersCsv.parse(CentersCsv.format(rows)))
    }

    @Test
    fun frenchExcelWithSemicolonsAndReorderedHeader() {
        val csv = "﻿Ville;Nom;Téléphone;Type\r\nOran;Clinique El Amel;041 11 22 33;Centre\r\nOran;Dr Benali;0550 00 00 00;Néphrologue\r\n;;;\r\n"
        val rows = CentersCsv.parse(csv)
        assertEquals(2, rows.size)
        assertEquals(CenterRow("Clinique El Amel", "CENTER", "Oran", phone = "041 11 22 33"), rows[0])
        assertEquals("NEPHROLOGIST", rows[1].type)
    }

    @Test
    fun arabicHeaderAndNoHeader() {
        val ar = CentersCsv.parse("الاسم,النوع,الولاية\nمركز الأمل,مستشفى,سطيف\n")
        assertEquals(CenterRow("مركز الأمل", "HOSPITAL", "سطيف"), ar.single())
        val bare = CentersCsv.parse("Centre A,CENTER,Blida,,025 00\n")
        assertEquals(CenterRow("Centre A", "CENTER", "Blida", "", "025 00"), bare.single())
        assertTrue(CentersCsv.parse("   \n").isEmpty())
    }
}
